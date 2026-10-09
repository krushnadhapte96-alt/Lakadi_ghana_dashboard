import { api } from '@/api/axios'
import { fetchBlob } from '@/api/download'
import type { ApiResponse } from '@/api/types'
import { PAYMENT_MODES, type PaymentMode } from '@/api/expenses'

export const RETURN_TYPES = [
  'LEAKAGE',
  'BROKEN',
  'TASTE_SMELL',
  'WRONG_ITEM',
  'QTY_SHORT',
  'OTHER',
] as const
export type ReturnType = (typeof RETURN_TYPES)[number]

export const RETURN_STATUSES = ['OPEN', 'IN_REVIEW', 'RESOLVED'] as const
export type ReturnStatus = (typeof RETURN_STATUSES)[number]

export const RETURN_RESOLUTIONS = ['REFUND', 'REPLACE', 'REJECT'] as const
export type ReturnResolution = (typeof RETURN_RESOLUTIONS)[number]

export const RETURN_ITEM_ACTIONS = ['RESTOCK', 'WRITE_OFF'] as const
export type ReturnItemAction = (typeof RETURN_ITEM_ACTIONS)[number]

export const RETURN_SOURCE_TYPES = ['SALE', 'ORDER'] as const
export type ReturnSourceType = (typeof RETURN_SOURCE_TYPES)[number]

export { PAYMENT_MODES }
export type { PaymentMode }

/** Mirrors ReturnService.MAX_PHOTOS_PER_RETURN. */
export const MAX_PHOTOS = 6

export interface ReturnLookupItem {
  productId: number
  productName: string
  sku: string
  /** Quantity still returnable - the bill qty minus anything already claimed. */
  qty: number
  unit: string
  rate: number
  batchId: string | null
}

export interface ReturnLookup {
  sourceType: ReturnSourceType
  sourceId: number
  billNo: string
  customerId: number | null
  customerName: string | null
  items: ReturnLookupItem[]
}

export interface ReturnItem {
  id: number
  productId: number
  productName: string
  sku: string
  batchId: string | null
  qty: number
  unitPrice: number
  action: ReturnItemAction | null
}

export interface ReturnPhoto {
  id: number
  fileName: string
  fileSize: number
  fileType: string
}

export interface ReturnSummary {
  id: number
  returnNo: string
  sourceType: ReturnSourceType
  sourceId: number
  sourceBillNo: string
  customerId: number | null
  customerName: string | null
  type: ReturnType
  status: ReturnStatus
  resolution: ReturnResolution | null
  itemCount: number
  totalValue: number
  photoIds: number[]
  createdAt: string
}

export interface ReturnDetail {
  id: number
  returnNo: string
  sourceType: ReturnSourceType
  sourceId: number
  sourceBillNo: string
  customerId: number | null
  customerName: string | null
  type: ReturnType
  status: ReturnStatus
  resolution: ReturnResolution | null
  description: string | null
  refundAmount: number | null
  replacementSaleId: number | null
  items: ReturnItem[]
  photos: ReturnPhoto[]
  reportedByName: string
  resolvedByName: string | null
  resolvedAt: string | null
  createdAt: string
}

export interface ReturnFilters {
  status?: ReturnStatus | ''
  type?: ReturnType | ''
  dateFrom?: string
  dateTo?: string
}

export interface ReturnCreateInput {
  sourceType: ReturnSourceType
  sourceId: number
  type: ReturnType
  description: string
  items: { productId: number; qty: number }[]
}

export interface ReturnResolveInput {
  resolution: ReturnResolution
  items?: { returnItemId: number; action: ReturnItemAction }[]
  refundAmount?: number | null
  paymentMode?: PaymentMode
  note?: string
}

export interface ReturnTypeStat {
  type: ReturnType
  count: number
  totalValue: number
}

export interface ReturnBatchStat {
  batchId: string
  productName: string
  count: number
  totalValue: number
}

export interface ReturnStats {
  byType: ReturnTypeStat[]
  byBatch: ReturnBatchStat[]
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

/** Bills starting with "O" resolve to an order, everything else to a sale. */
export async function lookupReturn(billNo: string): Promise<ReturnLookup> {
  const { data } = await api.get<ApiResponse<ReturnLookup>>('/returns/lookup', {
    params: { billNo: billNo.trim() },
  })
  return unwrap(data)
}

export async function fetchReturns(filters: ReturnFilters): Promise<ReturnSummary[]> {
  const { data } = await api.get<ApiResponse<ReturnSummary[]>>('/returns', {
    params: {
      status: filters.status || undefined,
      type: filters.type || undefined,
      dateFrom: filters.dateFrom || undefined,
      dateTo: filters.dateTo || undefined,
    },
  })
  return unwrap(data)
}

export async function fetchReturn(id: number): Promise<ReturnDetail> {
  const { data } = await api.get<ApiResponse<ReturnDetail>>(`/returns/${id}`)
  return unwrap(data)
}

export async function fetchReturnStats(dateFrom?: string, dateTo?: string): Promise<ReturnStats> {
  const { data } = await api.get<ApiResponse<ReturnStats>>('/returns/stats', {
    params: { dateFrom: dateFrom || undefined, dateTo: dateTo || undefined },
  })
  return unwrap(data)
}

export async function createReturn(input: ReturnCreateInput): Promise<ReturnDetail> {
  const { data } = await api.post<ApiResponse<ReturnDetail>>('/returns', {
    sourceType: input.sourceType,
    sourceId: input.sourceId,
    type: input.type,
    description: input.description.trim() || undefined,
    items: input.items,
  })
  return unwrap(data)
}

export async function reviewReturn(id: number, note?: string): Promise<ReturnDetail> {
  const { data } = await api.post<ApiResponse<ReturnDetail>>(`/returns/${id}/review`, {
    note: note?.trim() || undefined,
  })
  return unwrap(data)
}

/** Owner-only: pays money out and moves stock. */
export async function resolveReturn(id: number, input: ReturnResolveInput): Promise<ReturnDetail> {
  const { data } = await api.post<ApiResponse<ReturnDetail>>(`/returns/${id}/resolve`, {
    resolution: input.resolution,
    items: input.items,
    refundAmount: input.refundAmount ?? undefined,
    paymentMode: input.paymentMode,
    note: input.note?.trim() || undefined,
  })
  return unwrap(data)
}

export async function uploadReturnPhotos(id: number, files: File[]): Promise<ReturnPhoto[]> {
  const form = new FormData()
  for (const file of files) form.append('files', file)
  const { data } = await api.post<ApiResponse<ReturnPhoto[]>>(`/returns/${id}/photos`, form)
  return unwrap(data)
}

/**
 * Photos are behind the JWT filter, so they come back as a blob and are shown
 * from an object URL rather than a plain <img src>.
 */
export function fetchReturnPhoto(returnId: number, photoId: number): Promise<Blob> {
  return fetchBlob(`/returns/${returnId}/photos/${photoId}`)
}

/**
 * Object URLs are cached per photo so a card thumbnail and the detail drawer
 * share one download, and one place to revoke.
 */
const photoUrls = new Map<string, Promise<string>>()

export function returnPhotoUrl(returnId: number, photoId: number): Promise<string> {
  const key = `${returnId}:${photoId}`
  const existing = photoUrls.get(key)
  if (existing) return existing
  const pending = fetchReturnPhoto(returnId, photoId).then(
    (blob) => URL.createObjectURL(blob),
    (error: unknown) => {
      photoUrls.delete(key)
      throw error
    },
  )
  photoUrls.set(key, pending)
  return pending
}

export function evictReturnPhoto(returnId: number, photoId: number): void {
  const key = `${returnId}:${photoId}`
  const pending = photoUrls.get(key)
  if (!pending) return
  photoUrls.delete(key)
  void pending.then((url) => URL.revokeObjectURL(url), () => undefined)
}

export function evictAllReturnPhotos(returnId: number): void {
  for (const key of [...photoUrls.keys()]) {
    if (key.startsWith(`${returnId}:`)) {
      const photoId = Number(key.split(':')[1])
      evictReturnPhoto(returnId, photoId)
    }
  }
}

