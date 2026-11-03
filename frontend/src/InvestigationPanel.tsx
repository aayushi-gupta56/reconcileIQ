import { useState } from 'react'
import './InvestigationPanel.css'
import {
  investigateResult,
  type InvestigationReport
} from './api'

type Props = {
  resultId: number
  onClose: () => void
}

export default function InvestigationPanel({
  resultId,
  onClose
}: Props) {
  const [report, setReport] = useState<InvestigationReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleInvestigate() {
    setLoading(true)
    setError('')
    setReport(null)

    try {
      const result = await investigateResult(resultId)
      setReport(result)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Something went wrong during investigation'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="investigation-overlay">
      <section className="investigation-panel">
        <div className="investigation-header">
          <div>
            <p className="eyebrow">AI-ASSISTED ANALYSIS</p>
            <h2>Payment investigation</h2>
            <p>Reconciliation result #{resultId}</p>
          </div>

          <button
            className="close-button"
            onClick={onClose}
            aria-label="Close investigation"
          >
            ×
          </button>
        </div>

        {!report && !loading && (
          <div className="investigation-empty">
            <h3>Investigate this discrepancy</h3>
            <p>
              Qwen will analyse the reconciliation evidence and
              generate an explanation with recommended next steps.
            </p>

            <button
              className="run-button"
              onClick={handleInvestigate}
            >
              Generate investigation →
            </button>
          </div>
        )}

        {loading && (
          <div className="investigation-loading">
            <div className="loader" />
            <h3>Analysing payment evidence...</h3>
            <p>
              The local AI model is reviewing the discrepancy.
              This may take a few seconds.
            </p>
          </div>
        )}

        {error && (
          <div className="investigation-error">
            <strong>Investigation failed</strong>
            <p>{error}</p>
            <button onClick={handleInvestigate}>
              Try again
            </button>
          </div>
        )}

        {report && (
          <div className="investigation-content">
            <div className="report-meta">
              <span className="pill">
                {report.status.replaceAll('_', ' ')}
              </span>
              <span>Model: {report.modelName}</span>
            </div>

            <section className="report-section">
              <h3>Likely cause</h3>
              <p>{report.likelyCause}</p>
            </section>

            <section className="report-section">
              <h3>Evidence analysed</h3>
              <ul>
                {report.evidence.split('\n').map((item, index) => (
                  <li key={index}>{item}</li>
                ))}
              </ul>
            </section>

            <section className="report-section recommendation">
              <h3>Recommended action</h3>
              <p>{report.recommendedAction}</p>
            </section>

            <div className="review-notice">
              <strong>Human review required</strong>
              <p>
                AI findings are advisory. Verify the underlying
                payment records before taking action.
              </p>
            </div>

            <div className="report-footer">
              <span>Cause unverified: analyst confirmation needed</span>
              <span>
                {new Date(report.createdAt).toLocaleString()}
              </span>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}