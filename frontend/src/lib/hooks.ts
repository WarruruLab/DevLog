import { useEffect, useState } from 'react';

export function useCopyToClipboard() {
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) {
      return;
    }

    const timeout = window.setTimeout(() => setCopied(false), 1200);
    return () => window.clearTimeout(timeout);
  }, [copied]);

  async function copy(text: string) {
    await navigator.clipboard.writeText(text);
    setCopied(true);
  }

  return { copied, copy };
}
