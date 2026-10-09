import axios from 'axios'
import { api } from '@/api/axios'
import { fetchBlob } from '@/api/download'
import type { ApiResponse } from '@/api/types'

export type PaymentMode = 'CASH' | 'UPI' | 'CARD' | 'BANK'
export const PAYMENT_MODES: PaymentMode[] = ['CASH', 'UPI', 'CARD', 'BANK']

export interface ExpenseCategory {
  id: number
  nameEn: string
  nameHi: string
  categoryType: string
  description: string | null
  colour: string | null
  active: boolean
  sortOrder: number
}

export interface ExpenseSummary {
  id: number
  categoryId: number
  categoryName: string
  categoryColour: string | null
  amount: number
  title: string
  expenseDate: string
  paymentMode: PaymentMode
  referenceNo: string | null
  hasBill: boolean
  createdAt: string
}

export interface ExpenseDetail {
  id: number
  categoryId: number
  categoryName: string
  amount: number
  title: string
  description: string | null
  billFileName: string | null
  billFileSize: number | null
  billFileType: string | null
  hasBill: boolean
  expenseDate: string
  paymentMode: PaymentMode
  referenceNo: string | null
  createdBy: number
  createdByName: string
  createdAt: string
}

export interface CategoryTotal {
  categoryId: number
  categoryName: string
  categoryColour: string | null
  amount: number
  count: number
}

export interface ExpenseFilters {
  dateFrom: string
  dateTo: string
  categoryId?: number | null
  paymentMode?: PaymentMode | ''
}

export interface ExpenseInput {
  categoryId: number
  amount: string
  title: string
  description: string
  expenseDate: string
  paymentMode: PaymentMode
  referenceNo: string
  billFile: File | null
  removeBill: boolean
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

export async function fetchExpenseCategories(): Promise<ExpenseCategory[]> {
  const { data } = await api.get<ApiResponse<ExpenseCategory[]>>('/expense-categories')
  return unwrap(data)
}

export async function fetchExpenses(filters: ExpenseFilters): Promise<ExpenseSummary[]> {
  const { data } = await api.get<ApiResponse<ExpenseSummary[]>>('/expenses', {
    params: {
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      categoryId: filters.categoryId ?? undefined,
      paymentMode: filters.paymentMode || undefined,
    },
  })
  return unwrap(data)
}

export async function fetchExpense(id: number): Promise<ExpenseDetail> {
  const { data } = await api.get<ApiResponse<ExpenseDetail>>(`/expenses/${id}`)
  return unwrap(data)
}

export async function fetchExpensesByCategory(
  dateFrom: string,
  dateTo: string,
): Promise<CategoryTotal[]> {
  const { data } = await api.get<ApiResponse<CategoryTotal[]>>('/expenses/by-category', {
    params: { dateFrom, dateTo },
  })
  return unwrap(data)
}

function toFormData(input: ExpenseInput): FormData {
  const form = new FormData()
  form.append('categoryId', String(input.categoryId))
  form.append('amount', input.amount)
  form.append('title', input.title.trim())
  form.append('expenseDate', input.expenseDate)
  form.append('paymentMode', input.paymentMode)
  if (input.description.trim()) form.append('description', input.description.trim())
  if (input.referenceNo.trim()) form.append('referenceNo', input.referenceNo.trim())
  if (input.billFile) form.append('billFile', input.billFile)
  else if (input.removeBill) form.append('removeBill', 'true')
  return form
}

export async function createExpense(input: ExpenseInput): Promise<ExpenseDetail> {
  const { data } = await api.post<ApiResponse<ExpenseDetail>>('/expenses', toFormData(input))
  return unwrap(data)
}

export async function updateExpense(id: number, input: ExpenseInput): Promise<ExpenseDetail> {
  const { data } = await api.put<ApiResponse<ExpenseDetail>>(`/expenses/${id}`, toFormData(input))
  return unwrap(data)
}

export async function deleteExpense(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`/expenses/${id}`)
  if (!data.success) throw new Error(data.message)
}

export function fetchExpenseBill(id: number): Promise<Blob> {
  return fetchBlob(`/expenses/${id}/bill`)
}

export function serverFieldErrors(error: unknown): Record<string, string> {
  if (!axios.isAxiosError(error)) return {}
  const errors = (error.response?.data as ApiResponse<unknown> | undefined)?.errors
  if (!errors) return {}
  return Object.fromEntries(
    Object.entries(errors).map(([field, messages]) => [field, messages.join(', ')]),
  )
}

