import { useState } from 'react'
import type { SalesPoint } from '@/api/dashboard'
import { useElementWidth } from '@/hooks/useElementWidth'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatQty } from '@/utils/format'
import { compact, MIN_LABEL_SPACING, niceTicks, smoothPath } from './chartUtils'

const HEIGHT = 240
const MARGIN = { top: 14, right: 14, bottom: 28, left: 52 }

/**
 * 30-day takings as a filled area.
 *
 * <p>Points arrive zero-filled from the backend so a quiet day still draws a point rather than
 * stretching the line across the gap. Hovering or focusing a column shows the exact figures, and the
 * whole series is repeated in a screen-reader table at the bottom - the same approach `TrendChart`
 * uses, so an area chart is never the only way to read the numbers.
 */
export function SalesAreaChart({ points }: { points: SalesPoint[] }) {
  const { t } = useLanguage()
  const { ref, width } = useElementWidth<HTMLDivElement>()
  const [activeIndex, setActiveIndex] = useState<number | null>(null)

  const plotW = Math.max(width - MARGIN.left - MARGIN.right, 0)
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom
  const amounts = points.map((p) => p.amount)
  const ticks = niceTicks(0, Math.max(0, ...amounts))
  const yMax = ticks[ticks.length - 1]
  const y = (v: number) => MARGIN.top + plotH - (v / (yMax || 1)) * plotH

  // One column per day; xs are midpoints so the first and last points sit inside the plot.
  const band = plotW / Math.max(points.length, 1)
  const cx = (i: number) => MARGIN.left + band * i + band / 2
  const labelEvery = Math.max(1, Math.ceil(MIN_LABEL_SPACING / Math.max(band, 1)))
  const active = activeIndex === null ? null : points[activeIndex]

  const xs = points.map((_, i) => cx(i))
  const ys = points.map((p) => y(p.amount))
  const line = smoothPath(xs, ys)
  const baseY = MARGIN.top + plotH
  const area = line ? `${line} L${xs[xs.length - 1]},${baseY} L${xs[0]},${baseY} Z` : ''

  const dayLabel = (point: SalesPoint) =>
    formatDate(point.date, 'en', { day: 'numeric', month: 'short' })

  return (
    <div>
      <div ref={ref} className="relative w-full" style={{ height: HEIGHT }}>
        {width > 0 && (
          <svg width={width} height={HEIGHT} role="group" aria-label={t('home.chart.salesTitle')}>
            <defs>
              <linearGradient id="sales-area-fill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--color-brand-500)" stopOpacity="0.35" />
                <stop offset="100%" stopColor="var(--color-brand-500)" stopOpacity="0.02" />
              </linearGradient>
            </defs>

            {ticks.map((tick) => (
              <g key={tick}>
                <line
                  x1={MARGIN.left}
                  x2={width - MARGIN.right}
                  y1={y(tick)}
                  y2={y(tick)}
                  className="stroke-line"
                  strokeWidth={1}
                />
                <text
                  x={MARGIN.left - 8}
                  y={y(tick)}
                  textAnchor="end"
                  dominantBaseline="middle"
                  className="fill-ink-soft text-[11px] tabular-nums"
                >
                  {compact(tick)}
                </text>
              </g>
            ))}

            {area && <path d={area} fill="url(#sales-area-fill)" />}
            {line && (
              <path
                d={line}
                fill="none"
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                className="stroke-brand-600 dark:stroke-brand-400"
              />
            )}

            {points.map((point, i) => (
              <g key={point.date}>
                {activeIndex === i && (
                  <circle
                    cx={cx(i)}
                    cy={y(point.amount)}
                    r={5}
                    strokeWidth={2}
                    className="fill-brand-600 stroke-surface-2 dark:fill-brand-400"
                  />
                )}
                {i % labelEvery === 0 && (
                  <text x={cx(i)} y={HEIGHT - 8} textAnchor="middle" className="fill-ink-soft text-[11px]">
                    {dayLabel(point)}
                  </text>
                )}
              </g>
            ))}

            {points.map((point, i) => (
              <rect
                key={`hit-${point.date}`}
                x={MARGIN.left + band * i}
                y={MARGIN.top}
                width={band}
                height={plotH}
                fill="transparent"
                tabIndex={0}
                role="img"
                aria-label={`${dayLabel(point)}: ${t('home.card.todaySales')} ${formatCurrency(point.amount)}, ${t('home.chart.bills')} ${point.bills}`}
                className="outline-none focus-visible:stroke-brand-500"
                strokeWidth={2}
                onMouseEnter={() => setActiveIndex(i)}
                onMouseLeave={() => setActiveIndex(null)}
                onFocus={() => setActiveIndex(i)}
                onBlur={() => setActiveIndex(null)}
              />
            ))}
          </svg>
        )}

        {active && activeIndex !== null && (
          <div
            className="pointer-events-none absolute z-10 w-48 rounded-card border border-line bg-surface-2 p-3 text-xs shadow-card"
            style={{
              top: MARGIN.top,
              left: Math.min(Math.max(cx(activeIndex) - 96, 0), Math.max(width - 192, 0)),
            }}
          >
            <p className="mb-1.5 font-semibold text-ink">{dayLabel(active)}</p>
            <p className="flex items-center justify-between gap-2 text-ink-soft">
              <span>{t('home.chart.salesTitle')}</span>
              <span className="font-semibold tabular-nums text-ink">{formatCurrency(active.amount)}</span>
            </p>
            <p className="flex items-center justify-between gap-2 text-ink-soft">
              <span>{t('home.chart.bills')}</span>
              <span className="font-semibold tabular-nums text-ink">{active.bills}</span>
            </p>
            <p className="flex items-center justify-between gap-2 text-ink-soft">
              <span>{t('home.card.litres')}</span>
              <span className="font-semibold tabular-nums text-ink">
                {formatQty(active.litres, 1)} L
              </span>
            </p>
          </div>
        )}
      </div>

      <table className="sr-only">
        <caption>{t('home.chart.salesTitle')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('common.date')}</th>
            <th scope="col">{t('home.chart.salesTitle')}</th>
            <th scope="col">{t('home.chart.bills')}</th>
          </tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={point.date}>
              <th scope="row">{dayLabel(point)}</th>
              <td>{formatCurrency(point.amount)}</td>
              <td>{point.bills}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Local alias so the date helper does not pull the whole i18n date signature into this file. */
function formatDate(value: string, _locale: 'en', options: Intl.DateTimeFormatOptions): string {
  return new Date(value).toLocaleDateString('en-IN', options)
}
