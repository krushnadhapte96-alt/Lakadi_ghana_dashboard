import { describe, expect, it } from 'vitest'
import { formatCurrency, formatQty } from '@/utils/format'

describe('formatCurrency', () => {
  it('prefixes positive amounts with the rupee sign', () => {
    expect(formatCurrency(1234567.89)).toBe('Rs 12,34,567.89')
  })

  it('keeps whole amounts neat', () => {
    expect(formatCurrency(1500)).toBe('Rs 1,500')
  })

  it('renders negatives with the sign outside the currency', () => {
    expect(formatCurrency(-250.5)).toBe('-Rs 250.50')
  })

  it('supports a different currency', () => {
    expect(formatCurrency(100, { currency: 'Rs', decimals: 2 })).toBe('Rs 100.00')
  })
})

describe('formatQty', () => {
  it('rounds to two decimals by default', () => {
    expect(formatQty(12.3456)).toBe('12.35')
  })
})

