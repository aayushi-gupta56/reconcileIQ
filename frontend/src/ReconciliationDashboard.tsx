import { useEffect, useMemo, useState } from "react";
import { AgGridReact } from "ag-grid-react";
import { AllCommunityModule, ModuleRegistry } from "ag-grid-community";
import type { ColDef, RowClickedEvent } from "ag-grid-community";
import { paypal, reconcileGridTheme } from "./gridTheme";

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

ModuleRegistry.registerModules([AllCommunityModule]);

// Status -> [text colour, background tint], all derived from the PayPal palette
const statusColors: Record<string, [string, string]> = {
  MATCHED: [paypal.success, "#E3F5EB"],
  MISSING_PAYMENT: [paypal.warning, "#FFF1CC"],
  AMOUNT_MISMATCH: [paypal.danger, "#FCE8E8"],
  CURRENCY_MISMATCH: [paypal.danger, "#FCE8E8"],
  DUPLICATE_PAYMENT: [paypal.navy, "#E4EAF7"],
  REFERENCE_MISMATCH: [paypal.blue, "#E5F0FC"],
  UNEXPECTED_PAYMENT: ["#006C93", "#DDF2FA"],
};

const REVIEW_COPY: Record<string, string> = {
  PENDING: "Pending",
  UNREVIEWED: "Unreviewed",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  REVIEWED: "Reviewed",
};

function formatAmount(amount: number | null, currency: string | null) {
  if (amount === null || currency === null) return "-";
  return `${Number(amount).toFixed(2)} ${currency}`;
}

function StatusCell(props: { value: string }) {
  const [color, background] = statusColors[props.value] ?? [paypal.slate, paypal.surface];
  return (
    <span className="status" style={{ color, background }}>
      <i className="status-dot" style={{ background: color }} />
      {(props.value ?? "").replaceAll("_", " ")}
    </span>
  );
}

function ReviewCell(props: { value: string | null }) {
  if (!props.value) return <span className="cell-muted">-</span>;
  const key = props.value.toUpperCase();
  return (
    <span className={`review-pill ${key.toLowerCase()}`}>
      {REVIEW_COPY[key] ?? props.value.replaceAll("_", " ")}
    </span>
  );
}

function IdCell(props: { value: string | null }) {
  if (!props.value) return <span className="cell-muted">-</span>;
  return <span className="cell-id" title={props.value}>{props.value}</span>;
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
      { field: "merchantOrderId", headerName: "Merchant order", cellRenderer: IdCell, flex: 1.2, minWidth: 170 },
      { field: "paypalOrderId", headerName: "PayPal order", cellRenderer: IdCell, flex: 1.2, minWidth: 170 },
      { field: "status", headerName: "Status", cellRenderer: StatusCell, minWidth: 210 },
      {
        headerName: "Expected",
        type: "rightAligned",
        cellClass: "cell-amount",
        minWidth: 140,
        valueGetter: (params) =>
          formatAmount(params.data?.expectedAmount ?? null, params.data?.expectedCurrency ?? null),
      },
      {
        headerName: "Actual",
        type: "rightAligned",
        cellClass: "cell-amount",
        minWidth: 140,
        valueGetter: (params) =>
          formatAmount(params.data?.actualAmount ?? null, params.data?.actualCurrency ?? null),
      },
      { field: "expectedCurrency", headerName: "Currency", width: 120 },
      { field: "source", headerName: "Source", cellRenderer: SourceCell, width: 130 },
      { field: "reviewStatus", headerName: "Review", cellRenderer: ReviewCell, width: 150 },
      {
        field: "createdAt",
        headerName: "Created",
        minWidth: 190,
        valueFormatter: (params) =>
          params.value ? new Date(params.value).toLocaleString() : "-",
      },
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
          // ?run=<id> (from the notification email) opens that specific run.
          const wantedId = Number(new URLSearchParams(window.location.search).get("run"));
          const wanted = wantedId > 0 ? data.find((run) => run.id === wantedId) : undefined;
          await selectRun(wanted ?? data[0]);
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
          <SummaryCard label="Matched" value={stats.matched} color={paypal.success} />
          <SummaryCard label="Discrepancies" value={stats.discrepancies} color={paypal.danger} />
          <SummaryCard label="Unexpected payments" value={stats.unexpected} color={paypal.warning} />
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
            <div className="results-grid">
              <AgGridReact<ReconciliationResult>
                theme={reconcileGridTheme}
                domLayout="autoHeight"
                rowData={visibleResults}
                columnDefs={columnDefs}
                defaultColDef={{ resizable: true, filter: true, sortable: true, minWidth: 110 }}
                rowSelection={{ mode: "singleRow", checkboxes: false, enableClickSelection: true }}
                pagination
                paginationPageSize={10}
                paginationPageSizeSelector={[10, 25, 50]}
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
  color = paypal.ink,
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
