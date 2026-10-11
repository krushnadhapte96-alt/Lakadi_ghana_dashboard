import { api } from '@/api/axios'
import { fetchBlob } from '@/api/download'
import type { ApiResponse } from '@/api/types'

export type PaymentDirection = 'IN' | 'OUT'
export type PaymentSource = 'SALE' | 'ORDER' | 'KHATA' | 'KHALI_SALE' | 'SUPPLIER' | 'EXPENSE' | 'REFUND' | 'OTHER'
export type PaymentMode = 'CASH' | 'UPI' | 'CARD' | 'BANK'

export const PAYMENT_SOURCES: PaymentSource[] = [
  'SALE',
  'ORDER',
  'KHATA',
  'KHALI_SALE',
  'SUPPLIER',
  'EXPENSE',
  'REFUND',
  'OTHER',
]
export const PAYMENT_MODES: PaymentMode[] = ['CASH', 'UPI', 'CARD', 'BANK']

export interface Payment {
  id: number
  shopId: number
  direction: PaymentDirection
  mode: PaymentMode
  amount: number
  sourceType: PaymentSource
  sourceId: number | null
  referenceNo: string | null
  paymentDate: string
  note: string | null
  createdBy: number
  createdByName: string
  createdAt: string
}

export interface PaymentFilters {
  dateFrom: string
  dateTo: string
  direction?: PaymentDirection | ''
  sourceType?: PaymentSource | ''
}

export interface PaymentInput {
  direction: PaymentDirection
  mode: PaymentMode
  amount: number
  sourceType: PaymentSource
  sourceId: number | null
  referenceNo: string
  paymentDate: string
  note: string
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

export async function fetchPayments(filters: PaymentFilters): Promise<Payment[]> {
  const { data } = await api.get<ApiResponse<Payment[]>>('/payments', {
    params: {
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      direction: filters.direction || undefined,
      sourceType: filters.sourceType || undefined,
    },
  })
  return unwrap(data)
}

export async function createPayment(input: PaymentInput): Promise<Payment> {
  const { data } = await api.post<ApiResponse<Payment>>('/payments', input)
  return unwrap(data)
}

export function exportPaymentsCsv(filters: PaymentFilters): Promise<Blob> {
  return fetchBlob('/payments/export.csv', {
    dateFrom: filters.dateFrom,
    dateTo: filters.dateTo,
    direction: filters.direction || undefined,
    sourceType: filters.sourceType || undefined,
  })
}