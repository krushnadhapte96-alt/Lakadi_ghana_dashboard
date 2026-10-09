/**
 * Hand-off channel from the home dashboard's quick actions into a module screen.
 *
 * <p>A quick action navigates to the module and asks it to open its "new" drawer via
 * `?action=<name>`. The module reads the param itself and opens the drawer on mount, so the intent
 * survives a page reload or a shared link - the same approach `posPrefill.ts` uses for the POS
 * customer, and for the same reason: one source of truth, no snapshot passed in memory.
 */

/** Query param carrying the requested entry point. */
export const ENTRY_PARAM = 'action'

/** Entry points the quick actions ask for. One per target drawer/form. */
export type EntryAction = 'sale' | 'order' | 'batch' | 'purchase' | 'credit' | 'expense'

const KNOWN: readonly string[] = [
  'sale',
  'order',
  'batch',
  'purchase',
  'credit',
  'expense',
]

export function entryHref(path: string, action: EntryAction): string {
  return `${path}?${ENTRY_PARAM}=${action}`
}

/** Returns the requested action, or null when absent or not one we recognise. */
export function readEntryAction(search: string | URLSearchParams): EntryAction | null {
  const params = typeof search === 'string' ? new URLSearchParams(search) : search
  const raw = params.get(ENTRY_PARAM)
  if (!raw) return null
  return KNOWN.includes(raw) ? (raw as EntryAction) : null
}

/**
 * Reads an allow-listed query param, for links that preselect a filter or tab (`?status=EXPIRED`).
 * Validating against the allow-list means a stale or hand-edited link falls back to the default
 * instead of leaving the screen showing nothing.
 */
export function readEnumParam<T extends string>(
  search: string | URLSearchParams,
  key: string,
  allowed: readonly T[],
): T | null {
  const params = typeof search === 'string' ? new URLSearchParams(search) : search
  const raw = params.get(key)
  if (!raw) return null
  return allowed.includes(raw as T) ? (raw as T) : null
}

/** A copy of `params` without `key`, so a consumed link stops steering the screen on reload. */
export function withoutParam(params: URLSearchParams, key: string): URLSearchParams {
  const next = new URLSearchParams(params)
  next.delete(key)
  return next
}
