import { api } from '@/api/axios'
import type { ApiResponse } from '@/api/types'
import type { Page } from '@/api/inventory'

export type PaymentMode = 'CASH' | 'UPI' | 'CARD' | 'UDHAAR'
export type OrderStatus = 'NEW' | 'CONFIRMED' | 'PACKED' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'CANCELLED'
export type PaymentStatus = 'PAID' | 'PARTIAL' | 'UNPAID'

export const PAYMENT_MODES: PaymentMode[] = ['CASH', 'UPI', 'CARD', 'UDHAAR']
export const ORDER_STATUSES: OrderStatus[] = ['NEW', 'CONFIRMED', 'PACKED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED']
export const SALE_PAYMENT_STATUSES: PaymentStatus[] = ['PAID', 'PARTIAL', 'UNPAID']

export interface SaleSummary {
  id: number
  billNo: string
  customerName: string
  customerPhone: string | null
  totalAmount: number
  paymentMode: PaymentMode
  paymentStatus: PaymentStatus
  isVoid: boolean
  createdAt: string
}

export interface SaleResponse {
  id: number
  shopId: number
  billNo: string
  customerId: number | null
  customerName: string
  customerPhone: string | null
  userId: number
  userName: string
  subtotal: number
  discount: number
  discountType: string
  totalAmount: number
  paidAmount: number
  paymentMode: PaymentMode
  paymentStatus: PaymentStatus
  paymentReference: string | null
  isVoid: boolean
  voidReason: string | null
  voidedBy: number | null
  voidedByName: string | null
  voidedAt: string | null
  active: boolean
  createdAt: string
  items: SaleItem[]
}

export interface SaleItem {
  id: number
  saleId: number
  productId: number
  sku: string
  productName: string
  batchId: string | null
  qty: number
  unit: string
  rate: number
  amount: number
  costPrice: number
  profit: number
}

export interface SaleItemInput {
  productId: number
  qty: number
  rate: number
}

export interface CreateSaleInput {
  customerId: number | null
  customerPhone: string
  items: SaleItemInput[]
  discount: number
  discountType: 'PERCENT' | 'AMOUNT'
  paymentMode: PaymentMode
  paidAmount: number
  paymentReference: string
  notes: string
}

export interface OrderItem {
  id: number
  orderId: number
  productId: number
  sku: string
  productName: string
  qty: number
  unit: string
  rate: number
  amount: number
  costPrice: number
  profit: number
}

export interface OrderItemInput {
  productId: number
  qty: number
  rate: number
}

export interface OrderSummary {
  id: number
  billNo: string
  customerName: string
  customerPhone: string | null
  totalAmount: number
  paymentMode: PaymentMode
  paymentStatus: PaymentStatus
  status: OrderStatus
  expectedDeliveryDate: string | null
  createdAt: string
}

export interface OrderResponse extends OrderSummary {
  shopId: number
  customerId: number | null
  addressId: number | null
  addressSummary: string | null
  userId: number
  userName: string
  subtotal: number
  deliveryCharge: number
  discount: number
  discountType: string
  paidAmount: number
  paymentReference: string | null
  actualDeliveryDate: string | null
  notes: string | null
  cancelReason: string | null
  cancelledBy: number | null
  cancelledByName: string | null
  cancelledAt: string | null
  active: boolean
  items: OrderItem[]
}

export interface CreateOrderInput {
  customerId: number | null
  addressId: number | null
  items: OrderItemInput[]
  deliveryCharge: number
  discount: number
  discountType: 'PERCENT' | 'AMOUNT'
  paymentMode: PaymentMode
  paidAmount: number
  paymentReference: string
  expectedDeliveryDate: string | null
  notes: string
}

export interface OrderStatusCounts {
  NEW: number
  CONFIRMED: number
  PACKED: number
  OUT_FOR_DELIVERY: number
  DELIVERED: number
  CANCELLED: number
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

export interface SaleFilters {
  page: number
  size: number
  paymentStatus?: PaymentStatus | ''
  paymentMode?: string
}

export async function fetchSales(filters: SaleFilters): Promise<Page<SaleSummary>> {
  const { data } = await api.get<ApiResponse<Page<SaleSummary>>>('/sales', {
    params: {
      page: filters.page,
      size: filters.size,
      paymentStatus: filters.paymentStatus || undefined,
      paymentMode: filters.paymentMode || undefined,
    },
  })
  return unwrap(data)
}

export async function fetchSale(id: number): Promise<SaleResponse> {
  const { data } = await api.get<ApiResponse<SaleResponse>>(`/sales/${id}`)
  return unwrap(data)
}

export async function createSale(input: CreateSaleInput): Promise<SaleResponse> {
  const { data } = await api.post<ApiResponse<SaleResponse>>('/sales', input)
  return unwrap(data)
}

export async function updateSalePaymentStatus(
  id: number,
  input: { paymentStatus: PaymentStatus; paidAmount?: number; paymentReference?: string },
): Promise<SaleResponse> {
  const { data } = await api.patch<ApiResponse<SaleResponse>>(`/sales/${id}/payment-status`, input)
  return unwrap(data)
}

export async function voidSale(id: number, reason: string): Promise<SaleResponse> {
  const { data } = await api.post<ApiResponse<SaleResponse>>(`/sales/${id}/void`, { reason })
  return unwrap(data)
}

export interface OrderFilters {
  page: number
  size: number
  status?: OrderStatus | ''
}

export async function fetchOrders(filters: OrderFilters): Promise<Page<OrderSummary>> {
  const { data } = await api.get<ApiResponse<Page<OrderSummary>>>('/orders', {
    params: { page: filters.page, size: filters.size, status: filters.status || undefined },
  })
  return unwrap(data)
}

export async function fetchOrder(id: number): Promise<OrderResponse> {
  const { data } = await api.get<ApiResponse<OrderResponse>>(`/orders/${id}`)
  return unwrap(data)
}

export async function fetchOrderStatusCounts(): Promise<OrderStatusCounts> {
  const { data } = await api.get<ApiResponse<OrderStatusCounts>>('/orders/status-counts')
  return unwrap(data)
}

export async function createOrder(input: CreateOrderInput): Promise<OrderResponse> {
  const { data } = await api.post<ApiResponse<OrderResponse>>('/orders', input)
  return unwrap(data)
}

export async function updateOrderStatus(id: number, newStatus: OrderStatus, notes?: string): Promise<OrderResponse> {
  const { data } = await api.patch<ApiResponse<OrderResponse>>(`/orders/${id}/status`, {
    newStatus,
    notes: notes ?? '',
  })
  return unwrap(data)
}

export async function cancelOrder(id: number, reason: string): Promise<OrderResponse> {
  const { data } = await api.post<ApiResponse<OrderResponse>>(`/orders/${id}/cancel`, { reason })
  return unwrap(data)
}