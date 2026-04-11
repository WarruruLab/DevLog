export interface DraftResponse {
  draftId: number
  sessionId: string
  versionNo: number
  status: string
  title: string
  contentMarkdown: string
  selectedBlockIds: number[]
  createdAt: string
}

export interface CreateDraftRequest {
  sessionId: string
  selectedBlockIds: number[]
}

export type DraftViewMode = 'preview' | 'raw'

export type DraftPagePhase = 'loading' | 'creating' | 'ready' | 'error'
