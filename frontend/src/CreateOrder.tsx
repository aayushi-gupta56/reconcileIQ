import { useEffect, useState, type FormEvent } from 'react'
import {
  createMerchantOrder,
  listMerchantOrders,
  type MerchantOrder,
} from './api'
import './CreateOrder.css'

const CURRENCIES = ['USD', 'EUR', 'GBP', 'CAD', 'AUD']

// Mirrors the backend rules in MerchantOrderService.createOrder
function validateAmount(raw: string): string | null {
  const value = raw.trim()
  if (!value) return 'Enter an amount.'
  if (!/^\d+(\.\d{1,2})?$/.test(value)) {
    return 'Use a number with at most two decimal places, for example 100.00.'
  }
  if (Number(value) <= 0) return 'Amount must be greater than zero.'
  return null
}

function formatDate(iso: string) {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString()
}

function statusClass(status: string) {
  if (status === 'COMPLETED') return 'co-pill ok'
  if (status === 'AWAITING_APPROVAL') return 'co-pill warn'
  if (status === 'PAYPAL_CREATION_FAILED') return 'co-pill bad'
  return 'co-pill'
}

type Props = { onBack: () => void }

export default function CreateOrder({ onBack }: Props) {
  const [amount, setAmount] = useState('100.00')
  const [currency, setCurrency] = useState('USD')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState<MerchantOrder | null>(null)

  const [orders, setOrders] = useState<MerchantOrder[]>([])
  const [loadingOrders, setLoadingOrders] = useState(false)
  const [listError, setListError] = useState('')

  async function refreshOrders() {
    setLoadingOrders(true)
    setListError('')
    try {
      setOrders(await listMerchantOrders())
    } catch (err) {
      setListError(err instanceof Error ? err.message : 'Failed to load orders')
    } finally {
      setLoadingOrders(false)
    }
  }

  useEffect(() => {
    void refreshOrders()
  }, [])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()

    const problem = validateAmount(amount)
    if (problem) {
      setError(problem)
      return
    }

    setSubmitting(true)
    setError('')
    setCreated(null)

    try {
      const order = await createMerchantOrder(Number(amount), currency)
      setCreated(order)
      await refreshOrders()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create order')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="shell">
      <nav className="topbar">
        <div className="brand-mark">R</div>
        <span className="brand">
          Reconcile<span>IQ</span>
        </span>
        <button className="co-link" onClick={onBack}>
          ← Back
        </button>
      </nav>

      <section className="co-section">
        <p className="eyebrow">MERCHANT ORDERS</p>
        <h1 className="co-title">Create a merchant order</h1>
        <p className="co-intro">
          Creates the order in ReconcileIQ and a matching PayPal sandbox order.
          Approve it with a sandbox buyer account to complete the payment.
        </p>

        <form className="co-card" onSubmit={handleSubmit} noValidate>
          <div className="co-row">
            <label className="co-field">
              <span>Amount</span>
              <input
                type="text"
                inputMode="decimal"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="100.00"
                disabled={submitting}
              />
            </label>

            <label className="co-field">
              <span>Currency</span>
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                disabled={submitting}
              >
                {CURRENCIES.map((code) => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {error && <p className="co-error">{error}</p>}

          <button className="co-primary" type="submit" disabled={submitting}>
            {submitting ? 'Creating order…' : 'Create order'}
          </button>
        </form>

        {created && (
          <div className="co-card co-success">
            <strong>Order created</strong>
            <dl>
              <dt>Merchant order</dt>
              <dd>{created.merchantOrderId}</dd>
              <dt>PayPal order</dt>
              <dd>{created.paypalOrderId ?? '-'}</dd>
              <dt>Expected amount</dt>
              <dd>
                {created.expectedAmount.toFixed(2)} {created.currency}
              </dd>
              <dt>Status</dt>
              <dd>{created.status}</dd>
            </dl>

            {created.approvalUrl && (
              <a className="co-primary" href={created.approvalUrl}>
                Approve in PayPal sandbox →
              </a>
            )}
          </div>
        )}
      </section>

      <section className="co-section">
        <div className="co-list-header">
          <h2>Recent orders</h2>
          <button
            className="co-link"
            onClick={() => void refreshOrders()}
            disabled={loadingOrders}
          >
            {loadingOrders ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>

        {listError && <p className="co-error">{listError}</p>}

        {!listError && orders.length === 0 && !loadingOrders && (
          <p className="co-muted">No orders yet.</p>
        )}

        {orders.length > 0 && (
          <div className="co-table-wrap">
            <table className="co-table">
              <thead>
                <tr>
                  <th>Merchant order</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>PayPal order</th>
                  <th>Created</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {orders.map((order) => (
                  <tr key={order.merchantOrderId}>
                    <td>{order.merchantOrderId}</td>
                    <td>
                      {order.expectedAmount.toFixed(2)} {order.currency}
                    </td>
                    <td>
                      <span className={statusClass(order.status)}>
                        {order.status}
                      </span>
                    </td>
                    <td>{order.paypalOrderId ?? '-'}</td>
                    <td>{formatDate(order.createdAt)}</td>
                    <td>
                      {order.status === 'AWAITING_APPROVAL' &&
                        order.approvalUrl && (
                          <a className="co-link" href={order.approvalUrl}>
                            Approve →
                          </a>
                        )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <footer>ReconcileIQ · Sandbox data only · No real money movement</footer>
    </main>
  )
}