/** Shared scales/formatting for the hand-rolled SVG charts (no chart library in this app). */

/** Minimum horizontal room between two x-axis labels before we start dropping them. */
export const MIN_LABEL_SPACING = 56

/** Axis-friendly short form: 12.4k, 3.1L, 1.2Cr - Indian numbering, as used on the bills. */
export function compact(value: number): string {
  const abs = Math.abs(value)
  const sign = value < 0 ? '-' : ''
  if (abs >= 1e7) return `${sign}${+(abs / 1e7).toFixed(1)}Cr`
  if (abs >= 1e5) return `${sign}${+(abs / 1e5).toFixed(1)}L`
  if (abs >= 1e3) return `${sign}${+(abs / 1e3).toFixed(1)}k`
  return `${sign}${Math.round(abs)}`
}

/**
 * Round tick values covering [min, max] using 1/2/2.5/5/10 steps, so gridlines land on numbers a
 * shopkeeper can read (500, 1k, 2.5k) rather than on arbitrary maxima.
 */
export function niceTicks(min: number, max: number, target = 4): number[] {
  const span = max - min || 1
  const rough = span / target
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const step = ([1, 2, 2.5, 5, 10].find((m) => m * magnitude >= rough) ?? 10) * magnitude
  const ticks: number[] = []
  for (let v = Math.floor(min / step) * step; v <= max + step * 0.001; v += step) {
    ticks.push(Math.round(v * 1e6) / 1e6)
  }
  return ticks
}

/**
 * Smooth-ish line through points using midpoint quadratic curves. `values` must not be empty; a
 * single point renders as a flat line rather than NaN.
 */
export function smoothPath(xs: number[], ys: number[]): string {
  if (xs.length === 0) return ''
  if (xs.length === 1) return `M${xs[0]},${ys[0]} L${xs[0]},${ys[0]}`

  let d = `M${xs[0]},${ys[0]}`
  for (let i = 1; i < xs.length; i++) {
    const prevX = xs[i - 1]
    const prevY = ys[i - 1]
    const x = xs[i]
    const y = ys[i]
    const midX = (prevX + x) / 2
    d += ` Q${prevX},${prevY} ${midX},${(prevY + y) / 2}`
    d += ` Q${midX},${(prevY + y) / 2} ${x},${y}`
  }
  return d
}
