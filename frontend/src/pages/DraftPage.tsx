import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { marked } from 'marked';
import { api } from '../lib/api';
import { formatDateTime } from '../lib/format';
import { useCopyToClipboard } from '../lib/hooks';
import type { Draft, RequestStatus } from '../types/api';

export function DraftPage() {
  const { draftId = '' } = useParams();
  const location = useLocation();
  const seededDraft = (location.state as { draft?: Draft } | null)?.draft ?? null;
  const [draft, setDraft] = useState<Draft | null>(seededDraft);
  const [status, setStatus] = useState<RequestStatus>(seededDraft ? 'success' : 'loading');
  const [error, setError] = useState<string | null>(null);
  const [showRaw, setShowRaw] = useState(false);
  const { copied, copy } = useCopyToClipboard();

  useEffect(() => {
    if (seededDraft && String(seededDraft.draftId) === draftId) {
      return;
    }

    async function loadDraft() {
      setStatus('loading');
      setError(null);
      try {
        const payload = await api.getDraft(draftId);
        setDraft(payload);
        setStatus('success');
      } catch (loadError) {
        setStatus('error');
        setError(loadError instanceof Error ? loadError.message : '초안 정보를 불러오지 못했습니다.');
      }
    }

    void loadDraft();
  }, [draftId, seededDraft]);

  const renderedMarkdown = draft?.contentMarkdown ? marked.parse(draft.contentMarkdown) : '';

  return (
    <section className="page page--draft">
      <div className="detail-head">
        <div>
          <Link className="back-link" to={draft ? `/sessions/${draft.sessionId}` : '/sessions'}>
            세션으로 돌아가기
          </Link>
          <span className="eyebrow">Markdown Draft</span>
          <h1>{draft?.title || '생성된 초안'}</h1>
          <p className="page-copy">선택한 블록 기준으로 만든 markdown 초안입니다. 읽고 복사하거나 다시 생성할 수 있습니다.</p>
        </div>
        <div className="button-row">
          <button
            className="button button--secondary"
            disabled={!draft?.contentMarkdown}
            onClick={() => {
              if (draft?.contentMarkdown) {
                void copy(draft.contentMarkdown);
              }
            }}
          >
            {copied ? '복사됨' : 'markdown 복사'}
          </button>
          {draft ? (
            <Link className="button button--primary" to={`/sessions/${draft.sessionId}`}>
              블록 다시 선택
            </Link>
          ) : null}
        </div>
      </div>

      {status === 'loading' ? (
        <div className="panel panel--state">
          <div className="loader" />
          <p>초안을 불러오고 있습니다.</p>
        </div>
      ) : null}

      {status === 'error' ? (
        <div className="panel panel--state panel--danger">
          <h2>초안을 불러오지 못했습니다</h2>
          <p>{error}</p>
        </div>
      ) : null}

      {status === 'success' && draft ? (
        <div className="draft-layout">
          <aside className="panel draft-meta">
            <div className="section-head">
              <div>
                <h2>초안 메타</h2>
                <p>결과를 다시 확인하고 원문 markdown을 볼 수 있습니다.</p>
              </div>
            </div>
            <div className="meta-grid">
              <div>
                <strong>상태</strong>
                <span>{draft.status}</span>
              </div>
              <div>
                <strong>버전</strong>
                <span>{draft.versionNo ?? '-'}</span>
              </div>
              <div>
                <strong>선택 블록</strong>
                <span>{draft.selectedBlockIds.length}개</span>
              </div>
              <div>
                <strong>생성 시각</strong>
                <span>{formatDateTime(draft.createdAt)}</span>
              </div>
            </div>
            <button className="button button--ghost button--wide" onClick={() => setShowRaw((current) => !current)}>
              {showRaw ? '미리보기 보기' : 'raw markdown 보기'}
            </button>
          </aside>

          <article className="panel markdown-panel">
            {showRaw ? (
              <pre className="markdown-raw">{draft.contentMarkdown || '생성된 내용이 없습니다.'}</pre>
            ) : (
              <div
                className="markdown-body"
                dangerouslySetInnerHTML={{ __html: typeof renderedMarkdown === 'string' ? renderedMarkdown : '' }}
              />
            )}
          </article>
        </div>
      ) : null}
    </section>
  );
}
