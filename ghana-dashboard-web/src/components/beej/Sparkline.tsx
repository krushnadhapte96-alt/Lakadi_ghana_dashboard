const WIDTH = 160
const HEIGHT = 36
const PAD = 3

export function Sparkline({ values, className }: { values: number[]; className?: string }) {
  if (values.length < 2) {
    return <div style={{ height: HEIGHT }} className={className} aria-hidden="true" />
  }

  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  const step = (WIDTH - PAD * 2) / (values.length - 1)
  const y = (v: number) => PAD + (HEIGHT - PAD * 2) * (1 - (v - min) / span)
  const points = values.map((v, i) => `${PAD + i * step},${y(v)}`).join(' ')
  const last = values[values.length - 1]
  const rising = last >= values[0]

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      width="100%"
      height={HEIGHT}
      preserveAspectRatio="none"
      className={className}
      role="img"
      aria-label="Recent stock trend"
    >
      <polyline
        points={points}
        fill="none"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={rising ? 'stroke-gold-600 dark:stroke-gold-300' : 'stroke-crimson-500'}
      />
      <circle
        cx={PAD + (values.length - 1) * step}
        cy={y(last)}
        r={2.5}
        className={rising ? 'fill-gold-600 dark:fill-gold-300' : 'fill-crimson-500'}
      />
    </svg>
  )
}

