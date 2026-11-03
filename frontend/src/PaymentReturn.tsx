import { useEffect, useState, useRef } from "react";

type PaymentResult = {
  merchantOrderId?: string;
  paypalOrderId?: string;
  captureId?: string;
  amount?: string;
  currency?: string;
  status?: string;
  message?: string;
};

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
      <main>
        <h1>Completing your payment</h1>
        <p>Verifying the approved order with PayPal...</p>
      </main>
    );
  }

  if (error) {
    return (
      <main>
        <h1>Payment needs attention</h1>
        <p>{error}</p>
        <p>
          Check the backend logs and PayPal Sandbox order status before
          attempting another capture.
        </p>
      </main>
    );
  }

  return (
    <main>
      <h1>Payment completed</h1>
      <p>PayPal has confirmed the capture.</p>

      <dl>
        <dt>Merchant order</dt>
        <dd>{result?.merchantOrderId}</dd>

        <dt>PayPal order</dt>
        <dd>{result?.paypalOrderId}</dd>

        <dt>Capture ID</dt>
        <dd>{result?.captureId ?? "Already recorded"}</dd>

        <dt>Amount</dt>
        <dd>
          {result?.amount && result?.currency
            ? `${result.amount} ${result.currency}`
            : "See transaction records"}
        </dd>

        <dt>Status</dt>
        <dd>{result?.status}</dd>
      </dl>

      <a href="/">Return to ReconcileIQ</a>
    </main>
  );
}