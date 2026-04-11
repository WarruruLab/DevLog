import { createElement as h } from 'react'
import type {
  SessionBlock,
  SessionDetail,
  SessionDetailState,
  SessionStatusTone,
  SessionSummary,
} from './types'

function formatRelative(value: string | null): string {
  if (!value) return '기록 없음'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(parsed)
}

function toneLabel(tone: SessionStatusTone): string {
  if (tone === 'success') return '세션-badge success'
  if (tone === 'warning') return '세션-badge warning'
  if (tone === 'danger') return '세션-badge danger'
  if (tone === 'progress') return '세션-badge progress'
  return '세션-badge'
}

export function sessionStatusMeta(session: SessionSummary | SessionDetail): {
  label: string
  tone: SessionStatusTone
} {
  if (session.analysisStatus === 'FAILED' || session.syncStatus === 'FAILED') {
    return { label: '구조화 실패', tone: 'danger' }
  }
  if (session.unstructuredMessageCount > 0) {
    return {
      label: session.blockCount > 0 ? '구조화 진행 중' : '분석 대기 중',
      tone: 'warning',
    }
  }
  if (session.blockCount > 0) {
    return { label: '구조화 완료', tone: 'success' }
  }
  return { label: '세션 준비됨', tone: 'neutral' }
}

export function SessionStatusBadge(props: { label: string; tone: SessionStatusTone }) {
  return h('span', { className: toneLabel(props.tone) }, props.label)
}

export function SessionListItem(props: {
  session: SessionSummary
  active?: boolean
  onOpen?: (sessionId: string) => void
}) {
  const status = sessionStatusMeta(props.session)
  return h(
    'button',
    {
      type: 'button',
      className: props.active ? '세션-listItem active' : '세션-listItem',
      onClick: () => props.onOpen?.(props.session.sessionId),
    },
    h(
      'div',
      { className: '세션-listItemHeader' },
      h(
        'div',
        { className: '세션-stack' },
        h('p', { className: '세션-overline' }, props.session.sourceSessionId),
        h('h3', { className: '세션-itemTitle' }, props.session.title),
      ),
      h(SessionStatusBadge, status),
    ),
    h(
      'div',
      { className: '세션-itemMetaRow' },
      h('span', null, `메시지 ${props.session.totalMessageCount}개`),
      h('span', null, `블록 ${props.session.blockCount}개`),
      h('span', null, `최근 동기화 ${formatRelative(props.session.lastSyncedAt)}`),
    ),
    h(
      'div',
      { className: '세션-itemFoot' },
      h('span', null, `구조화 ${props.session.structuredMessageCount}/${props.session.totalMessageCount}`),
      h('span', null, `미구조화 ${props.session.unstructuredMessageCount}개`),
    ),
  )
}

export function StructureProgressOverlay(props: {
  state: SessionDetailState
  detail: SessionDetail
  onRetry: () => void
}) {
  const total = Math.max(props.detail.totalMessageCount, 1)
  const progress = Math.round((props.detail.structuredMessageCount / total) * 100)
  const isFailure = props.state === 'analysisFailed'
  const title = isFailure ? '구조화를 마치지 못했습니다' : '세션 메시지를 블록으로 정리하는 중입니다'
  const body = isFailure
    ? props.detail.analysisErrorMessage || '분석 결과를 아직 받지 못했습니다.'
    : props.state === 'partialStructured'
      ? '일부 블록이 먼저 준비되었습니다. 남은 메시지를 계속 구조화하고 있어요.'
      : '아직 일부 메시지를 분석하고 있어요. 완료되면 선택 가능한 블록이 표시됩니다.'

  return h(
    'div',
    { className: isFailure ? '세션-overlay failure' : '세션-overlay' },
    h(
      'div',
      { className: '세션-overlayPanel' },
      h('div', { className: isFailure ? '세션-overlayIcon failure' : '세션-overlayIcon' }),
      h('p', { className: '세션-overline' }, isFailure ? 'Analysis Failed' : 'Structuring'),
      h('h3', { className: '세션-overlayTitle' }, title),
      h('p', { className: '세션-overlayText' }, body),
      h(
        'div',
        { className: '세션-progressMeta' },
        h('span', null, `구조화 ${props.detail.structuredMessageCount}개`),
        h('span', null, `남은 메시지 ${props.detail.unstructuredMessageCount}개`),
      ),
      h(
        'div',
        { className: '세션-progressTrack', 'aria-hidden': true },
        h('span', {
          className: '세션-progressFill',
          style: { width: `${Math.max(progress, isFailure ? 18 : 8)}%` },
        }),
      ),
      h(
        'div',
        { className: '세션-overlayActions' },
        h(
          'button',
          {
            type: 'button',
            className: '세션-secondaryButton',
            onClick: props.onRetry,
          },
          isFailure ? '분석 다시 요청' : '상태 다시 확인',
        ),
      ),
    ),
  )
}

function blockTypeLabel(blockType: string | null): string {
  if (!blockType) return 'BLOCK'
  return blockType.replace(/_/g, ' ')
}

export function BlockList(props: {
  blocks: SessionBlock[]
  selectedBlockIds: number[]
  disabled?: boolean
  onToggle: (blockId: number) => void
}) {
  return h(
    'div',
    { className: '세션-blockList' },
    props.blocks.map((block) => {
      const selected = props.selectedBlockIds.includes(block.blockId)
      return h(
        'button',
        {
          type: 'button',
          key: block.blockId,
          disabled: props.disabled,
          className: selected ? '세션-blockCard selected' : '세션-blockCard',
          onClick: () => props.onToggle(block.blockId),
        },
        h(
          'div',
          { className: '세션-blockHeader' },
          h(
            'div',
            { className: '세션-stack' },
            h('p', { className: '세션-overline' }, `BLOCK ${block.sequenceNo ?? '-'}`),
            h('h4', { className: '세션-blockTitle' }, block.title || '제목 없는 블록'),
          ),
          h(SessionStatusBadge, {
            label: selected ? '선택됨' : blockTypeLabel(block.blockType),
            tone: selected ? 'progress' : 'neutral',
          }),
        ),
        h('p', { className: '세션-blockSummary' }, block.summary || '요약이 아직 없습니다.'),
        h(
          'div',
          { className: '세션-itemMetaRow' },
          h('span', null, `메시지 ${block.sourceMessageCount ?? block.messageIds.length}개`),
          h('span', null, `${block.messageIds[0] ?? '-'} ~ ${block.messageIds[block.messageIds.length - 1] ?? '-'}`),
        ),
      )
    }),
  )
}

export function BlockSelectionToolbar(props: {
  totalCount: number
  selectedCount: number
  disabled?: boolean
  onSelectAll: () => void
  onClear: () => void
}) {
  return h(
    'div',
    { className: '세션-toolbar' },
    h(
      'div',
      { className: '세션-toolbarCopy' },
      h('h3', { className: '세션-sectionTitle' }, '글에 포함할 블록을 선택하세요'),
      h('p', { className: '세션-sectionText' }, `선택된 블록 ${props.selectedCount}개 / 전체 ${props.totalCount}개`),
    ),
    h(
      'div',
      { className: '세션-toolbarActions' },
      h('button', { type: 'button', className: '세션-secondaryButton', disabled: props.disabled, onClick: props.onSelectAll }, '전체 선택'),
      h('button', { type: 'button', className: '세션-secondaryButton', disabled: props.disabled, onClick: props.onClear }, '선택 해제'),
    ),
  )
}

export function SessionSummaryPanel(props: {
  detail: SessionDetail
  selectedCount: number
  isDraftCreating: boolean
  onCreateDraft: () => void
}) {
  const status = sessionStatusMeta(props.detail)
  return h(
    'aside',
    { className: '세션-sidePanel' },
    h(
      'div',
      { className: '세션-sideSection' },
      h('p', { className: '세션-overline' }, 'Session'),
      h('h2', { className: '세션-sideTitle' }, props.detail.title),
      h('div', { className: '세션-sideMeta' }, h(SessionStatusBadge, status), h('span', null, `DevTalk ID ${props.detail.sourceSessionId}`)),
    ),
    h(
      'div',
      { className: '세션-metricGrid' },
      metricTile('메시지', `${props.detail.totalMessageCount}개`),
      metricTile('구조화', `${props.detail.structuredMessageCount}개`),
      metricTile('남은 메시지', `${props.detail.unstructuredMessageCount}개`),
      metricTile('블록', `${props.detail.blockCount}개`),
    ),
    h(
      'div',
      { className: '세션-sideSection' },
      h('h3', { className: '세션-sectionTitle' }, '초안 생성'),
      h('p', { className: '세션-sectionText' }, props.selectedCount > 0 ? `선택한 블록 ${props.selectedCount}개로 초안을 만듭니다.` : '핵심 흐름이 되는 블록을 먼저 선택하세요.'),
      h(
        'button',
        {
          type: 'button',
          className: '세션-primaryButton',
          disabled: props.selectedCount === 0 || props.isDraftCreating,
          onClick: props.onCreateDraft,
        },
        props.isDraftCreating ? '초안 생성 중...' : '선택한 블록으로 글 생성',
      ),
    ),
  )
}

function metricTile(label: string, value: string) {
  return h('div', { className: '세션-metricTile' }, h('span', { className: '세션-overline' }, label), h('strong', { className: '세션-metricValue' }, value))
}

export function EmptyState(props: { title: string; description: string; action?: { label: string; onClick: () => void } }) {
  return h(
    'div',
    { className: '세션-emptyState' },
    h('p', { className: '세션-overline' }, 'Empty'),
    h('h3', { className: '세션-sectionTitle' }, props.title),
    h('p', { className: '세션-sectionText' }, props.description),
    props.action ? h('button', { type: 'button', className: '세션-secondaryButton', onClick: props.action.onClick }, props.action.label) : null,
  )
}
