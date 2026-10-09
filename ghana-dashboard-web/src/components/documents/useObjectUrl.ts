import { useEffect, useState } from 'react'
import { fetchFileBlob } from '@/api/documents'

export interface ResolvedFile {
  /** Document the url belongs to, so a stale blob is never shown for a new one. */
  documentId: number
  url: string | null
}

/**
 * Resolves one authenticated file into an object URL, revoking it on change and
 * unmount so a long gallery session does not leak blobs. Consumers should treat
 * a mismatched documentId as "still loading" instead of reading the url.
 */
export function useObjectUrl(
  documentId: number,
  load: (id: number) => Promise<Blob>,
): ResolvedFile {
  const [resolved, setResolved] = useState<ResolvedFile>({ documentId, url: null })

  useEffect(() => {
    let objectUrl: string | null = null
    let cancelled = false

    load(documentId)
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setResolved({ documentId, url: objectUrl })
      })
      .catch(() => {
        if (!cancelled) setResolved({ documentId, url: null })
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [documentId, load])

  return resolved
}

/** Module-level so its identity is stable and does not re-trigger the effect. */
const loadBlob = (id: number) => fetchFileBlob(id)

/** Null while the blob is still resolving, which is what viewers should render. */
export function useFileUrl(documentId: number): string | null {
  const resolved = useObjectUrl(documentId, loadBlob)
  return resolved.documentId === documentId ? resolved.url : null
}
