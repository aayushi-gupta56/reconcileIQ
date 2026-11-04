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

export default function InvestigationPanel({
  result,
  onClose,
  onReviewed,
}: Props) {
  const [history, setHistory] = useState<InvestigationReport[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [reviewer, setReviewer] = useState("analyst");
  const [note, setNote] = useState("");

  const report = history[0] ?? null;
  // A dismissed report is closed, so the analyst can ask the AI for a fresh one.
  const canRerun = report?.status === "DISMISSED";

  useEffect(() => {
    if (!result || result.status === "MATCHED") {
      setHistory([]);
      return;
    }
    getInvestigations(result.id)
      .then(setHistory)
      .catch(() => setHistory([]));
  }, [result]);

  if (!result) {
    return (
      <aside className="investigation-panel embedded">
        <p className="eyebrow">DETAIL</p>
        <h2>Select a discrepancy</h2>
        <p>Choose a row to inspect facts, hypotheses and review actions.</p>
      </aside>
    );
  }

  async function handleInvestigate() {
    if (!result) return;
    setLoading(true);
    setError("");
    try {
      const created = await investigateResult(result.id);
      setHistory((current) => [created, ...current]);
      setNote("");
      onReviewed();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Investigation failed");
    } finally {
      setLoading(false);
    }
  }

  async function handleReview(action: "approve" | "dismiss") {
    if (!report) return;
    setLoading(true);
    setError("");
    try {
      const updated = await reviewInvestigation(
        report.id,
        action,
        reviewer,
        note
      );
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

          {error && !report && (
            <div className="investigation-error">
              <strong>Request failed</strong>
              <p>{error}</p>
            </div>
          )}

          {report && (
            <div className="investigation-content">
              <div className="report-meta">
                <span className="pill">{report.status.replaceAll("_", " ")}</span>
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
              {report.status === "PENDING_REVIEW" && (
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
                  {report.status} by {report.reviewedBy}
                  {report.reviewNote ? ` — ${report.reviewNote}` : ""}
                </p>
              )}
              {canRerun && (
                <div className="rerun-box">
                  <p>
                    This investigation was dismissed. Run the AI again to get a
                    fresh analysis; the dismissed report stays in the history.
                  </p>
                  <button
                    className="run-button"
                    onClick={handleInvestigate}
                    disabled={loading}
                  >
                    {loading ? "Analysing…" : "↻ Re-run investigation"}
                  </button>
                </div>
              )}
              {error && (
                <div className="investigation-error">
                  <strong>Request failed</strong>
                  <p>{error}</p>
                </div>
              )}
              {history.length > 1 && (
                <section className="report-section">
                  <h3>History</h3>
                  <ul>
                    {history.map((item) => (
                      <li key={item.id}>
                        #{item.id} {item.status} · {item.validationStatus} ·{" "}
                        {new Date(item.createdAt).toLocaleString()}
                      </li>
                    ))}
                  </ul>
                </section>
              )}
            </div>
          )}
        </>
      )}
    </aside>
  );
}
