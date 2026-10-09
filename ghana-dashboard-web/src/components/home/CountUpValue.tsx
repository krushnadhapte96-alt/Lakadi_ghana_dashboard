import { useCountUp } from '@/hooks/useCountUp'
import { formatCurrency } from '@/utils/format'

/**
 * Animates a figure from its previous value to the new one.
 *
 * <p>Thin wrapper over the `useCountUp` hook so the dashboard cards all tick the same
 * way and stay readable for assistive tech: the interpolated number is exposed via
 * `aria-live="polite"`, and `prefers-reduced-motion` (handled in the hook) snaps
 * straight to the final figure instead of counting.
 */
export function CountUpValue({
  value,
  format,
  durationMs = 700,
}: {
  value: number
  format?: (value: number) => string
  durationMs?: number
}) {
  const animated = useCountUp(value, durationMs)
  return (
    <span aria-live="polite" className="tabular-nums">
      {format ? format(animated) : animated}
    </span>
  )
}

/** Count-up for a currency figure. Kept here so cards do not each re-implement rounding. */
export function CountUpCurrency({
  value,
  decimals,
  durationMs,
}: {
  value: number
  decimals?: number
  durationMs?: number
}) {
  const animated = useCountUp(value, durationMs)
  return (
    <span aria-live="polite" className="tabular-nums">
      {formatCurrency(animated, { decimals })}
    </span>
  )
}
