/**
 * Sale arithmetic for the counter screens.
 *
 * <p>Every line is rounded to paise first, so "5 x Rs 33.33" is Rs 166.65 and never Rs 166.6499.
 * The discount is applied on the rounded subtotal, not the per-line amounts, which is the one most
 * likely source of a one-paise disagreement between the bill and the till.
 */
export interface SaleLine {
  qty: number
  rate: number
}

export type SaleDiscount = { mode: 'percent'; percent: number } | { mode: 'amount'; amount: number }

export interface SaleTotals {
  itemCount: number
  subtotal: number
  discount: number
  total: number
}

const roundTo2 = (value: number): number => Math.round(value * 100) / 100

export function computeSaleTotals(lines: SaleLine[], discount?: SaleDiscount): SaleTotals {
  const subtotal = roundTo2(lines.reduce((acc, line) => acc + roundTo2(line.qty * line.rate), 0))

  let discountValue = 0
  if (discount) {
    discountValue =
      discount.mode === 'percent'
        ? roundTo2((subtotal * discount.percent) / 100)
        : roundTo2(discount.amount)
  }
  discountValue = Math.min(Math.max(discountValue, 0), subtotal)

  return {
    itemCount: lines.reduce((acc, line) => acc + line.qty, 0),
    subtotal,
    discount: discountValue,
    total: roundTo2(subtotal - discountValue),
  }
}

