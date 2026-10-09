import { api } from '@/api/axios'
import type { ApiResponse } from '@/api/types'
import type { Reminder } from '@/api/khata'

export type { Reminder }

/** The four CustomerInsightService list modes. */
export const CUSTOMER_TABS = ['all', 'refill', 'top', 'inactive'] as const
export type CustomerTab = (typeof CUSTOMER_TABS)[number]

/** Matches chk_customer_address_type; only used to label the address cards. */
export const ADDRESS_TYPES = ['HOME', 'OFFICE', 'OTHER'] as const
export type AddressType = (typeof ADDRESS_TYPES)[number]

export interface CustomerListItem {
  id: number
  name: string
  phone: string | null
  tags: string[]
  preferredLanguage: 'en' | 'hi' | 'mr'
  totalOrders: number
  totalSpent: number
  lastPurchaseAt: string | null
  daysSinceLastPurchase: number | null
  /** last purchase + average gap; null until the customer has 2+ purchase days. */
  predictedRefillDate: string | null
  refillDue: boolean
  favouriteProductName: string | null
}

export interface CustomerNote {
  id: number
  note: string
  createdByName: string
  createdAt: string
}

export interface CustomerHistoryItem {
  type: 'SALE' | 'ORDER'
  id: number
  billNo: string
  date: string
  amount: number
  status: string
  paymentStatus: string | null
  itemsSummary: string | null
}

export interface CustomerAddress {
  id: number
  customerId: number
  addressType: AddressType
  addressLine1: string
  addressLine2: string | null
  city: string
  state: string
  pincode: string
  landmark: string | null
  isDefault: boolean
  active: boolean
  createdAt: string
}

export interface CustomerDetail {
  id: number
  name: string
  phone: string | null
  email: string | null
  gstNo: string | null
  tags: string[]
  preferredLanguage: 'en' | 'hi' | 'mr'
  active: boolean
  createdAt: string
  totalOrders: number
  totalSpent: number
  lastPurchaseAt: string | null
  daysSinceLastPurchase: number | null
  avgGapDays: number | null
  predictedRefillDate: string | null
  refillDue: boolean
  favouriteProductId: number | null
  favouriteProductName: string | null
  /** Outstanding on the linked khata party, when one exists. */
  khataBalance: number | null
  addresses: CustomerAddress[]
  notes: CustomerNote[]
  history: CustomerHistoryItem[]
}

export interface CustomerProfileInput {
  name: string
  phone: string
  email: string | null
  gstNo: string | null
  tags: string[]
  preferredLanguage: 'en' | 'hi' | 'mr'
}

export interface CustomerAddressInput {
  customerId: number
  addressType: AddressType
  addressLine1: string
  addressLine2: string | null
  city: string
  state: string
  pincode: string
  landmark: string | null
  isDefault: boolean
}

export interface CustomerFilters {
  tab: CustomerTab
  q: string
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

export async function fetchCustomers({ tab, q }: CustomerFilters): Promise<CustomerListItem[]> {
  const { data } = await api.get<ApiResponse<CustomerListItem[]>>('/customers', {
    params: { tab, q: q.trim() || undefined },
  })
  return unwrap(data)
}

/** Same list as the "refill" tab; kept because the prompt exposes it separately. */
export async function fetchRefillDue(): Promise<CustomerListItem[]> {
  const { data } = await api.get<ApiResponse<CustomerListItem[]>>('/customers/refill-due')
  return unwrap(data)
}

export async function fetchCustomer(id: number): Promise<CustomerDetail> {
  const { data } = await api.get<ApiResponse<CustomerDetail>>(`/customers/${id}`)
  return unwrap(data)
}

export async function updateCustomer(
  id: number,
  payload: CustomerProfileInput,
): Promise<CustomerDetail> {
  const { data } = await api.put<ApiResponse<CustomerDetail>>(`/customers/${id}`, payload)
  return unwrap(data)
}

export async function createCustomerAddress(
  customerId: number,
  payload: CustomerAddressInput,
): Promise<CustomerAddress> {
  const { data } = await api.post<ApiResponse<CustomerAddress>>(
    `/customers/${customerId}/addresses`,
    payload,
  )
  return unwrap(data)
}

/**
 * `lang` omitted so the backend falls back to the customer's preferred language,
 * which is the whole point of that column.
 */
export async function fetchWhatsappRefill(
  id: number,
  lang?: 'en' | 'hi' | 'mr',
): Promise<Reminder> {
  const { data } = await api.get<ApiResponse<Reminder>>(`/customers/${id}/whatsapp-refill`, {
    params: lang ? { lang } : undefined,
  })
  return unwrap(data)
}
