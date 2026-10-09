import { api } from '@/api/axios'
import type { ApiResponse } from '@/api/types'

export interface TodaySales {
  amount: number
  litres: number
  bills: number
}

export interface LowStockItem {
  productId: number
  name: string
  sku: string
  available: number
  threshold: number
}

export interface LowStock {
  count: number
  top: LowStockItem[]
}

export interface SeedStock {
  totalKg: number
  seedTypes: number
  lowCount: number
}

export interface ExpiringBatch {
  batchStockId: number
  productName: string
  batchId: string
  qtyAvailable: number
  expiryDate: string
  daysLeft: number
}

export interface ExpiringSoon {
  count: number
  nearestExpiry: string | null
  qtyAtRisk: number
  top: ExpiringBatch[]
}

export interface MoneyToCollect {
  amount: number
  unpaidBills: number
  parties: number
}

export interface LicenceAlert {
  licenceId: number
  number: string
  type: string
  expiryDate: string
  daysLeft: number
  status: string
}

export interface LicenceAlerts {
  expiringSoon: number
  expired: number
  next: LicenceAlert | null
}

export interface RefillCustomer {
  customerId: number
  name: string
  phone: string
  predictedDate: string
  daysUntil: number
}

export interface RefillDue {
  count: number
  top: RefillCustomer[]
}

export interface SalesPoint {
  date: string
  amount: number
  bills: number
  litres: number
}

export interface OilMixSlice {
  oilTypeId: number | null
  label: string
  colourTag: string
  amount: number
}

export interface DashboardSummary {
  date: string
  shopName: string | null
  todaySales: TodaySales
  pendingOrders: number
  lowStock: LowStock
  seedStock: SeedStock
  expiringSoon: ExpiringSoon
  /** Owner only - null for a staff account. */
  monthNetProfit: number | null
  /** Owner only - null for a staff account. */
  monthMarginPercent: number | null
  moneyToCollect: MoneyToCollect
  licences: LicenceAlerts
  refillDue: RefillDue
  salesSeries: SalesPoint[]
  oilMix: OilMixSlice[]
  generatedAt: string
}

export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  const { data } = await api.get<ApiResponse<DashboardSummary>>('/dashboard/summary')
  if (!data.success) throw new Error(data.message)
  return data.data
}
