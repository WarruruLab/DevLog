import { useDeferredValue } from 'react'
import { marked } from 'marked'

import type { DraftViewMode } from './types'

interface MarkdownViewerProps {
  markdown: string
  mode: DraftViewMode
}

export function MarkdownViewer({ markdown, mode }: MarkdownViewerProps) {
  const deferredMarkdown = useDeferredValue(markdown)

  if (mode === 'raw') {
    return (
      <pre className="markdown-raw" aria-label="Raw markdown">
        {deferredMarkdown}
      </pre>
    )
  }

  const renderedMarkdown = marked.parse(deferredMarkdown, {
    breaks: true,
    gfm: true,
  })

  return (
    <article
      className="markdown-rendered"
      dangerouslySetInnerHTML={{ __html: renderedMarkdown }}
    />
  )
}
