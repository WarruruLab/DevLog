import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { DraftComposer } from '../features/drafts/DraftComposer';
import { api } from '../lib/api';
import { formatCount, formatDateTime } from '../lib/format';
import { deriveStructurePhase } from '../lib/session';
import type { Draft, RequestStatus, SessionBlock, SessionDetail } from '../types/api';

const POLL_MS = 3000;

export function SessionDetailPage() {
  const { sessionId = '' } = useParams();
  const navigate = useNavigate();
  const [session, setSession] = useState<SessionDetail | null>(null);
  const [blocks, setBlocks] = useState<SessionBlock[]>([]);
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [selectedBlockIds, setSelectedBlockIds] = useState<number[]>([]);
  const [draftState, setDraftState] = useState<RequestStatus>('idle');
  const [draftError, setDraftError] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const analysisTriggered = useRef(false);

  const structurePhase = useMemo(() => deriveStructurePhase(session, blocks), [session, blocks]);
  const isSelectable = structurePhase === 'structured' || structurePhase === 'partialStructured';

  async function loadDetail() {
    setError(null);
    try {
      const [detailPayload, blockPayload] = await Promise.all([
        api.getSessionDetail(sessionId),
        api.getSessionBlocks(sessionId),
      ]);
      setSession(detailPayload);
      setBlocks(blockPayload);
      setStatus('success');
    } catch (loadError) {
      setStatus('error');
      setError(loadError instanceof Error ? loadError.message : '세션 정보를 불러오지 못했습니다.');
    }
  }

  useEffect(() => {
    analysisTriggered.current = false;
    setStatus('loading');
    void loadDetail();
  }, [sessionId]);

  useEffect(() => {
    if (!sessionId || status !== 'success') {
      return;
    }

    if (!analysisTriggered.current && (structurePhase === 'structuring' || structurePhase === 'partialStructured')) {
      analysisTriggered.current = true;
      void api.triggerAnalysis(sessionId).catch(() => undefined);
    }

    if (structurePhase !== 'structuring' && structurePhase !== 'partialStructured') {
      return;
    }

    const timer = window.setInterval(() => {
      void loadDetail();
    }, POLL_MS);

    return () => window.clearInterval(timer);
  }, [sessionId, status, structurePhase]);

  function toggleBlock(blockId: number) {
    if (!isSelectable) {
      return;
    }

    setSelectedBlockIds((current) =>
      current.includes(blockId) ? current.filter((candidate) => candidate !== blockId) : [...current, blockId],
    );
  }

  async function handleCreateDraft() {
    if (!sessionId || selectedBlockIds.length === 0) {
      return;
    }

    setDraftState('loading');
    setDraftError(null);

    try {
      const payload = await api.createDraft({ sessionId, selectedBlockIds });
      setDraft(payload);
      setDraftState('success');
      navigate(`/drafts/${payload.draftId}`, { state: { draft: payload } });
    } catch (createError) {
      setDraftState('error');
      setDraftError(createError instanceof Error ? createError.message : '초안을 생성하지 못했습니다.');
    }
  }

  return (
    <section className="page page--detail">
      <div className="detail-head">
        <div>
          <Link className="back-link" to="/sessions">
            세션 목록
          </Link>
          <span className="eyebrow">Session Detail</span>
          <h1>{session?.title || sessionId}</h1>
          <p className="page-copy">구조화 상태를 확인한 뒤 블록을 고르고, 선택한 흐름으로 초안을 생성합니다.</p>
        </div>
        <button className="button button--secondary" onClick={() => void loadDetail()}>
          상태 새로고침
        </button>
      </div>

      {status === 'loading' ? (
        <div className="panel panel--state">
          <div className="loader" />
          <p>세션 상세를 불러오고 있습니다.</p>
        </div>
      ) : null}

      {status === 'error' ? (
        <div className="panel panel--state panel--danger">
          <h2>세션 상세를 불러오지 못했습니다</h2>
          <p>{error}</p>
        </div>
      ) : null}

      {status === 'success' && session ? (
        <div className="detail-layout">
          <section className="workspace panel">
            <div className="workspace__meta">
              <div className="meta-grid">
                <div>
                  <strong>세션 ID</strong>
                  <span>{session.sourceSessionId}</span>
                </div>
                <div>
                  <strong>메시지</strong>
                  <span>{formatCount(session.totalMessageCount, '개')}</span>
                </div>
                <div>
                  <strong>미구조화</strong>
                  <span>{formatCount(session.unstructuredMessageCount, '개')}</span>
                </div>
                <div>
                  <strong>최근 동기화</strong>
                  <span>{formatDateTime(session.lastSyncedAt ?? session.lastMessageAt)}</span>
                </div>
              </div>
            </div>

            <div className="section-head">
              <div>
                <h2>블록 선택</h2>
                <p>글에 포함할 흐름만 골라 초안을 만듭니다.</p>
              </div>
              <span className={`badge badge--${phaseTone(structurePhase)}`}>{phaseLabel(structurePhase)}</span>
            </div>

            {(structurePhase === 'structuring' || structurePhase === 'partialStructured') && (
              <div className="overlay-card">
                <div className="overlay-card__spinner" />
                <div>
                  <h3>
                    {structurePhase === 'partialStructured' ? '일부 블록이 먼저 준비되었습니다' : '구조화 진행 중'}
                  </h3>
                  <p>
                    {structurePhase === 'partialStructured'
                      ? '나머지 메시지를 계속 구조화하고 있습니다. 준비된 블록은 미리 살펴볼 수 있습니다.'
                      : '아직 일부 메시지를 분석하고 있어요. 완료되면 블록을 선택할 수 있습니다.'}
                  </p>
                </div>
              </div>
            )}

            {structurePhase === 'failed' ? (
              <div className="panel panel--danger inline-panel">
                <h3>구조화가 실패했습니다</h3>
                <p>{session.analysisErrorMessage || '분석 결과를 아직 받지 못했습니다.'}</p>
              </div>
            ) : null}

            <div className="selection-toolbar">
              <div>
                <strong>선택된 블록 {selectedBlockIds.length}개</strong>
                <p>핵심 흐름 우선으로 선택하세요.</p>
              </div>
              <div className="button-row">
                <button className="button button--ghost" disabled={!isSelectable || blocks.length === 0} onClick={() => setSelectedBlockIds(blocks.map((block) => block.blockId))}>
                  전체 선택
                </button>
                <button className="button button--ghost" disabled={selectedBlockIds.length === 0} onClick={() => setSelectedBlockIds([])}>
                  선택 해제
                </button>
              </div>
            </div>

            {blocks.length === 0 ? (
              <div className="panel panel--state inline-panel">
                <h3>아직 구조화된 블록이 없습니다</h3>
                <p>분석이 끝나면 자동으로 표시됩니다.</p>
              </div>
            ) : (
              <div className="block-list">
                {blocks.map((block) => {
                  const selected = selectedBlockIds.includes(block.blockId);
                  return (
                    <button
                      className={`block-card ${selected ? 'block-card--selected' : ''}`}
                      disabled={!isSelectable}
                      key={block.blockId}
                      onClick={() => toggleBlock(block.blockId)}
                      type="button"
                    >
                      <div className="block-card__head">
                        <div>
                          <span className="block-index">#{block.sequenceNo ?? '-'}</span>
                          <h3>{block.title}</h3>
                        </div>
                        <span className={`badge ${selected ? 'badge--success' : 'badge--neutral'}`}>
                          {selected ? '선택됨' : block.blockType}
                        </span>
                      </div>
                      <p>{block.summary}</p>
                      <div className="block-card__meta">
                        <span>{formatCount(block.sourceMessageCount, '개 메시지')}</span>
                        <span>{block.messageIds.length}개 매핑</span>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </section>

          <DraftComposer
            canCreate={selectedBlockIds.length > 0}
            draft={draft}
            draftError={draftError}
            draftState={draftState}
            onCreate={handleCreateDraft}
            selectedBlockIds={selectedBlockIds}
          />
        </div>
      ) : null}
    </section>
  );
}

function phaseLabel(phase: string) {
  switch (phase) {
    case 'structured':
      return '구조화 완료';
    case 'partialStructured':
      return '부분 완료';
    case 'failed':
      return '실패';
    default:
      return '구조화 중';
  }
}

function phaseTone(phase: string) {
  switch (phase) {
    case 'structured':
      return 'success';
    case 'partialStructured':
      return 'warning';
    case 'failed':
      return 'danger';
    default:
      return 'warning';
  }
}
