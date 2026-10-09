import { api } from '@/api/axios'
import type { ApiResponse } from '@/api/types'

export type ExpiryBucket = 'EXPIRED' | 'D7' | 'D15' | 'D30'

export interface ExpiryAlertItem {
  batchStockId: number
  batchId: string
  productId: number
  productName: string
  sku: string
  qtyAvailable: number
  unit: string
  costPrice: number
  valueAtRisk: number
  expiryDate: string
  daysLeft: number
  bucket: ExpiryBucket
  activeDiscountPct: number | null
}

export interface ExpiryBucketSummary {
  bucket: ExpiryBucket
  label: string
  totalUnits: number
  totalValue: number
  items: ExpiryAlertItem[]
}

export interface ExpiryAlertsSummary {
  totalUnitsAtRisk: number
  totalValueAtRisk: number
  buckets: ExpiryBucketSummary[]
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

export async function fetchExpiryAlerts(): Promise<ExpiryAlertsSummary> {
  const { data } = await api.get<ApiResponse<ExpiryAlertsSummary>>('/expiry-alerts')
  return unwrap(data)
}

export async function giveExpiryDiscount(
  batchStockId: number,
  discountPct: number,
  reason: string,
): Promise<ExpiryAlertItem> {
  const { data } = await api.post<ApiResponse<ExpiryAlertItem>>(
    `/expiry-alerts/${batchStockId}/discount`,
    { discountPct, reason: reason || undefined },
  )
  return unwrap(data)
}

export async function writeOffExpiry(
  batchStockId: number,
  qty: number,
  note: string,
): Promise<ExpiryAlertItem> {
  const { data } = await api.post<ApiResponse<ExpiryAlertItem>>(
    `/expiry-alerts/${batchStockId}/write-off`,
    { qty, note: note || undefined },
  )
  return unwrap(data)
}

export async function snoozeExpiry(batchStockId: number, days: number): Promise<ExpiryAlertItem> {
  const { data } = await api.post<ApiResponse<ExpiryAlertItem>>(
    `/expiry-alerts/${batchStockId}/snooze`,
    { days },
  )
  return unwrap(data)
}

