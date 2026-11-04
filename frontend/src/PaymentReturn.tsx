import { useEffect, useState, useRef, type ReactNode } from "react";
import "./PaymentReturn.css";

type PaymentResult = {
  merchantOrderId?: string;
  paypalOrderId?: string;
  captureId?: string;
  amount?: string;
  currency?: string;
  status?: string;
  message?: string;
};

type Tone = "loading" | "success" | "error" | "neutral";

const TONE_ICON: Record<Tone, string> = {
  loading: "",
  success: "✓",
  error: "!",
  neutral: "×",
};

function PaymentShell({
  tone,
  title,
  subtitle,
  children,
}: {
  tone: Tone;
  title: string;
  subtitle: string;
  children?: ReactNode;
}) {
  return (
    <main className="payment-page">
      <header className="payment-header">
        <p className="eyebrow">RECONCILEIQ / CHECKOUT</p>
        <h1>{title}</h1>
        <p className="subtitle">{subtitle}</p>
      </header>

      <section className={`payment-card ${tone}`}>
        <div className={`payment-icon ${tone}`} aria-hidden="true">
          {tone === "loading" ? <span className="payment-spinner" /> : TONE_ICON[tone]}
        </div>
        {children}
      </section>
    </main>
  );
}

function statusTone(status?: string) {
  const value = (status ?? "").toUpperCase();
  if (["COMPLETED", "CAPTURED", "APPROVED"].includes(value)) return "ok";
  if (["PENDING", "CREATED"].includes(value)) return "warn";
  if (["FAILED", "DECLINED", "DENIED", "VOIDED"].includes(value)) return "bad";
  return "neutral";
}

function DetailRow({ label, value, mono }: { label: string; value?: string; mono?: boolean }) {
  return (
    <div className="payment-row">
      <dt>{label}</dt>
      <dd className={mono ? "mono" : undefined}>{value || "-"}</dd>
    </div>
  );
}

export function PaymentCancelled() {
  return (
    <PaymentShell
      tone="neutral"
      title="Payment cancelled"
      subtitle="No successful capture has been confirmed."
    >
      <p className="payment-note">
        You left the PayPal checkout before approving the payment. Nothing was
        charged, and the merchant order is still waiting for payment.
      </p>
      <div className="payment-actions">
        <a className="run-button" href="/">Return to ReconcileIQ</a>
      </div>
    </PaymentShell>
  );
}

export default function PaymentReturn() {
  const [result, setResult] = useState<PaymentResult | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const captureStarted = useRef(false);

  useEffect(() => {
    if (captureStarted.current) {
      return;
    }
    
    captureStarted.current = true;
    const params = new URLSearchParams(window.location.search);
    const paypalOrderId = params.get("token");

    if (!paypalOrderId) {
      setError("The PayPal order ID is missing from the return URL.");
      setLoading(false);
      return;
    }

    async function capturePayment() {
      try {
        const response = await fetch(
          `http://localhost:8080/api/v1/orders/${encodeURIComponent(
            paypalOrderId!
          )}/capture`,
          { method: "POST" }
        );

        const data = await response.json();

        if (!response.ok) {
          throw new Error(
            data.message || data.error || "Unable to capture payment."
          );
        }

        setResult(data);
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : "An unexpected error occurred."
        );
      } finally {
        setLoading(false);
      }
    }

    void capturePayment();
  }, []);

  if (loading) {
    return (
      <PaymentShell
        tone="loading"
        title="Completing your payment"
        subtitle="Verifying the approved order with PayPal…"
      >
        <p className="payment-note">
          This only takes a moment. Please don’t close or refresh this page.
        </p>
      </PaymentShell>
    );
  }

  if (error) {
    return (
      <PaymentShell
        tone="error"
        title="Payment needs attention"
        subtitle="We couldn’t confirm the capture with PayPal."
      >
        <div className="payment-alert">{error}</div>
        <p className="payment-note">
          Check the backend logs and PayPal Sandbox order status before
          attempting another capture.
        </p>
        <div className="payment-actions">
          <a className="run-button" href="/">Return to ReconcileIQ</a>
        </div>
      </PaymentShell>
    );
  }

  const amount =
    result?.amount && result?.currency
      ? `${result.amount} ${result.currency}`
      : "See transaction records";

  return (
    <PaymentShell
      tone="success"
      title="Payment completed"
      subtitle="PayPal has confirmed the capture."
    >
      <dl className="payment-details">
        <DetailRow label="Merchant order" value={result?.merchantOrderId} mono />
        <DetailRow label="PayPal order" value={result?.paypalOrderId} mono />
        <DetailRow label="Capture ID" value={result?.captureId ?? "Already recorded"} mono />
        <DetailRow label="Amount" value={amount} />
        <div className="payment-row">
          <dt>Status</dt>
          <dd>
            {result?.status ? (
              <span className={`payment-status ${statusTone(result.status)}`}>
                <i />
                {result.status.replaceAll("_", " ")}
              </span>
            ) : (
              "-"
            )}
          </dd>
        </div>
      </dl>

      <div className="payment-actions">
        <a className="run-button" href="/">Return to ReconcileIQ</a>
      </div>
    </PaymentShell>
  );
}
