import axios from 'axios'
import { api } from '@/api/axios'
import { fetchBlob } from '@/api/download'
import type { ApiResponse } from '@/api/types'
import type { PaymentMode } from '@/api/expenses'

export type Grade = 'A' | 'B' | 'C'
export const GRADES: Grade[] = ['A', 'B', 'C']

export type PaymentStatus = 'PAID' | 'PARTIAL' | 'UNPAID'

export type StockMovementType = 'ADJUST' | 'WASTE'

export interface SeedType {
  id: number
  oilTypeId: number | null
  oilTypeName: string | null
  nameEn: string
  nameHi: string
  active: boolean
}

export interface RateHistoryPoint {
  purchaseId: number
  purchaseDate: string
  ratePerKg: number
  qtyKg: number
  supplierName: string
}

export interface Supplier {
  id: number
  name: string
  phone: string | null
  address: string | null
  gstin: string | null
  active: boolean
  totalPurchases: number
  totalPaid: number
  balance: number
  createdAt: string | null
}

export interface SupplierInput {
  name: string
  phone: string
  address: string
  gstin: string
}

export interface SeedStock {
  seedTypeId: number
  seedTypeName: string
  availableKg: number
  avgRate: number
  lowStockThreshold: number
  lowStock: boolean
  stockTrend: number[]
}

export interface SeedPurchaseDetail {
  id: number
  seedTypeId: number
  seedTypeName: string
  supplierId: number
  supplierName: string
  qtyKg: number
  ratePerKg: number
  totalAmount: number
  moisturePercent: number | null
  grade: Grade
  purchaseDate: string
  hasBill: boolean
  billFileName: string | null
  paymentStatus: PaymentStatus
  amountPaid: number
  createdBy: number
  createdByName: string
  createdAt: string | null
}

export interface SeedPurchaseSummary {
  id: number
  seedTypeId: number
  seedTypeName: string
  supplierId: number
  supplierName: string
  qtyKg: number
  ratePerKg: number
  totalAmount: number
  grade: Grade
  purchaseDate: string
  paymentStatus: PaymentStatus
  amountPaid: number
  hasBill: boolean
}

export interface SeedPurchaseCreateInput {
  seedTypeId: number
  supplierId: number
  qtyKg: string
  ratePerKg: string
  moisturePercent: string
  grade: Grade
  purchaseDate: string
  amountPaid: string
  paymentMode: PaymentMode
  billFile: File | null
}

export interface SeedPurchaseUpdateInput {
  supplierId: number
  moisturePercent: string
  grade: Grade
  purchaseDate: string
  amountPaid: string
  paymentMode: PaymentMode
  billFile: File | null
  removeBill: boolean
}

export interface SeedPurchaseFilters {
  dateFrom: string
  dateTo: string
  supplierId?: number | null
  seedTypeId?: number | null
  paymentStatus?: PaymentStatus | ''
}

export interface SupplierPaymentInput {
  amount: string
  mode: PaymentMode
  paymentDate: string
  referenceNo: string
  note: string
}

export interface SupplierPayment {
  id: number
  supplierId: number
  seedPurchaseId: number | null
  amount: number
  mode: PaymentMode
  paymentDate: string
  referenceNo: string | null
  note: string | null
  createdByName: string
  createdAt: string
}

export interface SupplierLedgerEntry {
  date: string
  type: 'PURCHASE' | 'PAYMENT'
  description: string
  debit: number
  credit: number
  runningBalance: number
}

export interface SupplierLedger {
  supplierId: number
  supplierName: string
  totalPurchases: number
  totalPaid: number
  balance: number
  entries: SupplierLedgerEntry[]
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

// ---- Seed Types ----

export async function fetchSeedTypes(): Promise<SeedType[]> {
  const { data } = await api.get<ApiResponse<SeedType[]>>('/seed-types')
  return unwrap(data)
}

export async function fetchRateHistory(seedTypeId: number): Promise<RateHistoryPoint[]> {
  const { data } = await api.get<ApiResponse<RateHistoryPoint[]>>(`/seed-types/${seedTypeId}/rate-history`)
  return unwrap(data)
}

// ---- Seed Stock ----

export async function fetchSeedStock(): Promise<SeedStock[]> {
  const { data } = await api.get<ApiResponse<SeedStock[]>>('/seed-stock')
  return unwrap(data)
}

export async function adjustSeedStock(
  seedTypeId: number,
  input: { qty: string; type: StockMovementType; reason: string; note: string },
): Promise<void> {
  const { data } = await api.post<ApiResponse<null>>(`/seed-stock/${seedTypeId}/adjust`, {
    qty: Number(input.qty),
    type: input.type,
    reason: input.reason.trim(),
    note: input.note.trim() || undefined,
  })
  if (!data.success) throw new Error(data.message)
}

// ---- Suppliers ----

export async function fetchSuppliers(): Promise<Supplier[]> {
  const { data } = await api.get<ApiResponse<Supplier[]>>('/suppliers')
  return unwrap(data)
}

export async function fetchSupplier(id: number): Promise<Supplier> {
  const { data } = await api.get<ApiResponse<Supplier>>(`/suppliers/${id}`)
  return unwrap(data)
}

function supplierPayload(input: SupplierInput) {
  return {
    name: input.name.trim(),
    phone: input.phone.trim() || undefined,
    address: input.address.trim() || undefined,
    gstin: input.gstin.trim() || undefined,
  }
}

export async function createSupplier(input: SupplierInput): Promise<Supplier> {
  const { data } = await api.post<ApiResponse<Supplier>>('/suppliers', supplierPayload(input))
  return unwrap(data)
}

export async function updateSupplier(id: number, input: SupplierInput): Promise<Supplier> {
  const { data } = await api.put<ApiResponse<Supplier>>(`/suppliers/${id}`, supplierPayload(input))
  return unwrap(data)
}

export async function deleteSupplier(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`/suppliers/${id}`)
  if (!data.success) throw new Error(data.message)
}

export async function recordSupplierPayment(id: number, input: SupplierPaymentInput): Promise<SupplierPayment> {
  const { data } = await api.post<ApiResponse<SupplierPayment>>(`/suppliers/${id}/payments`, {
    amount: Number(input.amount),
    mode: input.mode,
    paymentDate: input.paymentDate,
    referenceNo: input.referenceNo.trim() || undefined,
    note: input.note.trim() || undefined,
  })
  return unwrap(data)
}

export async function fetchSupplierLedger(id: number): Promise<SupplierLedger> {
  const { data } = await api.get<ApiResponse<SupplierLedger>>(`/suppliers/${id}/ledger`)
  return unwrap(data)
}

// ---- Seed Purchases ----

export async function fetchSeedPurchases(filters: SeedPurchaseFilters): Promise<SeedPurchaseSummary[]> {
  const { data } = await api.get<ApiResponse<SeedPurchaseSummary[]>>('/seed-purchases', {
    params: {
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      supplierId: filters.supplierId ?? undefined,
      seedTypeId: filters.seedTypeId ?? undefined,
      paymentStatus: filters.paymentStatus || undefined,
    },
  })
  return unwrap(data)
}

export async function fetchSeedPurchase(id: number): Promise<SeedPurchaseDetail> {
  const { data } = await api.get<ApiResponse<SeedPurchaseDetail>>(`/seed-purchases/${id}`)
  return unwrap(data)
}

export async function createSeedPurchase(input: SeedPurchaseCreateInput): Promise<SeedPurchaseDetail> {
  const form = new FormData()
  form.append('seedTypeId', String(input.seedTypeId))
  form.append('supplierId', String(input.supplierId))
  form.append('qtyKg', input.qtyKg)
  form.append('ratePerKg', input.ratePerKg)
  form.append('grade', input.grade)
  form.append('purchaseDate', input.purchaseDate)
  form.append('amountPaid', input.amountPaid || '0')
  form.append('paymentMode', input.paymentMode)
  if (input.moisturePercent.trim()) form.append('moisturePercent', input.moisturePercent)
  if (input.billFile) form.append('billFile', input.billFile)
  const { data } = await api.post<ApiResponse<SeedPurchaseDetail>>('/seed-purchases', form)
  return unwrap(data)
}

export async function updateSeedPurchase(id: number, input: SeedPurchaseUpdateInput): Promise<SeedPurchaseDetail> {
  const form = new FormData()
  form.append('supplierId', String(input.supplierId))
  form.append('grade', input.grade)
  form.append('purchaseDate', input.purchaseDate)
  form.append('amountPaid', input.amountPaid || '0')
  form.append('paymentMode', input.paymentMode)
  if (input.moisturePercent.trim()) form.append('moisturePercent', input.moisturePercent)
  if (input.billFile) form.append('billFile', input.billFile)
  else if (input.removeBill) form.append('removeBill', 'true')
  const { data } = await api.put<ApiResponse<SeedPurchaseDetail>>(`/seed-purchases/${id}`, form)
  return unwrap(data)
}

export async function deleteSeedPurchase(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`/seed-purchases/${id}`)
  if (!data.success) throw new Error(data.message)
}

export function fetchSeedPurchaseBill(id: number): Promise<Blob> {
  return fetchBlob(`/seed-purchases/${id}/bill`)
}

export function serverFieldErrors(error: unknown): Record<string, string> {
  if (!axios.isAxiosError(error)) return {}
  const errors = (error.response?.data as ApiResponse<unknown> | undefined)?.errors
  if (!errors) return {}
  return Object.fromEntries(Object.entries(errors).map(([field, messages]) => [field, messages.join(', ')]))
}

