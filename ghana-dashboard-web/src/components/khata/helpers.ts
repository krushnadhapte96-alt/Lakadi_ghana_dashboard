import type { LedgerEntryType, PartyType } from '@/api/khata'

type BadgeVariant =
  | 'default'
  | 'primary'
  | 'secondary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'clay'

export const TYPE_VARIANT: Record<PartyType, BadgeVariant> = {
  RETAILER: 'primary',
  HOTEL: 'info',
  RESTAURANT: 'info',
  INDIVIDUAL: 'secondary',
}

export const ENTRY_VARIANT: Record<LedgerEntryType, BadgeVariant> = {
  OPENING: 'default',
  CREDIT_SALE: 'clay',
  PAYMENT_RECEIVED: 'success',
  ADJUSTMENT: 'warning',
}

/** A positive delta means the party owes more. */
export function deltaClass(delta: number): string {
  if (delta > 0) return 'text-clay-600 dark:text-clay-400'
  if (delta < 0) return 'text-brand-600 dark:text-brand-400'
  return 'text-ink-soft'
}

/** A line with no product, a non-positive qty or a bad rate must never be sent. */
export function lineProblem(line: { qty: number; rate: number }): boolean {
  return !Number.isFinite(line.qty) || line.qty <= 0 || !Number.isFinite(line.rate) || line.rate < 0
}
