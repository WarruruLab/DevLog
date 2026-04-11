import { useCallback, useEffect, useState } from 'react'
import { fetchSessions } from './api'
import type { SessionListModel, SessionSummary } from './types'

export function useSessionList(): SessionListModel {
  const [state, setState] = useState<SessionListModel['state']>('loading')
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setState((current) => (current === 'ready' ? current : 'loading'))
    setError(null)

    try {
      const next = await fetchSessions()
      setSessions(next)
      setState(next.length === 0 ? 'empty' : 'ready')
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '세션 목록을 불러오지 못했습니다.')
      setState('error')
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return {
    state,
    sessions,
    error,
    refresh,
  }
}
