import { api } from '@/api/axios'
import type { ApiResponse } from '@/api/types'

export type OverheadMode = 'PER_BATCH' | 'PER_KG'

export interface CostDefaults {
  electricityMode: OverheadMode
  electricityValue: number
  labourMode: OverheadMode
  labourValue: number
  otherMode: OverheadMode
  otherValue: number
  khaliValuePerKg: number
  targetMarginPct: number
  updatedAt: string
}

export interface CostDefaultsInput {
  electricityMode: OverheadMode
  electricityValue: number
  labourMode: OverheadMode
  labourValue: number
  otherMode: OverheadMode
  otherValue: number
  khaliValuePerKg: number
  targetMarginPct: number
}

export interface BatchCost {
  batchId: number
  batchCode: string
  seedKg: number
  oilLitres: number
  khaliKg: number
  seedCost: number
  electricityCost: number
  labourCost: number
  otherCost: number
  khaliCredit: number
  totalCost: number
  costPerLitre: number
  manualOverride: boolean
  computedAt: string
}

export interface BatchCostUpdateInput {
  electricityCost?: number
  labourCost?: number
  otherCost?: number
  khaliCredit?: number
}

export interface CostingProduct {
  productId: number
  productName: string
  sku: string
  isKhali: boolean
  unitCost: number
  sellingPrice: number
  marginPct: number
  targetMarginPct: number
  suggestedPrice: number
  belowTarget: boolean
  sourceBatchId: number | null
  sourceBatchCode: string | null
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

export async function fetchCostDefaults(): Promise<CostDefaults> {
  const { data } = await api.get<ApiResponse<CostDefaults>>('/cost-defaults')
  return unwrap(data)
}

export async function updateCostDefaults(input: CostDefaultsInput): Promise<CostDefaults> {
  const { data } = await api.put<ApiResponse<CostDefaults>>('/cost-defaults', input)
  return unwrap(data)
}

export async function fetchBatchCost(batchId: number): Promise<BatchCost> {
  const { data } = await api.get<ApiResponse<BatchCost>>(`/batches/${batchId}/cost`)
  return unwrap(data)
}

export async function updateBatchCost(batchId: number, input: BatchCostUpdateInput): Promise<BatchCost> {
  const { data } = await api.put<ApiResponse<BatchCost>>(`/batches/${batchId}/cost`, input)
  return unwrap(data)
}

export async function recalculateBatchCost(batchId: number): Promise<BatchCost> {
  const { data } = await api.post<ApiResponse<BatchCost>>(`/batches/${batchId}/cost/recalculate`)
  return unwrap(data)
}

export async function fetchCostingProducts(): Promise<CostingProduct[]> {
  const { data } = await api.get<ApiResponse<CostingProduct[]>>('/costing/products')
  return unwrap(data)
}

export async function applySuggestedPrice(productId: number): Promise<{ productId: number; sellingPrice: number }> {
  const { data } = await api.post<ApiResponse<{ productId: number; sellingPrice: number }>>(
    `/products/${productId}/apply-suggested-price`,
  )
  return unwrap(data)
}

