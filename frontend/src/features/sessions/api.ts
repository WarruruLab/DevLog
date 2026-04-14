import { mockBlocks, mockSessionDetails, mockSessions } from './mockData'
import type {
  AnalysisTriggerResponse,
  CreateDraftRequest,
  DraftResponse,
  SessionBlock,
  SessionDetail,
  SessionSummary,
} from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

const JSON_HEADERS = {
  'Content-Type': 'application/json',
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Request failed with ${response.status}`)
  }

  return (await response.json()) as T
}

async function tryFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init)
  return readJson<T>(response)
}

export async function fetchSessions(): Promise<SessionSummary[]> {
  try {
    return await tryFetch<SessionSummary[]>('/api/sessions')
  } catch {
    return mockSessions
  }
}

export async function fetchSessionDetail(sessionId: string): Promise<SessionDetail> {
  try {
    return await tryFetch<SessionDetail>(`/api/sessions/${sessionId}`)
  } catch {
    const detail = mockSessionDetails[sessionId]
    if (!detail) {
      throw new Error(`Session not found: ${sessionId}`)
    }

    return detail
  }
}

export async function fetchSessionBlocks(sessionId: string): Promise<SessionBlock[]> {
  try {
    return await tryFetch<SessionBlock[]>(`/api/sessions/${sessionId}/blocks`)
  } catch {
    return mockBlocks[sessionId] ?? []
  }
}

export async function triggerAnalysis(sessionId: string): Promise<AnalysisTriggerResponse> {
  try {
    return await tryFetch<AnalysisTriggerResponse>(`/api/analysis/${sessionId}`, {
      method: 'POST',
    })
  } catch {
    return {
      sessionId,
      sessionStatus: 'READY',
      analysisStatus: 'RUNNING',
      pendingMessageCount: mockSessionDetails[sessionId]?.unstructuredMessageCount ?? 0,
      message: 'Mock analysis accepted.',
    }
  }
}

export async function createDraft(request: CreateDraftRequest): Promise<DraftResponse> {
  try {
    return await tryFetch<DraftResponse>('/api/drafts', {
      method: 'POST',
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    })
  } catch {
    return {
      draftId: Date.now(),
      sessionId: request.sessionId,
      versionNo: 1,
      status: 'READY',
      title: 'Mock draft',
      contentMarkdown: '# Mock draft\n\n선택한 블록을 기준으로 생성된 임시 초안입니다.',
      selectedBlockIds: request.selectedBlockIds,
      createdAt: new Date().toISOString(),
    }
  }
}
