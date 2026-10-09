import { describe, expect, it } from 'vitest'
import { computeSaleTotals } from '@/utils/posTotals'

describe('computeSaleTotals', () => {
  it('sums line totals to the paise', () => {
    const t = computeSaleTotals([
      { qty: 2, rate: 132.5 },
      { qty: 1, rate: 85 },
    ])
    expect(t).toEqual({ itemCount: 3, subtotal: 350, discount: 0, total: 350 })
  })

  it('rounds every line before summing', () => {
    // 3 x 33.33 must be 99.99, not 99.99000000000001.
    const t = computeSaleTotals([{ qty: 3, rate: 33.33 }])
    expect(t.subtotal).toBe(99.99)
    expect(t.total).toBe(99.99)
  })

  it('applies a percent discount to the rounded subtotal', () => {
    const t = computeSaleTotals([{ qty: 2, rate: 100 }], { mode: 'percent', percent: 10 })
    expect(t.discount).toBe(20)
    expect(t.total).toBe(180)
  })

  it('applies a flat discount and cannot drive the total negative', () => {
    const t = computeSaleTotals([{ qty: 1, rate: 50 }], { mode: 'amount', amount: 80 })
    expect(t.discount).toBe(50)
    expect(t.total).toBe(0)
  })

  it('counts items and handles an empty bill', () => {
    expect(computeSaleTotals([])).toEqual({ itemCount: 0, subtotal: 0, discount: 0, total: 0 })
  })
})

