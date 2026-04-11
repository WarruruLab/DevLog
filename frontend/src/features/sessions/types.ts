export type SessionStatusTone = 'neutral' | 'progress' | 'success' | 'warning' | 'danger'

export interface SessionSummary {
  sessionId: string
  sourceSessionId: string
  title: string
  sessionStatus: string
  syncStatus: string
  analysisStatus: string
  totalMessageCount: number
  syncedMessageCount: number
  structuredMessageCount: number
  unstructuredMessageCount: number
  blockCount: number
  lastMessageAt: string | null
  lastSyncedAt: string | null
  lastAnalyzedAt: string | null
}

export interface SessionDetail extends SessionSummary {
  syncErrorMessage: string | null
  analysisErrorMessage: string | null
}

export interface SessionBlock {
  blockId: number
  sessionId: string
  sequenceNo: number | null
  blockType: string | null
  title: string | null
  summary: string | null
  sourceMessageCount: number | null
  messageIds: string[]
}

export interface AnalysisTriggerResponse {
  sessionId: string
  sessionStatus: string
  analysisStatus: string
  pendingMessageCount: number
  message: string
}

export interface DraftResponse {
  draftId: number
  sessionId: string
  versionNo: number
  status: string
  title: string | null
  contentMarkdown: string
  selectedBlockIds: number[]
  createdAt: string | null
}

export interface CreateDraftRequest {
  sessionId: string
  selectedBlockIds: number[]
}

export type SessionListState = 'loading' | 'ready' | 'empty' | 'error'
export type SessionDetailState =
  | 'idle'
  | 'structuring'
  | 'partialStructured'
  | 'structured'
  | 'analysisFailed'

export interface SessionListModel {
  state: SessionListState
  sessions: SessionSummary[]
  error: string | null
  refresh: () => Promise<void>
}

export interface SessionDetailModel {
  state: SessionDetailState
  detail: SessionDetail | null
  blocks: SessionBlock[]
  selectedBlockIds: number[]
  isDraftCreating: boolean
  error: string | null
  load: () => Promise<void>
  retryAnalysis: () => Promise<void>
  toggleBlock: (blockId: number) => void
  selectAll: () => void
  clearSelection: () => void
  createDraft: () => Promise<DraftResponse | null>
}
