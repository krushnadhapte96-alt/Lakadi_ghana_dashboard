export function formatCurrency(
  amount: number,
  options?: { decimals?: number; currency?: string },
): string {
  const decimals = options?.decimals ?? (Number.isInteger(amount) ? 0 : 2)
  const currency = options?.currency ?? 'Rs'
  const formatted = Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
  return `${amount < 0 ? '-' : ''}${currency} ${formatted}`
}

export function formatQty(value: number, decimals = 2): string {
  return value.toLocaleString('en-IN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimals,
  })
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function formatDate(
  value: Date | string | number,
  locale: 'en' | 'hi' | 'mr' = 'en',
  options?: Intl.DateTimeFormatOptions,
): string {
  const date = value instanceof Date ? value : new Date(value)
  return date.toLocaleDateString(locale === 'hi' ? 'hi-IN' : locale === 'mr' ? 'mr-IN' : 'en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    ...options,
  })
}
