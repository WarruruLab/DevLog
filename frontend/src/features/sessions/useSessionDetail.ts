import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  createDraft as createDraftRequest,
  fetchSessionBlocks,
  fetchSessionDetail,
  triggerAnalysis,
} from './api'
import type {
  DraftResponse,
  SessionBlock,
  SessionDetail,
  SessionDetailModel,
  SessionDetailState,
} from './types'

function deriveState(detail: SessionDetail | null, blocks: SessionBlock[]): SessionDetailState {
  if (!detail) {
    return 'idle'
  }

  if (detail.analysisStatus === 'FAILED' || detail.syncStatus === 'FAILED') {
    return 'analysisFailed'
  }

  if (detail.unstructuredMessageCount > 0 && blocks.length > 0) {
    return 'partialStructured'
  }

  if (detail.unstructuredMessageCount > 0) {
    return 'structuring'
  }

  if (blocks.length > 0 || detail.blockCount > 0) {
    return 'structured'
  }

  return 'idle'
}

export function useSessionDetail(sessionId: string): SessionDetailModel {
  const [detail, setDetail] = useState<SessionDetail | null>(null)
  const [blocks, setBlocks] = useState<SessionBlock[]>([])
  const [selectedBlockIds, setSelectedBlockIds] = useState<number[]>([])
  const [isDraftCreating, setIsDraftCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const analysisTriggeredRef = useRef(false)

  const load = useCallback(async () => {
    if (!sessionId) {
      return
    }

    setError(null)

    try {
      const [nextDetail, nextBlocks] = await Promise.all([
        fetchSessionDetail(sessionId),
        fetchSessionBlocks(sessionId),
      ])

      setDetail(nextDetail)
      setBlocks(nextBlocks)
      setSelectedBlockIds((current) =>
        current.filter((blockId) => nextBlocks.some((block) => block.blockId === blockId)),
      )
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '세션 상세를 불러오지 못했습니다.')
    }
  }, [sessionId])

  const retryAnalysis = useCallback(async () => {
    if (!sessionId) {
      return
    }

    setError(null)

    try {
      await triggerAnalysis(sessionId)
      analysisTriggeredRef.current = true
      await load()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '구조화를 다시 요청하지 못했습니다.')
    }
  }, [load, sessionId])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!detail || detail.unstructuredMessageCount <= 0 || analysisTriggeredRef.current) {
      return
    }

    analysisTriggeredRef.current = true
    void triggerAnalysis(sessionId).catch(() => undefined)
  }, [detail, sessionId])

  const state = useMemo(() => deriveState(detail, blocks), [blocks, detail])

  useEffect(() => {
    if (state !== 'structuring' && state !== 'partialStructured') {
      return
    }

    const timer = window.setInterval(() => {
      void load()
    }, 4000)

    return () => {
      window.clearInterval(timer)
    }
  }, [load, state])

  const toggleBlock = useCallback((blockId: number) => {
    setSelectedBlockIds((current) =>
      current.includes(blockId)
        ? current.filter((candidate) => candidate !== blockId)
        : [...current, blockId],
    )
  }, [])

  const selectAll = useCallback(() => {
    setSelectedBlockIds(blocks.map((block) => block.blockId))
  }, [blocks])

  const clearSelection = useCallback(() => {
    setSelectedBlockIds([])
  }, [])

  const createDraft = useCallback(async (): Promise<DraftResponse | null> => {
    if (!detail || selectedBlockIds.length === 0) {
      return null
    }

    setIsDraftCreating(true)
    setError(null)

    try {
      return await createDraftRequest({
        sessionId: detail.sessionId,
        selectedBlockIds,
      })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '초안을 생성하지 못했습니다.')
      return null
    } finally {
      setIsDraftCreating(false)
    }
  }, [detail, selectedBlockIds])

  return {
    state,
    detail,
    blocks,
    selectedBlockIds,
    isDraftCreating,
    error,
    load,
    retryAnalysis,
    toggleBlock,
    selectAll,
    clearSelection,
    createDraft,
  }
}
