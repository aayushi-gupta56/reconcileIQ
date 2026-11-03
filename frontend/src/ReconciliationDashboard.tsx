import { useEffect, useState } from "react";

import InvestigationPanel from './InvestigationPanel'

import './ReconciliationDashboard.css'

import {
  runReconciliation,
  getReconciliationRuns,
  getReconciliationResults,
  type ReconciliationRun,
  type ReconciliationResult,
} from "./api";

const statusColors: Record<string, string> = {
  MATCHED: "#16a34a",
  MISSING_PAYMENT: "#d97706",
  AMOUNT_MISMATCH: "#dc2626",
  CURRENCY_MISMATCH: "#dc2626",
  DUPLICATE_PAYMENT: "#9333ea",
  REFERENCE_MISMATCH: "#ea580c",
  UNEXPECTED_PAYMENT: "#dc2626",
};

function formatAmount(
  amount: number | null,
  currency: string | null
) {
  if (amount === null || currency === null) return "-";

  return `${amount.toFixed(2)} ${currency}`;
}

export default function ReconciliationDashboard() {
  const [runs, setRuns] = useState<ReconciliationRun[]>([]);
  const [selectedRun, setSelectedRun] =
    useState<ReconciliationRun | null>(null);

  const [results, setResults] = useState<ReconciliationResult[]>([]);

  const [loading, setLoading] = useState(false);
  const [loadingResults, setLoadingResults] = useState(false);
  const [error, setError] = useState("");
  const [selectedResultId, setSelectedResultId] =
  useState<number | null>(null)

  async function loadRuns() {
    const data = await getReconciliationRuns();

    setRuns(data);

    if (data.length > 0 && !selectedRun) {
      await selectRun(data[0]);
    }
  }

  async function selectRun(run: ReconciliationRun) {
    setSelectedRun(run);
    setLoadingResults(true);
    setError("");

    try {
      const data = await getReconciliationResults(run.id);
      setResults(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load results"
      );
    } finally {
      setLoadingResults(false);
    }
  }

  async function handleRunReconciliation() {
    setLoading(true);
    setError("");

    try {
      const run = await runReconciliation();

      setRuns((previous) => [run, ...previous]);
      setSelectedRun(run);

      const data = await getReconciliationResults(run.id);
      setResults(data);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Reconciliation failed"
      );
    } finally {
      setLoading(false);
      setLoadingResults(false);
    }
  }

  useEffect(() => {
    loadRuns().catch((err) => {
      setError(
        err instanceof Error ? err.message : "Failed to load runs"
      );
    });
  }, []);

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">RECONCILEIQ / PAYMENTS</p>
          <h1>Reconciliation Dashboard</h1>
          <p className="subtitle">
            Compare merchant orders against captured PayPal payments.
          </p>
        </div>
        <button
  className="run-button"
  onClick={() => window.location.href = "/"}
>
  ← Back to home
</button>

        <button
          className="run-button"
          onClick={handleRunReconciliation}
          disabled={loading}
        >
          {loading ? "Running..." : "↻ Run reconciliation"}
        </button>
      </header>

      {error && <div className="error-banner">{error}</div>}

      {selectedRun && (
        <>
          <section className="summary-grid">
            <SummaryCard
              label="Total orders"
              value={selectedRun.totalOrders}
            />

            <SummaryCard
              label="Matched"
              value={selectedRun.matchedCount}
              color="#16a34a"
            />

            <SummaryCard
              label="Discrepancies"
              value={selectedRun.discrepancyCount}
              color="#dc2626"
            />

            <SummaryCard
              label="Unexpected payments"
              value={selectedRun.unmatchedPaymentsCount}
              color="#d97706"
            />
          </section>

          <section className="results-section">
            <div className="section-heading">
              <div>
                <h2>Transaction results</h2>
                <p>
                  Run #{selectedRun.id} ·{" "}
                  {new Date(selectedRun.startedAt).toLocaleString()}
                </p>
              </div>
              <span className="result-count">
                {results.length} records
              </span>
            </div>

            {loadingResults ? (
              <p>Loading results...</p>
            ) : results.length === 0 ? (
              <div className="empty-state">
                No reconciliation results found for this run.
              </div>
            ) : (
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>Merchant order</th>
                      <th>PayPal order</th>
                      <th>Expected</th>
                      <th>Actual</th>
                      <th>Status</th>
                      <th>Explanation</th>
                      <th>Action</th>
                    </tr>
                  </thead>

                  <tbody>
                    {results.map((result) => (
                      <tr key={result.id}>
                        <td>{result.merchantOrderId ?? "-"}</td>

                        <td className="paypal-id">
                          {result.paypalOrderId ?? "-"}
                        </td>

                        <td>
                          {formatAmount(
                            result.expectedAmount,
                            result.expectedCurrency
                          )}
                        </td>

                        <td>
                          {formatAmount(
                            result.actualAmount,
                            result.actualCurrency
                          )}
                        </td>

                        <td>
                          <span
                            className="status"
                            style={{
                              color:
                                statusColors[result.status] ?? "#64748b",
                              background:
                                `${statusColors[result.status] ?? "#64748b"}18`,
                            }}
                          >
                            {result.status.replaceAll("_", " ")}
                          </span>
                        </td>

                        <td className="explanation">
                          {result.explanation}
                        </td>
                        <td className="action">
                        <button
  className="investigate-button"
  onClick={() => setSelectedResultId(result.id)}
  disabled={result.status === 'MATCHED'}
>
  Investigate
</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}

      {!selectedRun && !loading && (
        <section className="empty-state">
          <h2>No reconciliation runs yet</h2>
          <p>
            Trigger your first run to compare merchant orders and PayPal
            transactions.
          </p>
        </section>
      )}

{selectedResultId !== null && (
  <InvestigationPanel
    resultId={selectedResultId}
    onClose={() => setSelectedResultId(null)}
  />
)}

      <section className="history-section">
        <h2>Previous runs</h2>

        {runs.length === 0 ? (
          <p>No previous runs.</p>
        ) : (
          <div className="run-list">
            {runs.map((run) => (
              <button
                key={run.id}
                className={`run-item ${
                  selectedRun?.id === run.id ? "active" : ""
                }`}
                onClick={() => selectRun(run)}
              >
                <span>
                  <strong>Run #{run.id}</strong>
                  <small>
                    {new Date(run.startedAt).toLocaleString()}
                  </small>
                </span>

                <span>
                  {run.matchedCount} matched ·{" "}
                  {run.discrepancyCount} discrepancies
                </span>
              </button>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}

function SummaryCard({
  label,
  value,
  color = "#1e293b",
}: {
  label: string;
  value: number;
  color?: string;
}) {
  return (
    <div className="summary-card">
      <p>{label}</p>
      <strong style={{ color }}>{value}</strong>
    </div>
  );
}