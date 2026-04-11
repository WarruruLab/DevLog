import { Link } from 'react-router-dom';
import type { Draft, RequestStatus } from '../../types/api';

interface DraftComposerProps {
  canCreate: boolean;
  draft: Draft | null;
  draftError: string | null;
  draftState: RequestStatus;
  onCreate: () => void;
  selectedBlockIds: number[];
}

export function DraftComposer({
  canCreate,
  draft,
  draftError,
  draftState,
  onCreate,
  selectedBlockIds,
}: DraftComposerProps) {
  return (
    <aside className="draft-panel panel">
      <div className="section-head">
        <div>
          <h2>초안 생성</h2>
          <p>선택한 블록을 바탕으로 블로그 흐름을 정리합니다.</p>
        </div>
        <span className={`badge ${canCreate ? 'badge--success' : 'badge--neutral'}`}>선택 {selectedBlockIds.length}개</span>
      </div>

      <div className="draft-panel__body">
        <div className="draft-state">
          <strong>지금 할 일</strong>
          <p>{canCreate ? '선택한 블록으로 초안을 만들 수 있습니다.' : '먼저 포함할 블록을 선택하세요.'}</p>
        </div>

        <button className="button button--primary button--wide" disabled={!canCreate || draftState === 'loading'} onClick={onCreate}>
          {draftState === 'loading' ? '초안 생성 중...' : '선택한 블록으로 글 생성'}
        </button>

        {draftState === 'error' ? (
          <div className="panel panel--danger inline-panel">
            <h3>초안을 생성하지 못했습니다</h3>
            <p>{draftError || '잠시 후 다시 시도해주세요. 선택한 블록은 유지됩니다.'}</p>
          </div>
        ) : null}

        {draft ? (
          <div className="draft-preview">
            <div className="draft-preview__head">
              <div>
                <strong>마지막 초안</strong>
                <h3>{draft.title}</h3>
              </div>
              <span className={`badge ${draft.status === 'COMPLETED' ? 'badge--success' : 'badge--warning'}`}>
                {draft.status}
              </span>
            </div>
            <p className="draft-preview__copy">
              {draft.contentMarkdown
                ? `${draft.contentMarkdown.slice(0, 180)}${draft.contentMarkdown.length > 180 ? '...' : ''}`
                : '아직 생성된 markdown 본문이 없습니다.'}
            </p>
            <div className="button-row">
              <Link className="button button--secondary" to={`/drafts/${draft.draftId}`}>
                markdown 보기
              </Link>
            </div>
          </div>
        ) : (
          <div className="draft-empty">
            <p>생성된 markdown 초안은 여기에 요약으로 표시됩니다.</p>
          </div>
        )}
      </div>
    </aside>
  );
}
