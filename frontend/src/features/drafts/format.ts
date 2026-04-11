export function formatDraftTimestamp(value: string): string {
  const parsed = new Date(value)

  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(parsed)
}

export function formatBlockCount(blockIds: number[]): string {
  return `${blockIds.length}개 블록`
}
