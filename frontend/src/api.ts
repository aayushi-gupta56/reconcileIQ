// Backend origin, e.g. https://reconcileiq-l2wi.onrender.com (no trailing slash).
// When VITE_API_BASE_URL is unset/empty, URLs stay relative ("/api/...") so the
// Vite dev-server proxy (/api -> http://localhost:8080) keeps working locally.
const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL ?? "")
  .trim()
  .replace(/\/+$/, "");

export function apiUrl(path: string): string {
  return `${API_ORIGIN}${path.startsWith("/") ? path : `/${path}`}`;
}

const API_BASE_URL = "/api/v1";

export interface AppConfig {
  dataSource: "DEMO_SCENARIOS" | "LIVE_SANDBOX";
  source: "DEMO" | "SANDBOX";
  label: string;
  paypalConfigured: boolean;
}

export async function getAppConfig(): Promise<AppConfig> {
  const response = await fetch(apiUrl("/api/config"));
  return handleResponse<AppConfig>(response);
}

export interface ReconciliationRun {
  id: number;
  totalOrders: number;
  matchedCount: number;
  discrepancyCount: number;
  unmatchedPaymentsCount: number;
  startedAt: string;
}

export interface ReconciliationResult {
  id: number;
  reconciliationRunId: number;
  merchantOrderId: string | null;
  paypalOrderId: string | null;
  captureId: string | null;
  expectedAmount: number | null;
  actualAmount: number | null;
  expectedCurrency: string | null;
  actualCurrency: string | null;
  status: string;
  matchedTransactionCount: number;
  explanation: string;
  source: "DEMO" | "SANDBOX" | string;
  createdAt: string;
  reviewStatus: string | null;
  investigationId: number | null;
  validationStatus: string | null;
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function runReconciliation(): Promise<ReconciliationRun> {
  const response = await fetch(apiUrl(`${API_BASE_URL}/reconciliation/run`), {
    method: "POST",
  });
  return handleResponse<ReconciliationRun>(response);
}

export async function getReconciliationRuns(): Promise<ReconciliationRun[]> {
  const response = await fetch(apiUrl(`${API_BASE_URL}/reconciliation/runs`));
  return handleResponse<ReconciliationRun[]>(response);
}

export async function getReconciliationResults(
  runId: number
): Promise<ReconciliationResult[]> {
  const response = await fetch(
    apiUrl(`${API_BASE_URL}/reconciliation/runs/${runId}/results`)
  );
  return handleResponse<ReconciliationResult[]>(response);
}

export type InvestigationReport = {
  id: number;
  reconciliationResultId: number;
  summary: string;
  possibleExplanations: string[];
  observedFacts: string[];
  recommendedChecks: string[];
  requiresHumanReview: boolean;
  modelName: string;
  promptVersion: string | null;
  status: string;
  validationStatus: string;
  latencyMs: number | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  createdAt: string;
};

export async function investigateResult(
  resultId: number
): Promise<InvestigationReport> {
  const response = await fetch(
    apiUrl(`/api/v1/reconciliation/results/${resultId}/investigate`),
    { method: "POST" }
  );
  return handleResponse<InvestigationReport>(response);
}

export async function getInvestigations(
  resultId: number
): Promise<InvestigationReport[]> {
  const response = await fetch(
    apiUrl(`/api/v1/reconciliation/results/${resultId}/investigations`)
  );
  return handleResponse<InvestigationReport[]>(response);
}

export async function reviewInvestigation(
  id: number,
  action: "approve" | "dismiss" | "needs-info",
  reviewer: string,
  note: string
): Promise<InvestigationReport> {
  const response = await fetch(apiUrl(`/api/investigations/${id}/${action}`), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reviewer, note }),
  });
  return handleResponse<InvestigationReport>(response);
}

// ---------- Merchant orders ----------

export type MerchantOrder = {
  merchantOrderId: string
  expectedAmount: number
  currency: string
  status: string
  paypalOrderId: string | null
  approvalUrl: string | null
  createdAt: string
}

async function readError(response: Response, fallback: string): Promise<string> {
  const text = await response.text()
  try {
    const data = JSON.parse(text)
    return data.error || data.message || fallback
  } catch {
    return text || fallback
  }
}

export async function createMerchantOrder(
  amount: number,
  currency: string
): Promise<MerchantOrder> {
  const response = await fetch(apiUrl('/api/v1/orders'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount, currency }),
  })

  if (!response.ok) {
    throw new Error(await readError(response, 'Failed to create order'))
  }

  return response.json()
}

export async function listMerchantOrders(): Promise<MerchantOrder[]> {
  const response = await fetch(apiUrl('/api/v1/orders'))

  if (!response.ok) {
    throw new Error(await readError(response, 'Failed to load orders'))
  }

  return response.json()
}
