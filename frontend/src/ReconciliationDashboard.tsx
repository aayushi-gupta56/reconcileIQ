import { useEffect, useMemo, useState } from "react";
import { AgGridReact } from "ag-grid-react";
import type { ColDef, RowClickedEvent } from "ag-grid-community";
import "ag-grid-community/styles/ag-grid.css";
import "ag-grid-community/styles/ag-theme-quartz.css";

import InvestigationPanel from "./InvestigationPanel";
import "./ReconciliationDashboard.css";
import "./DashboardTabs.css";
import {
  getAppConfig,
  getReconciliationResults,
  getReconciliationRuns,
  runReconciliation,
  type AppConfig,
  type ReconciliationResult,
  type ReconciliationRun,
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

function formatAmount(amount: number | null, currency: string | null) {
  if (amount === null || currency === null) return "-";
  return `${Number(amount).toFixed(2)} ${currency}`;
}

function StatusCell(props: { value: string }) {
  const color = statusColors[props.value] ?? "#64748b";
  return (
    <span
      className="status"
      style={{ color, background: `${color}18` }}
    >
      {(props.value ?? "").replaceAll("_", " ")}
    </span>
  );
}

function SourceCell(props: { value: string }) {
  const source = props.value === "SANDBOX" ? "Sandbox" : "Demo";
  return (
    <span className={`source-badge compact ${source.toLowerCase()}`}>
      {source}
    </span>
  );
}

type Tab = "actual" | "demo";

const TAB_COPY: Record<Tab, { label: string; description: string }> = {
  actual: {
    label: "Actual payments",
    description:
      "Orders created in this app and paid through the PayPal sandbox. Real API data, no seeded rows.",
  },
  demo: {
    label: "Demo scenarios",
    description:
      "Seeded scenarios, one per status, for exploring the investigation workflow. They never call PayPal.",
  },
};

function summarise(rows: ReconciliationResult[]) {
  const unexpected = rows.filter((r) => r.status === "UNEXPECTED_PAYMENT").length;
  const matched = rows.filter((r) => r.status === "MATCHED").length;
  const orders = rows.filter(
    (r) => r.merchantOrderId != null && r.status !== "UNEXPECTED_PAYMENT"
  ).length;
  return { orders, matched, discrepancies: orders - matched, unexpected };
}

type Props = { onCreateOrder?: () => void };

export default function ReconciliationDashboard({ onCreateOrder }: Props) {
  const [runs, setRuns] = useState<ReconciliationRun[]>([]);
  const [selectedRun, setSelectedRun] = useState<ReconciliationRun | null>(null);
  const [results, setResults] = useState<ReconciliationResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selectedResult, setSelectedResult] = useState<ReconciliationResult | null>(null);
  const [appConfig, setAppConfig] = useState<AppConfig | null>(null);
  const [activeTab, setActiveTab] = useState<Tab | null>(null);

  // Until the user picks a tab, follow the backend mode:
  // demo mode opens on demo scenarios, live mode opens on actual payments.
  const tab: Tab =
    activeTab ?? (appConfig?.dataSource === "LIVE_SANDBOX" ? "actual" : "demo");

  const demoResults = useMemo(
    () => results.filter((row) => row.source === "DEMO"),
    [results]
  );
  const actualResults = useMemo(
    () => results.filter((row) => row.source !== "DEMO"),
    [results]
  );
  const visibleResults = tab === "demo" ? demoResults : actualResults;

  const stats = useMemo(() => summarise(visibleResults), [visibleResults]);
  const demoStats = useMemo(() => summarise(demoResults), [demoResults]);
  const actualStats = useMemo(() => summarise(actualResults), [actualResults]);

  function selectTab(next: Tab) {
    setActiveTab(next);
    setSelectedResult(null);
  }

  const columnDefs = useMemo<ColDef<ReconciliationResult>[]>(
    () => [
      { field: "merchantOrderId", headerName: "Merchant order", filter: true, sortable: true, flex: 1 },
      { field: "paypalOrderId", headerName: "PayPal order", filter: true, sortable: true, flex: 1 },
      { field: "status", headerName: "Status", filter: true, sortable: true, cellRenderer: StatusCell, width: 180 },
      {
        headerName: "Expected",
        filter: true,
        sortable: true,
        valueGetter: (params) =>
          formatAmount(params.data?.expectedAmount ?? null, params.data?.expectedCurrency ?? null),
      },
      {
        headerName: "Actual",
        filter: true,
        sortable: true,
        valueGetter: (params) =>
          formatAmount(params.data?.actualAmount ?? null, params.data?.actualCurrency ?? null),
      },
      { field: "expectedCurrency", headerName: "Currency", filter: true, sortable: true, width: 110 },
      { field: "source", headerName: "Source", filter: true, sortable: true, cellRenderer: SourceCell, width: 120 },
      { field: "reviewStatus", headerName: "Review", filter: true, sortable: true, width: 150 },
      { field: "createdAt", headerName: "Created", filter: true, sortable: true, width: 180 },
    ],
    []
  );

  async function loadResults(run: ReconciliationRun) {
    const data = await getReconciliationResults(run.id);
    setResults(data);
    setSelectedResult((current) =>
      current ? data.find((row) => row.id === current.id) ?? current : null
    );
  }

  async function selectRun(run: ReconciliationRun) {
    setSelectedRun(run);
    setError("");
    try {
      await loadResults(run);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load results");
    }
  }

  async function handleRunReconciliation() {
    setLoading(true);
    setError("");
    try {
      const run = await runReconciliation();
      setRuns((previous) => [run, ...previous]);
      setSelectedRun(run);
      await loadResults(run);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Reconciliation failed");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    getAppConfig().then(setAppConfig).catch(() => undefined);
    getReconciliationRuns()
      .then(async (data) => {
        setRuns(data);
        if (data.length > 0) {
          await selectRun(data[0]);
        }
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "Failed to load runs");
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
        <button className="run-button" onClick={() => (window.location.href = "/")}>
          ← Back to home
        </button>
        <button className="run-button" onClick={handleRunReconciliation} disabled={loading}>
          {loading ? "Running..." : "↻ Run reconciliation"}
        </button>
      </header>

      {error && <div className="error-banner">{error}</div>}

      <div className="dashboard-tabs" role="tablist">
        {(["actual", "demo"] as Tab[]).map((key) => {
          const tabStats = key === "demo" ? demoStats : actualStats;
          const needsAttention = tabStats.discrepancies + tabStats.unexpected;
          const total = key === "demo" ? demoResults.length : actualResults.length;
          return (
            <button
              key={key}
              role="tab"
              aria-selected={tab === key}
              className={`dashboard-tab ${tab === key ? "active" : ""}`}
              onClick={() => selectTab(key)}
            >
              {TAB_COPY[key].label}
              <span className="tab-count">{total}</span>
              {needsAttention > 0 && (
                <span className="tab-count alert" title="Need attention">
                  {needsAttention}
                </span>
              )}
            </button>
          );
        })}
      </div>
      <p className="tab-description">{TAB_COPY[tab].description}</p>

      {selectedRun && (
        <section className="summary-grid">
          <SummaryCard label="Total orders" value={stats.orders} />
          <SummaryCard label="Matched" value={stats.matched} color="#16a34a" />
          <SummaryCard label="Discrepancies" value={stats.discrepancies} color="#dc2626" />
          <SummaryCard label="Unexpected payments" value={stats.unexpected} color="#d97706" />
        </section>
      )}

      <div className="workspace">
        <section className="results-section">
          <div className="section-heading">
            <div>
              <h2>{TAB_COPY[tab].label}</h2>
              <p>
                {selectedRun
                  ? `Run #${selectedRun.id} · ${new Date(selectedRun.startedAt).toLocaleString()}`
                  : "Run reconciliation to load rows."}
              </p>
            </div>
            <span className="result-count">{visibleResults.length} records</span>
          </div>
          {visibleResults.length === 0 ? (
            <EmptyState
              tab={tab}
              hasRun={selectedRun !== null}
              paypalConfigured={appConfig?.paypalConfigured}
              onCreateOrder={onCreateOrder}
            />
          ) : (
            <div className="ag-theme-quartz results-grid">
              <AgGridReact<ReconciliationResult>
                rowData={visibleResults}
                columnDefs={columnDefs}
                defaultColDef={{ resizable: true, filter: true, sortable: true }}
                rowSelection="single"
                animateRows
                onRowClicked={(event: RowClickedEvent<ReconciliationResult>) => {
                  if (event.data) setSelectedResult(event.data);
                }}
              />
            </div>
          )}
        </section>
        <InvestigationPanel
          result={selectedResult}
          onClose={() => setSelectedResult(null)}
          onReviewed={() => {
            if (selectedRun) {
              loadResults(selectedRun).catch(() => undefined);
            }
          }}
        />
      </div>

      <section className="history-section">
        <h2>Previous runs</h2>
        {runs.length === 0 ? (
          <p>No previous runs.</p>
        ) : (
          <div className="run-list">
            {runs.map((run) => (
              <button
                key={run.id}
                className={`run-item ${selectedRun?.id === run.id ? "active" : ""}`}
                onClick={() => selectRun(run)}
              >
                <span>
                  <strong>Run #{run.id}</strong>
                  <small>{new Date(run.startedAt).toLocaleString()}</small>
                </span>
                <span>
                  {run.matchedCount} matched · {run.discrepancyCount} discrepancies
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

function EmptyState({
  tab,
  hasRun,
  paypalConfigured,
  onCreateOrder,
}: {
  tab: Tab;
  hasRun: boolean;
  paypalConfigured?: boolean;
  onCreateOrder?: () => void;
}) {
  if (!hasRun) {
    return (
      <div className="empty-state">
        <h3>No reconciliation run yet</h3>
        <p>Click “Run reconciliation” to compare merchant orders with PayPal captures.</p>
      </div>
    );
  }

  if (tab === "demo") {
    return (
      <div className="empty-state">
        <h3>No demo scenarios in this run</h3>
        <p>
          Demo scenarios are seeded when the backend starts with
          reconcileiq.data-source=DEMO_SCENARIOS. Start it in that mode, then run
          reconciliation again.
        </p>
      </div>
    );
  }

  return (
    <div className="empty-state">
      <h3>No actual payments reconciled yet</h3>
      <p>
        Create a merchant order, approve it in the PayPal sandbox, then run
        reconciliation again.
      </p>
      {paypalConfigured === false && (
        <p className="empty-warning">
          PayPal credentials are not configured on the backend. Set PAYPAL_CLIENT_ID
          and PAYPAL_CLIENT_SECRET and restart it.
        </p>
      )}
      {onCreateOrder && (
        <button className="run-button" onClick={onCreateOrder}>
          Create merchant order →
        </button>
      )}
    </div>
  );
}
