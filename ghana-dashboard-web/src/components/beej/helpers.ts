import type { BadgeProps } from '@/components/ui'
import type { Grade, PaymentStatus } from '@/api/beej'
import type { TranslationKey } from '@/i18n'

export function gradeKey(grade: Grade): TranslationKey {
  return `beej.grade.${grade}` as TranslationKey
}

export function statusKey(status: PaymentStatus): TranslationKey {
  return `beej.purchases.status.${status}` as TranslationKey
}

export function statusVariant(status: PaymentStatus): NonNullable<BadgeProps['variant']> {
  if (status === 'PAID') return 'success'
  if (status === 'PARTIAL') return 'warning'
  return 'danger'
}

