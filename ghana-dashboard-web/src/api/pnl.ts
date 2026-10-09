import { api } from '@/api/axios'
import { fetchBlob } from '@/api/download'
import type { ApiResponse } from '@/api/types'

export type PnlGroupBy = 'day' | 'month' | 'oilType'

export interface PnlGroup {
  groupKey: string
  groupLabel: string
  revenue: number
  cogs: number
  grossProfit: number
  expenses: number
  losses: number
  netProfit: number
}

export interface PnlSummary {
  fromDate: string
  toDate: string
  groupBy: PnlGroupBy
  totalRevenue: number
  totalCogs: number
  grossProfit: number
  totalExpenses: number
  totalLosses: number
  netProfit: number
  grossMarginPercent: number
  netMarginPercent: number
  groups: PnlGroup[]
}

export interface PnlQuery {
  from: string
  to: string
  groupBy: PnlGroupBy
}

export async function fetchPnl(query: PnlQuery): Promise<PnlSummary> {
  const { data } = await api.get<ApiResponse<PnlSummary>>('/pnl', { params: query })
  if (!data.success) throw new Error(data.message)
  return data.data
}

export function exportPnlCsv(query: PnlQuery): Promise<Blob> {
  return fetchBlob('/pnl/export.csv', { ...query })
}

export function exportPnlPdf(query: PnlQuery): Promise<Blob> {
  return fetchBlob('/pnl/report.pdf', { ...query })
}

