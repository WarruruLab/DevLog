/// <reference types="vite/client" />

import type { CreateDraftRequest, DraftResponse } from './types'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

async function parseErrorResponse(response: Response): Promise<string> {
  const fallbackMessage = `Request failed with status ${response.status}`
  const contentType = response.headers.get('content-type') ?? ''

  if (contentType.includes('application/json')) {
    const payload = (await response.json()) as { message?: string }
    return payload.message ?? fallbackMessage
  }

  const text = await response.text()
  return text || fallbackMessage
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  if (!response.ok) {
    throw new Error(await parseErrorResponse(response))
  }

  return (await response.json()) as T
}

export function fetchDraft(draftId: number): Promise<DraftResponse> {
  return requestJson<DraftResponse>(`/drafts/${draftId}`)
}

export function createDraft(payload: CreateDraftRequest): Promise<DraftResponse> {
  return requestJson<DraftResponse>('/drafts', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
