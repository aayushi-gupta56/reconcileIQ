import { useEffect, useState } from "react";
import "./InvestigationPanel.css";
import {
  getInvestigations,
  investigateResult,
  reviewInvestigation,
  type InvestigationReport,
  type ReconciliationResult,
} from "./api";

type Props = {
  result: ReconciliationResult | null;
  onClose: () => void;
  onReviewed: () => void;
};

type Tone = "pending" | "approved" | "dismissed" | "info" | "muted";

/** Human label + colour for a report. Older unreviewed reports were replaced by a regeneration. */
function describeStatus(
  report: InvestigationReport,
  isLatest: boolean
): { label: string; tone: Tone } {
  switch (report.status) {
    case "APPROVED":
      return { label: "Approved", tone: "approved" };
    case "DISMISSED":
      return { label: "Dismissed", tone: "dismissed" };
    case "NEEDS_INFO":
      return isLatest
        ? { label: "Needs info", tone: "info" }
        : { label: "Superseded", tone: "muted" };
    case "PENDING_REVIEW":
      return isLatest
        ? { label: "Pending review", tone: "pending" }
        : { label: "Superseded", tone: "muted" };
    default:
      return { label: report.status.replaceAll("_", " "), tone: "muted" };
  }
}

function sameContent(a: InvestigationReport, b: InvestigationReport) {
  return (
    JSON.stringify([a.summary, a.possibleExplanations, a.recommendedChecks]) ===
    JSON.stringify([b.summary, b.possibleExplanations, b.recommendedChecks])
  );
}

function formatWhen(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

function regenerateHint(report: InvestigationReport, isLatest: boolean) {
  if (!isLatest) {
    return "Creates a new report from the current evidence. Every earlier report stays in the history.";
  }
  switch (report.status) {
    case "APPROVED":
      return "This report was approved. Regenerating adds a new report for review and keeps this one in the history.";
    case "DISMISSED":
      return "This report was dismissed. Regenerate to ask the AI for a fresh analysis.";
    default:
      return "Not happy with this analysis? Regenerating adds a new report; this one stays in the history as superseded.";
  }
}

export default function InvestigationPanel({
  result,
  onClose,
  onReviewed,
}: Props) {
  const [history, setHistory] = useState<InvestigationReport[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [historyOpen, setHistoryOpen] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [reviewer, setReviewer] = useState("analyst");
  const [note, setNote] = useState("");

  const resultId = result?.id;

  // Switching to another row starts from a clean slate.
  useEffect(() => {
    setSelectedId(null);
    setError("");
    setNote("");
  }, [resultId]);

  useEffect(() => {
    if (!result || result.status === "MATCHED") {
      setHistory([]);
      return;
    }
    let cancelled = false;
    getInvestigations(result.id)
      .then((data) => {
        if (!cancelled) setHistory([...data].sort((a, b) => b.id - a.id));
      })
      .catch(() => {
        if (!cancelled) setHistory([]);
      });
    return () => {
      cancelled = true;
    };
  }, [result]);

  if (!result) {
    return (
      <aside className="investigation-panel embedded">
        <p className="eyebrow">DETAIL</p>
        <h2>Select a discrepancy</h2>
        <p>Choose a row to inspect facts, hypotheses, review actions and AI report history.</p>
      </aside>
    );
  }

  // history is newest first
  const latest = history[0] ?? null;
  const report =
    (selectedId != null ? history.find((item) => item.id === selectedId) : null) ??
    latest;
  const isLatest = report != null && latest != null && report.id === latest.id;
  const versionOf = (item: InvestigationReport) =>
    history.length - history.findIndex((h) => h.id === item.id);

  async function handleInvestigate() {
    if (!result) return;
    setLoading(true);
    setError("");
    try {
      const created = await investigateResult(result.id);
      setHistory((current) => [created, ...current]);
      setSelectedId(null); // jump to the new report
      setNote("");
      onReviewed();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Investigation failed");
    } finally {
      setLoading(false);
    }
  }

  async function handleReview(action: "approve" | "dismiss") {
    if (!report || !isLatest) return;
    setLoading(true);
    setError("");
    try {
      const updated = await reviewInvestigation(report.id, action, reviewer, note);
      setHistory((current) =>
        current.map((item) => (item.id === updated.id ? updated : item))
      );
      onReviewed();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Review failed");
    } finally {
      setLoading(false);
    }
  }

  const state = report ? describeStatus(report, isLatest) : null;

  return (
    <aside className="investigation-panel embedded">
      <div className="investigation-header">
        <div>
          <p className="eyebrow">AI-ASSISTED ANALYSIS</p>
          <h2>{result.merchantOrderId ?? result.paypalOrderId ?? `#${result.id}`}</h2>
          <p>{result.status.replaceAll("_", " ")}</p>
        </div>
        <button className="close-button" onClick={onClose} aria-label="Clear selection">
          ×
        </button>
      </div>

      {result.status === "MATCHED" ? (
        <p>Matched rows do not require investigation.</p>
      ) : (
        <>
          {!report && (
            <div className="investigation-empty">
              <p>
                The rule engine owns this classification. Generate an investigation
                to add hypotheses and verification checks.
              </p>
              <button className="run-button" onClick={handleInvestigate} disabled={loading}>
                {loading ? "Analysing…" : "Generate investigation →"}
              </button>
            </div>
          )}

          {error && (
            <div className="investigation-error">
              <strong>Request failed</strong>
              <p>{error}</p>
            </div>
          )}

          {report && state && (
            <div className="investigation-content">
              {/* ---------- history timeline ---------- */}
              <section className="history-panel">
                <button
                  className="history-toggle"
                  onClick={() => setHistoryOpen((open) => !open)}
                  aria-expanded={historyOpen}
                >
                  <span>AI report history</span>
                  <span className="history-count">{history.length}</span>
                  <span className="history-chevron">{historyOpen ? "−" : "+"}</span>
                </button>
                {historyOpen && (
                  <ol className="history-list">
                    {history.map((item, index) => {
                      const older = history[index + 1];
                      const itemState = describeStatus(item, index === 0);
                      const active = item.id === report.id;
                      return (
                        <li key={item.id}>
                          <button
                            className={`history-item ${active ? "active" : ""}`}
                            onClick={() => setSelectedId(index === 0 ? null : item.id)}
                            aria-current={active}
                          >
                            <span className={`history-dot ${itemState.tone}`} />
                            <span className="history-main">
                              <span className="history-title">
                                Report {versionOf(item)}
                                {index === 0 && <em className="latest-tag">Latest</em>}
                              </span>
                              <span className="history-sub">
                                {formatWhen(item.createdAt)} · {item.modelName}
                              </span>
                              <span className="history-tags">
                                <span className={`state-pill ${itemState.tone}`}>
                                  {itemState.label}
                                </span>
                                {item.validationStatus === "FALLBACK" && (
                                  <span className="tag warn">Fallback</span>
                                )}
                                {older && sameContent(item, older) && (
                                  <span className="tag">Same as previous</span>
                                )}
                                {item.reviewedBy && (
                                  <span className="tag">by {item.reviewedBy}</span>
                                )}
                              </span>
                            </span>
                          </button>
                        </li>
                      );
                    })}
                  </ol>
                )}
              </section>

              {!isLatest && (
                <div className="older-banner">
                  <span>
                    Viewing Report {versionOf(report)} of {history.length} (read-only).
                  </span>
                  <button className="link-button" onClick={() => setSelectedId(null)}>
                    Back to latest
                  </button>
                </div>
              )}

              {/* ---------- selected report ---------- */}
              <div className="report-meta">
                <span className={`state-pill ${state.tone}`}>{state.label}</span>
                <span>Validation: {report.validationStatus.replaceAll("_", " ")}</span>
              </div>

              {report.validationStatus === "FALLBACK" && (
                <div className="fallback-banner">
                  Deterministic fallback was used after the model output failed validation.
                </div>
              )}

              <section className="report-section">
                <h3>Summary</h3>
                <p>{report.summary}</p>
              </section>
              <section className="report-section">
                <h3>Observed facts</h3>
                <ul>
                  {report.observedFacts.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </section>
              <section className="report-section">
                <h3>Possible explanations (unverified)</h3>
                <ul>
                  {report.possibleExplanations.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </section>
              <section className="report-section recommendation">
                <h3>Recommended checks</h3>
                <ol>
                  {report.recommendedChecks.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ol>
              </section>

              <p className="model-meta">
                {report.modelName} · prompt {report.promptVersion ?? "v1"}
                {report.latencyMs != null ? ` · ${report.latencyMs} ms` : ""}
              </p>

              {isLatest && report.status === "PENDING_REVIEW" && (
                <div className="review-form">
                  <label>
                    Reviewer
                    <input
                      value={reviewer}
                      onChange={(event) => setReviewer(event.target.value)}
                    />
                  </label>
                  <label>
                    Note
                    <textarea
                      value={note}
                      onChange={(event) => setNote(event.target.value)}
                    />
                  </label>
                  <div className="review-actions">
                    <button className="run-button" onClick={() => handleReview("approve")} disabled={loading}>
                      Approve
                    </button>
                    <button className="dismiss-button" onClick={() => handleReview("dismiss")} disabled={loading}>
                      Dismiss
                    </button>
                  </div>
                </div>
              )}

              {report.reviewedBy && (
                <p className="review-history">
                  {report.status.replaceAll("_", " ")} by {report.reviewedBy}
                  {report.reviewedAt ? ` · ${formatWhen(report.reviewedAt)}` : ""}
                  {report.reviewNote ? ` — ${report.reviewNote}` : ""}
                </p>
              )}

              {/* ---------- regenerate: available in every state ---------- */}
              <div className="regen-bar">
                <p>{regenerateHint(report, isLatest)}</p>
                <button
                  className="regen-button"
                  onClick={handleInvestigate}
                  disabled={loading}
                >
                  {loading ? "Analysing…" : "↻ Regenerate report"}
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </aside>
  );
}
