import { api } from '@/api/axios'
import type { ApiResponse } from '@/api/types'

export type BatchStatus = 'PRESSING' | 'SETTLING' | 'READY' | 'BOTTLED' | 'SOLD_OUT'

export interface BatchSummary {
  id: number
  code: string
  oilTypeName: string
  seedKg: number
  oilLitres: number
  khaliKg: number
  yieldPct: number
  status: BatchStatus
  daysSettling: number
  pressStart: string
  createdAt: string
}

export interface BatchDetail {
  id: number
  code: string
  oilTypeName: string
  seedTypeName: string
  seedKg: number
  oilLitres: number
  khaliKg: number
  yieldPct: number
  status: BatchStatus
  pressStart: string
  settlingStart: string | null
  readyDate: string | null
  mfgDate: string
  bestBeforeDate: string
  notes: string | null
  qualityNotes: QualityNote[]
  createdAt: string
  updatedAt: string
}

export interface QualityNote {
  id: number
  note: string
  checkedBy: string | null
  qualityLevel: string | null
  createdAt: string
}

export interface CreateBatchRequest {
  oilTypeId: number
  seedTypeId: number
  seedKg: number
  pressStart: string
}

export interface RecordOutputRequest {
  oilLitres: number
  khaliKg: number
}

export interface MarkReadyRequest {
  readyDate: string
}

export interface BottleItem {
  productId: number
  units: number
}

export interface BottleBatchRequest {
  items: BottleItem[]
  totalLitresUsed: number
}

export interface BottleItemResult {
  productId: number
  productName: string
  unitsBottled: number
  packagingRemaining: number
  success: boolean
  message: string
}

export interface BottleBatchResponse {
  batchId: number
  code: string
  results: BottleItemResult[]
  status: BatchStatus
  fullyBottled: boolean
}

export interface BatchFilter {
  status?: string
  oilTypeId?: number | null
  code?: string
  dateFrom?: string
  dateTo?: string
}

export interface LabelData {
  code: string
  oilTypeName: string
  mfgDate: string
  bestBeforeDate: string
  oilLitres: number
  batchId: number
}

export interface PackagingMapping {
  id: number
  productId: number
  productName: string
  packagingItemId: number
  packagingName: string
  packagingType: string
  packagingSize: string
  unitsPerPack: number
  active: boolean
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

// ---- Batch API ----

export async function fetchBatches(filter?: BatchFilter): Promise<BatchSummary[]> {
  const params = new URLSearchParams()
  if (filter?.status) params.set('status', filter.status)
  if (filter?.oilTypeId) params.set('oilTypeId', String(filter.oilTypeId))
  if (filter?.code) params.set('code', filter.code)
  if (filter?.dateFrom) params.set('dateFrom', filter.dateFrom)
  if (filter?.dateTo) params.set('dateTo', filter.dateTo)
  const { data } = await api.get<ApiResponse<BatchSummary[]>>(`/batches?${params}`)
  return unwrap(data)
}

export async function fetchBatch(id: number): Promise<BatchDetail> {
  const { data } = await api.get<ApiResponse<BatchDetail>>(`/batches/${id}`)
  return unwrap(data)
}

export async function createBatch(input: CreateBatchRequest): Promise<BatchDetail> {
  const { data } = await api.post<ApiResponse<BatchDetail>>('/batches', input)
  return unwrap(data)
}

export async function recordBatchOutput(
  id: number,
  input: RecordOutputRequest,
): Promise<BatchDetail> {
  const { data } = await api.post<ApiResponse<BatchDetail>>(`/batches/${id}/output`, input)
  return unwrap(data)
}

export async function markBatchReady(
  id: number,
  input: MarkReadyRequest,
): Promise<BatchDetail> {
  const { data } = await api.post<ApiResponse<BatchDetail>>(`/batches/${id}/ready`, input)
  return unwrap(data)
}

export async function bottleBatch(id: number, input: BottleBatchRequest): Promise<BottleBatchResponse> {
  const { data } = await api.post<ApiResponse<BottleBatchResponse>>(`/batches/${id}/bottle`, input)
  return unwrap(data)
}

export async function updateBatch(id: number, input: CreateBatchRequest): Promise<BatchDetail> {
  const { data } = await api.put<ApiResponse<BatchDetail>>(`/batches/${id}`, input)
  return unwrap(data)
}

export async function deleteBatch(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`/batches/${id}`)
  if (!data.success) throw new Error(data.message)
}

// Rich quality notes moved to @/api/documents, which posts the JSON body the
// backend's QualityNoteRequest now expects.

export async function fetchBatchLabel(id: number): Promise<LabelData> {
  const { data } = await api.get<ApiResponse<LabelData>>(`/batches/${id}/label`)
  return unwrap(data)
}

export async function fetchPackagingMappings(productId: number): Promise<PackagingMapping[]> {
  const { data } = await api.get<ApiResponse<PackagingMapping[]>>(`/products/${productId}/packaging`)
  return unwrap(data)
}

export async function fetchOilTypes(): Promise<OilType[]> {
  const { data } = await api.get<ApiResponse<OilType[]>>('/oil-types')
  return unwrap(data)
}

export async function fetchSeedTypes(): Promise<SeedType[]> {
  const { data } = await api.get<ApiResponse<SeedType[]>>('/seed-types')
  return unwrap(data)
}

export interface OilType {
  id: number
  nameEn: string
  nameHi: string
  density: number
  bestBeforeDays: number
  colourTag: string
  active: boolean
  createdAt: string | null
}

export interface SeedType {
  id: number
  oilTypeId: number | null
  oilTypeName: string | null
  nameEn: string
  nameHi: string
  active: boolean
}

