import { useCallback, useState } from 'react'
import type { DocumentItem } from '@/api/documents'

/** Tracks which document the lightbox is showing inside a filtered list. */
export function useLightbox(documents: DocumentItem[]) {
  const [index, setIndex] = useState<number | null>(null)

  const open = useCallback(
    (item: DocumentItem) => {
      const found = documents.findIndex((candidate) => candidate.id === item.id)
      setIndex(found >= 0 ? found : 0)
    },
    [documents],
  )
  const close = useCallback(() => setIndex(null), [])

  return { index, open, close, setIndex }
}
