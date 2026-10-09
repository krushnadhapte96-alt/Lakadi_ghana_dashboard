import { api } from '@/api/axios'
import { fetchBlob } from '@/api/download'
import type { ApiResponse } from '@/api/types'

/** Mirrors the chk_documents_type constraint. */
export const DOCUMENT_TYPES = [
  'LAB_REPORT',
  'GHANA_PHOTO',
  'GHANA_VIDEO',
  'SHOP_PHOTO',
  'CERTIFICATE',
  'OTHER',
] as const
export type DocumentType = (typeof DOCUMENT_TYPES)[number]

/** Derived server-side from the stored MIME type, so the viewer never guesses. */
export type DocumentKind = 'IMAGE' | 'VIDEO' | 'PDF'

export const SEDIMENT_LEVELS = ['NONE', 'LOW', 'MEDIUM', 'HIGH'] as const
export type SedimentLevel = (typeof SEDIMENT_LEVELS)[number]

export interface DocumentItem {
  id: number
  type: DocumentType
  title: string
  fileName: string
  fileSize: number
  fileType: string
  kind: DocumentKind
  batchId: number | null
  batchCode: string | null
  notes: string | null
  uploadedByName: string
  createdAt: string
}

export interface DocumentFilters {
  type: DocumentType | ''
  q: string
}

export interface DocumentUpdateInput {
  title: string
  type: DocumentType
  batchId: number | null
  notes: string | null
}

export interface QualityNote {
  id: number
  batchId: number
  colour: string | null
  colourScore: number | null
  aromaScore: number | null
  tasteScore: number | null
  clarity: string | null
  sedimentLevel: SedimentLevel | null
  testedBy: string | null
  testedOn: string | null
  remarks: string | null
  createdAt: string
}

export interface QualityNoteInput {
  colour: string | null
  colourScore: number | null
  aromaScore: number | null
  tasteScore: number | null
  clarity: string | null
  sedimentLevel: SedimentLevel | null
  testedBy: string | null
  testedOn: string | null
  remarks: string | null
}

/** Backend accepts only these extensions, keyed off the file name. */
export const ACCEPTED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'pdf', 'mp4', 'webm', 'mov'] as const

export const MAX_IMAGE_MB = 10
/** DocumentService allows 50 MB for video; the servlet caps the request at 55 MB. */
export const MAX_VIDEO_MB = 50

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

export async function fetchDocuments({
  type,
  q,
}: DocumentFilters): Promise<DocumentItem[]> {
  const { data } = await api.get<ApiResponse<DocumentItem[]>>('/documents', {
    params: { type: type || undefined, q: q.trim() || undefined },
  })
  return unwrap(data)
}

export async function fetchDocument(id: number): Promise<DocumentItem> {
  const { data } = await api.get<ApiResponse<DocumentItem>>(`/documents/${id}`)
  return unwrap(data)
}

/** Files attached to one batch, for the batch Quality/Documents panel. */
export async function fetchBatchDocuments(batchId: number): Promise<DocumentItem[]> {
  const { data } = await api.get<ApiResponse<DocumentItem[]>>(`/batches/${batchId}/documents`)
  return unwrap(data)
}

export async function uploadDocument(
  file: File,
  input: {
    type: DocumentType
    title: string
    batchId: number | null
    notes: string | null
  },
): Promise<DocumentItem> {
  const form = new FormData()
  form.append('file', file)
  form.append('type', input.type)
  form.append('title', input.title)
  // Append only when set: an empty string would fail @RequestParam Long binding.
  if (input.batchId !== null) form.append('batchId', String(input.batchId))
  if (input.notes) form.append('notes', input.notes)
  const { data } = await api.post<ApiResponse<DocumentItem>>('/documents', form)
  return unwrap(data)
}

export async function updateDocument(id: number, input: DocumentUpdateInput): Promise<DocumentItem> {
  const { data } = await api.put<ApiResponse<DocumentItem>>(`/documents/${id}`, input)
  return unwrap(data)
}

/** Owner-only server-side; the UI hides the action for staff. */
export async function deleteDocument(id: number): Promise<void> {
  await api.delete(`/documents/${id}`)
}

/**
 * Files sit behind the JWT filter, so they come back as a blob and are rendered
 * from an object URL instead of a plain <img src="/files/..."> that would 401.
 */
export function fetchFileBlob(id: number, download = false): Promise<Blob> {
  return fetchBlob(`/files/${id}`, download ? { download: 'true' } : undefined)
}

// ---- batch quality notes ----

export async function fetchQualityNotes(batchId: number): Promise<QualityNote[]> {
  const { data } = await api.get<ApiResponse<QualityNote[]>>(`/batches/${batchId}/quality-notes`)
  return unwrap(data)
}

export async function addQualityNote(
  batchId: number,
  input: QualityNoteInput,
): Promise<QualityNote> {
  const { data } = await api.post<ApiResponse<QualityNote>>(`/batches/${batchId}/quality-notes`, input)
  return unwrap(data)
}

export async function updateQualityNote(
  batchId: number,
  noteId: number,
  input: QualityNoteInput,
): Promise<QualityNote> {
  const { data } = await api.put<ApiResponse<QualityNote>>(
    `/batches/${batchId}/quality-notes/${noteId}`,
    input,
  )
  return unwrap(data)
}

export async function deleteQualityNote(batchId: number, noteId: number): Promise<void> {
  await api.delete(`/batches/${batchId}/quality-notes/${noteId}`)
}
