const API_BASE_URL = "/api/v1";

export interface AppConfig {
  dataSource: "DEMO_SCENARIOS" | "LIVE_SANDBOX";
  source: "DEMO" | "SANDBOX";
  label: string;
  paypalConfigured: boolean;
}

export async function getAppConfig(): Promise<AppConfig> {
  const response = await fetch("/api/config");
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
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export async function runReconciliation(): Promise<ReconciliationRun> {
  const response = await fetch(`${API_BASE_URL}/reconciliation/run`, {
    method: "POST",
  });

  return handleResponse<ReconciliationRun>(response);
}

export async function getReconciliationRuns(): Promise<ReconciliationRun[]> {
  const response = await fetch(`${API_BASE_URL}/reconciliation/runs`);

  return handleResponse<ReconciliationRun[]>(response);
}

export async function getReconciliationResults(
  runId: number
): Promise<ReconciliationResult[]> {
  const response = await fetch(
    `${API_BASE_URL}/reconciliation/runs/${runId}/results`
  );

  return handleResponse<ReconciliationResult[]>(response);
}

export type InvestigationReport = {
  id: number
  reconciliationResultId: number
  likelyCause: string
  confidence: number
  evidence: string
  recommendedAction: string
  requiresHumanReview: boolean
  modelName: string
  status: string
  createdAt: string
}

export async function investigateResult(
  resultId: number
): Promise<InvestigationReport> {
  const response = await fetch(
    `/api/v1/reconciliation/results/${resultId}/investigate`,
    { method: 'POST' }
  )

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || 'Failed to investigate discrepancy')
  }

  return response.json()
}

export async function getInvestigations(
  resultId: number
): Promise<InvestigationReport[]> {
  const response = await fetch(
    `/api/v1/reconciliation/results/${resultId}/investigations`
  )

  if (!response.ok) {
    throw new Error('Failed to fetch investigation history')
  }

  return response.json()
}