import { useState } from 'react'

/**
 * Form state that follows a loaded record.
 *
 * Settings tabs all edit one server-owned object: seed the fields when the record
 * arrives (or changes), and leave them alone afterwards so typing is not clobbered.
 *
 * React documents setState-during-render as the way to adjust state when an input
 * changes - doing it in an effect instead would paint the stale form for a frame and
 * trigger a second render. This is the same shape `LicenceFormSheet` uses.
 *
 * `commit` lets the caller record an edit locally so the form does not snap back to
 * the previous server value while the refetch is in flight.
 */
export function useSyncedForm<T>(source: T | undefined, fallback: T) {
  const [form, setForm] = useState<T>(source ?? fallback)
  const [seed, setSeed] = useState<T | undefined>(source)
  // The last value known-good on the server, so `dirty` survives a refetch.
  const [committed, setCommitted] = useState<T>(source ?? fallback)

  if (source !== undefined && source !== seed) {
    setSeed(source)
    setForm(source)
    setCommitted(source)
  }

  const reset = (value?: T) => {
    const next = value ?? source ?? fallback
    setForm(next)
    setCommitted(next)
  }

  /** Records a successful save so `dirty` compares against what was just written. */
  const commit = (value: T) => {
    setForm(value)
    setCommitted(value)
  }

  const dirty = JSON.stringify(form) !== JSON.stringify(committed)

  return { form, setForm, dirty, reset, commit }
}
