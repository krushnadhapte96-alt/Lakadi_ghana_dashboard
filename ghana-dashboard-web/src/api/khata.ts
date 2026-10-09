import { api } from '@/api/axios'
import { fetchBlob } from '@/api/download'
import type { ApiResponse } from '@/api/types'
import { PAYMENT_MODES, type PaymentMode } from '@/api/expenses'

export { PAYMENT_MODES }
export type { PaymentMode }

/** Mirrors KhataService.TYPES. */
export const PARTY_TYPES = ['RETAILER', 'HOTEL', 'RESTAURANT', 'INDIVIDUAL'] as const
export type PartyType = (typeof PARTY_TYPES)[number]

/** Mirrors the chk_khata_entry_type check constraint. */
export const ENTRY_TYPES = ['CREDIT_SALE', 'PAYMENT_RECEIVED', 'ADJUSTMENT'] as const
export type EntryType = (typeof ENTRY_TYPES)[number]

/** The synthetic first row the ledger uses to show the party's opening balance. */
export type LedgerEntryType = EntryType | 'OPENING'

export interface Party {
  id: number
  customerId: number | null
  name: string
  type: PartyType
  phone: string
  address: string | null
  creditLimit: number | null
  paymentTermsDays: number
  openingBalance: number
  active: boolean
  /** Positive means the party owes you. */
  balance: number
  /** The part of the balance whose due date has passed. */
  overdueAmount: number
  overdue: boolean
  overdueDays: number
  dueDate: string | null
  lastPaymentDate: string | null
  /** Credit limit minus balance, floored at zero. Null when no limit is set. */
  availableCredit: number | null
  createdAt: string
}

export interface PartyInput {
  name: string
  type: PartyType
  phone: string
  address: string
  creditLimit: number | null
  paymentTermsDays: number
  openingBalance: number
}

export interface CreditSaleItemInput {
  productId: number
  qty: number
  rate: number
}

export interface CreditSaleInput {
  items: CreditSaleItemInput[]
  dueDate: string | null
  note: string
  /** OWNER-only: proceed past the credit limit guard. */
  overrideCreditLimit: boolean
}

export interface PaymentInput {
  amount: number
  mode: PaymentMode
  date: string | null
  referenceNo: string
  note: string
}

export interface AdjustmentInput {
  /** Signed: negative forgives part of the balance. */
  amount: number
  note: string
}

export interface LedgerItem {
  productId: number | null
  productName: string
  qty: number
  unit: string | null
  rate: number
  amount: number
}

export interface LedgerEntry {
  id: number | null
  type: LedgerEntryType
  date: string
  dueDate: string | null
  amount: number
  /** Positive adds to what the party owes, negative reduces it. */
  delta: number
  runningBalance: number
  billNo: string | null
  sourceType: string | null
  sourceId: number | null
  paymentMode: string | null
  note: string | null
  items: LedgerItem[]
}

export interface Ledger {
  party: Party
  entries: LedgerEntry[]
}

export interface KhataSummary {
  totalOutstanding: number
  overdueAmount: number
  partiesWithBalance: number
  overdueParties: number
  collectedThisMonth: number
  creditSalesThisMonth: number
  topDebtors: Party[]
}

export interface Reminder {
  /** Ready-made wa.me deep link with the message already encoded. */
  url: string
  message: string
  language: 'en' | 'hi' | 'mr'
}

/** From InventoryDtos.ProductResponse; used to price credit-sale lines. */
export interface Product {
  id: number
  name: string
  sku: string
  unit: string
  sellingPrice: number
  active: boolean
}

export interface PartyFilters {
  type?: PartyType | ''
  overdue?: boolean
  withBalance?: boolean
  q?: string
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

export async function fetchKhataSummary(): Promise<KhataSummary> {
  const { data } = await api.get<ApiResponse<KhataSummary>>('/khata/summary')
  return unwrap(data)
}

export async function fetchParties(filters: PartyFilters = {}): Promise<Party[]> {
  const { data } = await api.get<ApiResponse<Party[]>>('/khata/parties', {
    params: {
      type: filters.type || undefined,
      overdue: filters.overdue || undefined,
      withBalance: filters.withBalance || undefined,
      q: filters.q?.trim() || undefined,
    },
  })
  return unwrap(data)
}

export async function fetchParty(id: number): Promise<Party> {
  const { data } = await api.get<ApiResponse<Party>>(`/khata/parties/${id}`)
  return unwrap(data)
}

export async function createParty(input: PartyInput): Promise<Party> {
  const { data } = await api.post<ApiResponse<Party>>('/khata/parties', {
    ...input,
    name: input.name.trim(),
    phone: input.phone.trim(),
    address: input.address.trim(),
  })
  return unwrap(data)
}

export async function updateParty(id: number, input: PartyInput): Promise<Party> {
  const { data } = await api.put<ApiResponse<Party>>(`/khata/parties/${id}`, {
    ...input,
    name: input.name.trim(),
    phone: input.phone.trim(),
    address: input.address.trim(),
  })
  return unwrap(data)
}

/** Owner-only, and only once the balance is settled. */
export async function deleteParty(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`/khata/parties/${id}`)
  if (!data.success) throw new Error(data.message)
}

export async function fetchLedger(id: number): Promise<Ledger> {
  const { data } = await api.get<ApiResponse<Ledger>>(`/khata/parties/${id}/ledger`)
  return unwrap(data)
}

/** Books a real UDHAAR sale: stock is deducted FIFO and the ledger gets a CREDIT_SALE entry. */
export async function recordCreditSale(id: number, input: CreditSaleInput): Promise<Ledger> {
  const { data } = await api.post<ApiResponse<Ledger>>(`/khata/parties/${id}/credit-sale`, {
    items: input.items,
    dueDate: input.dueDate ?? undefined,
    note: input.note.trim() || undefined,
    overrideCreditLimit: input.overrideCreditLimit || undefined,
  })
  return unwrap(data)
}

/** Records an IN payment in the cash book and reduces the balance. */
export async function recordPayment(id: number, input: PaymentInput): Promise<Ledger> {
  const { data } = await api.post<ApiResponse<Ledger>>(`/khata/parties/${id}/payment`, {
    amount: input.amount,
    mode: input.mode,
    date: input.date ?? undefined,
    referenceNo: input.referenceNo.trim() || undefined,
    note: input.note.trim() || undefined,
  })
  return unwrap(data)
}

/** Owner-only. */
export async function recordAdjustment(id: number, input: AdjustmentInput): Promise<Ledger> {
  const { data } = await api.post<ApiResponse<Ledger>>(`/khata/parties/${id}/adjustment`, {
    amount: input.amount,
    note: input.note.trim(),
  })
  return unwrap(data)
}

export function fetchStatementPdf(id: number): Promise<Blob> {
  return fetchBlob(`/khata/parties/${id}/statement.pdf`)
}

export async function fetchReminder(id: number, lang: 'en' | 'hi' | 'mr'): Promise<Reminder> {
  const { data } = await api.get<ApiResponse<Reminder>>(`/khata/parties/${id}/whatsapp-reminder`, {
    params: { lang },
  })
  return unwrap(data)
}

/** Needed to price credit-sale lines. */
export async function fetchProducts(): Promise<Product[]> {
  const { data } = await api.get<ApiResponse<Product[]>>('/products')
  return unwrap(data)
}
