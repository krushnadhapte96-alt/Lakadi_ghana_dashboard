import { fetchFileBlob } from '@/api/documents'
import { saveBlob } from '@/api/download'
import type { DocumentItem } from '@/api/documents'

/** Streams the protected file to disk under its original name. */
export async function downloadDocument(item: DocumentItem): Promise<void> {
  const blob = await fetchFileBlob(item.id, true)
  saveBlob(blob, item.fileName)
}
