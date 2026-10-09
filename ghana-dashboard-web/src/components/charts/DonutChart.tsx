import { useState } from 'react'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency } from '@/utils/format'

export interface DonutSlice {
  id: number
  label: string
  value: number
  color: string
}

const SIZE = 200
const RADIUS = 76
const STROKE = 26
const GAP = 2
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

export function DonutChart({
  slices,
  label,
}: {
  slices: DonutSlice[]
  label?: string
}) {
  const { t } = useLanguage()
  const [activeId, setActiveId] = useState<number | null>(null)
  const total = slices.reduce((sum, s) => sum + s.value, 0)
  const active = slices.find((s) => s.id === activeId) ?? null
  const ariaLabel = label ?? t('expenses.byCategory')

  const arcs = slices.map((slice, i) => ({
    slice,
    length: (slice.value / total) * CIRCUMFERENCE,
    offset: (slices.slice(0, i).reduce((sum, s) => sum + s.value, 0) / total) * CIRCUMFERENCE,
  }))

  return (
    <div className="flex flex-col items-center gap-5 sm:flex-row sm:items-center">
      <div className="relative shrink-0" style={{ width: SIZE, height: SIZE }}>
        <svg
          viewBox={`0 0 ${SIZE} ${SIZE}`}
          width={SIZE}
          height={SIZE}
          role="group"
          aria-label={ariaLabel}
          className="-rotate-90"
        >
          <circle
            cx={SIZE / 2}
            cy={SIZE / 2}
            r={RADIUS}
            fill="none"
            strokeWidth={STROKE}
            className="stroke-surface-3"
          />
          {arcs.map(({ slice, length, offset: start }) => (
            <circle
              key={slice.id}
              cx={SIZE / 2}
              cy={SIZE / 2}
              r={RADIUS}
              fill="none"
              stroke={slice.color}
              strokeWidth={activeId === slice.id ? STROKE + 4 : STROKE}
              strokeDasharray={`${Math.max(length - (slices.length > 1 ? GAP : 0), 0.5)} ${CIRCUMFERENCE}`}
              strokeDashoffset={-start}
              tabIndex={0}
              role="img"
              aria-label={`${slice.label}: ${formatCurrency(slice.value)}, ${Math.round((slice.value / total) * 100)}%`}
              className="cursor-pointer outline-none transition-[stroke-width] duration-150 focus-visible:opacity-80 motion-reduce:transition-none"
              onMouseEnter={() => setActiveId(slice.id)}
              onMouseLeave={() => setActiveId(null)}
              onFocus={() => setActiveId(slice.id)}
              onBlur={() => setActiveId(null)}
            />
          ))}
        </svg>
        <div
          className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center px-10 text-center"
          aria-live="polite"
        >
          <span className="line-clamp-1 text-xs text-ink-soft">
            {active ? active.label : t('charts.donutTotal')}
          </span>
          <span className="text-lg font-bold tabular-nums text-ink">
            {formatCurrency(active ? active.value : total)}
          </span>
          {active && (
            <span className="text-xs tabular-nums text-ink-soft">
              {Math.round((active.value / total) * 100)}%
            </span>
          )}
        </div>
      </div>

      <ul className="w-full min-w-0 flex-1 space-y-1">
        {slices.map((slice) => (
          <li key={slice.id}>
            <button
              type="button"
              onMouseEnter={() => setActiveId(slice.id)}
              onMouseLeave={() => setActiveId(null)}
              onFocus={() => setActiveId(slice.id)}
              onBlur={() => setActiveId(null)}
              className={cn(
                'focus-ring flex w-full items-center gap-2.5 rounded-card px-2 py-1.5 text-left text-sm transition-colors',
                activeId === slice.id ? 'bg-surface-3' : 'hover:bg-surface-3',
              )}
            >
              <span
                className="size-3 shrink-0 rounded-full"
                style={{ backgroundColor: slice.color }}
                aria-hidden="true"
              />
              <span className="min-w-0 flex-1 truncate text-ink">{slice.label}</span>
              <span className="tabular-nums text-ink-soft">
                {Math.round((slice.value / total) * 100)}%
              </span>
              <span className="w-24 text-right font-semibold tabular-nums text-ink">
                {formatCurrency(slice.value)}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

