import type { ExpenseCategory } from '@/api/expenses'
import type { Language } from '@/i18n'

export const FALLBACK_COLOUR = '#A5A5A5'
export const MAX_BILL_BYTES = 10 * 1024 * 1024
export const BILL_ACCEPT = '.pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp'

const ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp']
const ALLOWED_EXTENSIONS = /\.(pdf|jpe?g|png|webp)$/i

export function categoryLabel(category: ExpenseCategory, language: Language): string {
  return language === 'hi' ? category.nameHi : category.nameEn
}

export function billFileError(file: File): 'fileType' | 'fileSize' | null {
  if (!ALLOWED_TYPES.includes(file.type) && !ALLOWED_EXTENSIONS.test(file.name)) return 'fileType'
  if (file.size > MAX_BILL_BYTES) return 'fileSize'
  return null
}

