import type { SessionBlock, SessionDetail, StructurePhase } from '../types/api';

export function deriveStructurePhase(session: SessionDetail | null, blocks: SessionBlock[]): StructurePhase {
  if (!session) {
    return 'structuring';
  }

  if (session.analysisStatus === 'FAILED' || session.sessionStatus === 'FAILED' || session.analysisErrorMessage) {
    return 'failed';
  }

  if (blocks.length > 0 && session.unstructuredMessageCount === 0) {
    return 'structured';
  }

  if (blocks.length > 0 && session.unstructuredMessageCount > 0) {
    return 'partialStructured';
  }

  return 'structuring';
}

export function statusTone(sessionStatus: string, analysisStatus: string) {
  if (sessionStatus === 'FAILED' || analysisStatus === 'FAILED') {
    return 'danger';
  }
  if (sessionStatus === 'READY' && analysisStatus === 'DONE') {
    return 'success';
  }
  if (sessionStatus === 'ANALYZING' || analysisStatus === 'RUNNING') {
    return 'warning';
  }
  return 'neutral';
}
