import type {
  AnalysisTriggerResponse,
  CreateDraftRequest,
  Draft,
  SessionBlock,
  SessionDetail,
  SessionSummary,
} from '../types/api';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    try {
      const payload = (await response.json()) as { message?: string };
      if (payload.message) {
        message = payload.message;
      }
    } catch {
      // noop
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const api = {
  getSessions: () => request<SessionSummary[]>('/api/sessions'),
  getSessionDetail: (sessionId: string) =>
    request<SessionDetail>(`/api/sessions/${encodeURIComponent(sessionId)}`),
  getSessionBlocks: (sessionId: string) =>
    request<SessionBlock[]>(`/api/sessions/${encodeURIComponent(sessionId)}/blocks`),
  triggerAnalysis: (sessionId: string) =>
    request<AnalysisTriggerResponse>(`/api/analysis/${encodeURIComponent(sessionId)}`, { method: 'POST' }),
  createDraft: (payload: CreateDraftRequest) =>
    request<Draft>('/api/drafts', { method: 'POST', body: JSON.stringify(payload) }),
  getDraft: (draftId: string | number) => request<Draft>(`/api/drafts/${encodeURIComponent(String(draftId))}`),
};

export { ApiError };
