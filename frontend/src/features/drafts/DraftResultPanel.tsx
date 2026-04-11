import type { DraftPagePhase, DraftResponse, DraftViewMode } from './types'
import { formatBlockCount, formatDraftTimestamp } from './format'
import { MarkdownViewer } from './MarkdownViewer'

interface DraftResultPanelProps {
  copyLabel: string
  draft: DraftResponse | null
  errorMessage: string | null
  mode: DraftViewMode
  onCopy: () => void
  onModeChange: (mode: DraftViewMode) => void
  onOpenSession: () => void
  onRegenerate: () => void
  phase: DraftPagePhase
}

export function DraftResultPanel({
  copyLabel,
  draft,
  errorMessage,
  mode,
  onCopy,
  onModeChange,
  onOpenSession,
  onRegenerate,
  phase,
}: DraftResultPanelProps) {
  const isBusy = phase === 'loading' || phase === 'creating'
  const isReady = phase === 'ready' && draft !== null

  return (
    <section className="surface draft-result-panel">
      <div className="panel-header">
        <div className="panel-title-block">
          <span className="eyebrow">Draft</span>
          <h1>{draft?.title ?? 'markdown 초안'}</h1>
          <p>
            선택된 블록으로 만든 초안을 읽고, 그대로 복사하거나 같은 흐름으로 다시
            생성할 수 있습니다.
          </p>
        </div>
        <div className="panel-actions">
          <button
            className="button button-secondary"
            disabled={!isReady}
            type="button"
            onClick={onCopy}
          >
            {copyLabel}
          </button>
          <button
            className="button button-secondary"
            disabled={!draft}
            type="button"
            onClick={onOpenSession}
          >
            블록 다시 선택
          </button>
          <button
            className="button button-primary"
            disabled={!draft}
            type="button"
            onClick={onRegenerate}
          >
            다시 생성
          </button>
        </div>
      </div>

      <div className="draft-meta-strip" aria-label="Draft metadata">
        <div>
          <span>세션</span>
          <strong>{draft?.sessionId ?? '-'}</strong>
        </div>
        <div>
          <span>선택 블록</span>
          <strong>{draft ? formatBlockCount(draft.selectedBlockIds) : '-'}</strong>
        </div>
        <div>
          <span>버전</span>
          <strong>{draft ? `v${draft.versionNo}` : '-'}</strong>
        </div>
        <div>
          <span>생성 시각</span>
          <strong>{draft ? formatDraftTimestamp(draft.createdAt) : '-'}</strong>
        </div>
      </div>

      <div className="draft-status-stack">
        {phase === 'loading' ? (
          <div className="status-banner status-banner-progress">
            <div className="status-orb" />
            <div>
              <strong>초안을 불러오는 중</strong>
              <p>저장된 markdown 결과를 조회하고 있습니다.</p>
            </div>
          </div>
        ) : null}

        {phase === 'creating' ? (
          <div className="status-banner status-banner-progress">
            <div className="status-orb status-orb-spin" />
            <div>
              <strong>초안을 다시 만드는 중</strong>
              <p>선택한 블록을 기준으로 제목과 본문 흐름을 재구성하고 있습니다.</p>
            </div>
          </div>
        ) : null}

        {phase === 'error' ? (
          <div className="status-banner status-banner-error">
            <div className="status-orb" />
            <div>
              <strong>초안을 표시하지 못했습니다</strong>
              <p>{errorMessage ?? '잠시 후 다시 시도해주세요.'}</p>
            </div>
          </div>
        ) : null}
      </div>

      <div className="draft-surface">
        <div className="draft-surface-toolbar">
          <div className="segmented-control" role="tablist" aria-label="Draft mode">
            <button
              aria-selected={mode === 'preview'}
              className="segmented-control__button"
              type="button"
              onClick={() => onModeChange('preview')}
            >
              미리보기
            </button>
            <button
              aria-selected={mode === 'raw'}
              className="segmented-control__button"
              type="button"
              onClick={() => onModeChange('raw')}
            >
              raw markdown
            </button>
          </div>
        </div>

        {isReady ? (
          <MarkdownViewer markdown={draft.contentMarkdown} mode={mode} />
        ) : (
          <div className="draft-empty-state">
            <div className="draft-empty-state__glyph" />
            <strong>{isBusy ? '초안 결과를 준비하고 있습니다' : '표시할 초안이 없습니다'}</strong>
            <p>
              {isBusy
                ? '완료되면 markdown 본문이 여기에서 바로 보입니다.'
                : '블록을 다시 선택하거나 초안을 재생성해 결과를 채워주세요.'}
            </p>
          </div>
        )}
      </div>
    </section>
  )
}
