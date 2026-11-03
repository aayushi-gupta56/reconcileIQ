import { useEffect, useState } from 'react'
import PaymentReturn from "./PaymentReturn"
import ReconciliationDashboard from "./ReconciliationDashboard"
import "./ReconciliationDashboard.css"
import CreateOrder from "./CreateOrder"

type Health = { status: string; application: string }

export default function App() {
  const [health, setHealth] = useState<Health | null>(null)
  const [apiError, setApiError] = useState('')
  const [showDashboard, setShowDashboard] = useState(false)
  const [showCreateOrder, setShowCreateOrder] = useState(false)

  useEffect(() => {
    fetch('/api/v1/health')
      .then((response) => {
        if (!response.ok) throw new Error(`API returned ${response.status}`)
        return response.json() as Promise<Health>
      })
      .then(setHealth)
      .catch((error: unknown) => {
        setApiError(error instanceof Error ? error.message : 'Could not reach API')
      })
  }, [])

  if (window.location.pathname === "/payment/return") {
    return <PaymentReturn />
  }

  if (window.location.pathname === "/payment/cancel") {
    return (
      <main>
        <h1>Payment cancelled</h1>
        <p>No successful capture has been confirmed.</p>
        <a href="/">Return to ReconcileIQ</a>
      </main>
    )
  }

  if (showDashboard) {
    return <ReconciliationDashboard />
  }

  if (showCreateOrder) {
    return <CreateOrder onBack={() => setShowCreateOrder(false)} />
  }

  return (
    <main className="shell">
      <nav className="topbar">
        <div className="brand-mark">R</div>
        <span className="brand">Reconcile<span>IQ</span></span>
        <span className="env-label">LOCAL DEVELOPMENT</span>
      </nav>

      <section className="hero">
        <p className="eyebrow">PAYMENTS OPERATIONS · INVESTIGATION WORKSPACE</p>
        <h1>Know what happened<br /><span>to every payment.</span></h1>
        <p className="intro">
          Reconcile payment records, surface exceptions, and investigate the evidence behind them.
        </p>

        <div className="status-card">
          <span className={`status-dot ${health ? 'up' : apiError ? 'down' : ''}`} />
          <div>
            <strong>{health ? 'Backend connected' : apiError ? 'Backend not connected' : 'Checking backend…'}</strong>
            <p>{health ? `${health.application} · ${health.status}` : apiError || 'Contacting localhost:8080'}</p>
          </div>
        </div>

        <div className="hero-actions">
        <button className="run-button" onClick={() => setShowDashboard(true)}>
    Open reconciliation dashboard →
  </button>
  <button className="run-button secondary" onClick={() => setShowCreateOrder(true)}>
    Create merchant order →
  </button>
</div>
      </section>

      <section className="grid">
        <article className="feature">
          <span className="feature-number">01</span>
          <h2>Reconcile</h2>
          <p>Compare ledger entries with payment-provider records using deterministic rules.</p>
          <span className="pill">READY</span>
        </article>

        <article className="feature">
          <span className="feature-number">02</span>
          <h2>Investigate</h2>
          <p>Use evidence-backed AI explanations to help humans resolve exceptions faster.</p>
          <span className="pill">READY</span>
        </article>

        <article className="feature">
          <span className="feature-number">03</span>
          <h2>Audit</h2>
          <p>Track review decisions and export a traceable record of every resolution.</p>
          <span className="pill">READY</span>
        </article>
      </section>

      <footer>ReconcileIQ · Sandbox data only · No real money movement</footer>
    </main>
  )
}