import type { CustomerHistoryItem, CustomerListItem } from '@/api/customers'

/** Shared status variant map so sale and order rows read the same way. */
const HISTORY_VARIANT: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
  COMPLETED: 'success',
  DELIVERED: 'success',
  VOID: 'danger',
  CANCELLED: 'danger',
  REJECTED: 'danger',
  PENDING: 'warning',
  CONFIRMED: 'info',
  PROCESSING: 'info',
  PACKED: 'info',
  DISPATCHED: 'info',
}

export function historyVariant(item: CustomerHistoryItem) {
  if (item.status === 'VOID') return HISTORY_VARIANT.VOID
  const variant = HISTORY_VARIANT[item.status]
  if (variant) return variant
  const paid = item.paymentStatus
  if (paid === 'PAID') return 'success' as const
  if (paid === 'PARTIAL') return 'warning' as const
  if (paid === 'UNPAID') return 'danger' as const
  return 'default' as const
}

export function paidVariant(paymentStatus: string | null) {
  if (paymentStatus === 'PAID') return 'success' as const
  if (paymentStatus === 'PARTIAL') return 'warning' as const
  if (paymentStatus === 'UNPAID') return 'danger' as const
  return 'default' as const
}

/** Days until the predicted refill; negative when it is already due. */
export function refillDays(predictedRefillDate: string | null): number | null {
  if (!predictedRefillDate) return null
  const predicted = new Date(predictedRefillDate)
  if (Number.isNaN(predicted.getTime())) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  predicted.setHours(0, 0, 0, 0)
  return Math.round((predicted.getTime() - today.getTime()) / 86_400_000)
}

export type RefillState = 'due' | 'soon' | 'later' | 'unknown'

export function refillState(item: Pick<CustomerListItem, 'refillDue' | 'predictedRefillDate'>): RefillState {
  if (item.refillDue) return 'due'
  const days = refillDays(item.predictedRefillDate)
  if (days === null) return 'unknown'
  if (days <= 7) return 'soon'
  return 'later'
}

export function inactiveState(daysSinceLastPurchase: number | null): RefillState {
  if (daysSinceLastPurchase === null) return 'unknown'
  if (daysSinceLastPurchase >= 60) return 'due'
  if (daysSinceLastPurchase >= 30) return 'soon'
  return 'later'
}
