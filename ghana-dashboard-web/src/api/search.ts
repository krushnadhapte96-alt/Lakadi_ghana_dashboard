import { api } from '@/api/axios'
import type { ApiResponse } from '@/api/types'

export interface SearchItem {
  id: number
  title: string
  subtitle: string
  link: string
}

export interface SearchGroup {
  group: string
  items: SearchItem[]
  hasMore: boolean
}

export interface SearchResult {
  query: string
  groups: SearchGroup[]
  total: number
}

export async function searchAll(q: string): Promise<SearchResult> {
  const { data } = await api.get<ApiResponse<SearchResult>>('/search', { params: { q } })
  if (!data.success) throw new Error(data.message)
  return data.data
}

/** The section labels the palette renders, keyed by the group name the API returns. */
export const GROUP_LABELS: Record<string, string> = {
  BILL_NUMBER: 'Bill numbers',
  PRODUCTS: 'Products',
  SALES: 'Sales',
  ORDERS: 'Orders',
  CUSTOMERS: 'Customers',
  KHATA_PARTIES: 'Khata',
  BATCHES: 'Batches',
  SUPPLIERS: 'Suppliers',
}

