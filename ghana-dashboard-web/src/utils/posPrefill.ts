/**
 * Hand-off channel between Customers and the POS.
 *
 * The POS page reads the id from the URL and resolves it itself, so there is one
 * source of truth for the customer record and no stale snapshot in session storage.
 */
export const POS_CUSTOMER_PARAM = 'customer'

export function saleHrefForCustomer(customerId: number): string {
  return `/sales?${POS_CUSTOMER_PARAM}=${customerId}`
}

/** Returns null for a missing, non-numeric or non-positive param. */
export function readPosCustomerId(search: string | URLSearchParams): number | null {
  const params = typeof search === 'string' ? new URLSearchParams(search) : search
  const raw = params.get(POS_CUSTOMER_PARAM)
  if (!raw) return null
  const id = Number(raw)
  return Number.isInteger(id) && id > 0 ? id : null
}
