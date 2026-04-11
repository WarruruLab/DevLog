export type RequestStatus = 'idle' | 'loading' | 'success' | 'error';

export interface SessionSummary {
  sessionId: string;
  sourceSessionId: string;
  title: string;
  sessionStatus: string;
  syncStatus: string;
  analysisStatus: string;
  totalMessageCount: number;
  syncedMessageCount: number;
  structuredMessageCount: number;
  unstructuredMessageCount: number;
  blockCount: number;
  lastMessageAt: string | null;
  lastSyncedAt: string | null;
  lastAnalyzedAt: string | null;
}

export interface SessionDetail extends SessionSummary {
  syncErrorMessage: string | null;
  analysisErrorMessage: string | null;
}

export interface SessionBlock {
  blockId: number;
  sessionId: string;
  sequenceNo: number | null;
  blockType: string;
  title: string;
  summary: string;
  sourceMessageCount: number | null;
  messageIds: string[];
}

export interface Draft {
  draftId: number;
  sessionId: string;
  versionNo: number | null;
  status: string;
  title: string;
  contentMarkdown: string | null;
  selectedBlockIds: number[];
  createdAt: string | null;
}

export interface AnalysisTriggerResponse {
  sessionId: string;
  sessionStatus: string;
  analysisStatus: string;
  pendingMessageCount: number;
  message: string;
}

export interface CreateDraftRequest {
  sessionId: string;
  selectedBlockIds: number[];
}

export type StructurePhase = 'structuring' | 'partialStructured' | 'structured' | 'failed';
