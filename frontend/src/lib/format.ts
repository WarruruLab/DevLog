const formatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '기록 없음';
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return formatter.format(parsed);
}

export function formatCount(value: number | null | undefined, suffix: string) {
  return `${value ?? 0}${suffix}`;
}
