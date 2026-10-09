import { useState } from 'react'
import { useElementWidth } from '@/hooks/useElementWidth'
import { useLanguage } from '@/i18n'
import type { PnlGroup } from '@/api/pnl'
import { formatCurrency } from '@/utils/format'
import { compact, MIN_LABEL_SPACING, niceTicks } from './chartUtils'

const HEIGHT = 260
const MARGIN = { top: 12, right: 12, bottom: 30, left: 52 }

function bucketLabel(group: PnlGroup): string {
  const parts = group.groupKey.split('-').map(Number)
  if (parts.length === 3 && parts.every((n) => !Number.isNaN(n))) {
    return new Date(parts[0], parts[1] - 1, parts[2]).toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
    })
  }
  if (parts.length === 2 && parts.every((n) => !Number.isNaN(n))) {
    return new Date(parts[0], parts[1] - 1, 1).toLocaleDateString('en-IN', {
      month: 'short',
      year: '2-digit',
    })
  }
  return group.groupLabel
}

function roundedTopBar(x: number, y: number, w: number, h: number, r: number): string {
  const rr = Math.min(r, w / 2, h)
  return `M${x},${y + h} V${y + rr} Q${x},${y} ${x + rr},${y} H${x + w - rr} Q${x + w},${y} ${x + w},${y + rr} V${y + h} Z`
}

export function TrendChart({ groups }: { groups: PnlGroup[] }) {
  const { t } = useLanguage()
  const { ref, width } = useElementWidth<HTMLDivElement>()
  const [activeIndex, setActiveIndex] = useState<number | null>(null)

  const plotW = Math.max(width - MARGIN.left - MARGIN.right, 0)
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom
  const values = groups.flatMap((g) => [g.revenue, g.netProfit])
  const ticks = niceTicks(Math.min(0, ...values), Math.max(0, ...values))
  const yMin = ticks[0]
  const yMax = ticks[ticks.length - 1]
  const y = (v: number) => MARGIN.top + plotH - ((v - yMin) / (yMax - yMin || 1)) * plotH
  const band = plotW / Math.max(groups.length, 1)
  const barW = Math.min(band * 0.6, 32)
  const cx = (i: number) => MARGIN.left + band * i + band / 2
  const labelEvery = Math.max(1, Math.ceil(MIN_LABEL_SPACING / Math.max(band, 1)))
  const active = activeIndex === null ? null : groups[activeIndex]
  const linePoints = groups.map((g, i) => `${cx(i)},${y(g.netProfit)}`).join(' ')

  return (
    <div>
      <div className="mb-3 flex flex-wrap gap-x-5 gap-y-1 text-xs text-ink-soft">
        <span className="flex items-center gap-2">
          <span className="size-3 rounded-[3px] bg-brand-500 dark:bg-brand-400" aria-hidden="true" />
          {t('pnl.trend.revenue')}
        </span>
        <span className="flex items-center gap-2">
          <span className="h-0.5 w-4 bg-gold-700 dark:bg-gold-300" aria-hidden="true" />
          {t('pnl.trend.net')}
        </span>
      </div>

      <div ref={ref} className="relative w-full" style={{ height: HEIGHT }}>
        {width > 0 && (
          <svg width={width} height={HEIGHT} role="group" aria-label={t('pnl.trend.title')}>
            {ticks.map((tick) => (
              <g key={tick}>
                <line
                  x1={MARGIN.left}
                  x2={width - MARGIN.right}
                  y1={y(tick)}
                  y2={y(tick)}
                  className={tick === 0 ? 'stroke-line-strong' : 'stroke-line'}
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

            {groups.map((g, i) => {
              const top = y(Math.max(g.revenue, 0))
              const h = y(0) - top
              return (
                <g key={g.groupKey}>
                  {h > 0 && (
                    <path
                      d={roundedTopBar(cx(i) - barW / 2, top, barW, h, 4)}
                      className="fill-brand-500 dark:fill-brand-400"
                      opacity={activeIndex === null || activeIndex === i ? 1 : 0.45}
                    />
                  )}
                  {i % labelEvery === 0 && (
                    <text
                      x={cx(i)}
                      y={HEIGHT - 8}
                      textAnchor="middle"
                      className="fill-ink-soft text-[11px]"
                    >
                      {bucketLabel(g)}
                    </text>
                  )}
                </g>
              )
            })}

            {groups.length > 1 && (
              <polyline
                points={linePoints}
                fill="none"
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                className="stroke-gold-700 dark:stroke-gold-300"
              />
            )}
            {groups.map((g, i) => (
              <circle
                key={g.groupKey}
                cx={cx(i)}
                cy={y(g.netProfit)}
                r={activeIndex === i ? 5 : 4}
                strokeWidth={2}
                className="fill-gold-700 stroke-surface-2 dark:fill-gold-300"
              />
            ))}

            {groups.map((g, i) => (
              <rect
                key={g.groupKey}
                x={MARGIN.left + band * i}
                y={MARGIN.top}
                width={band}
                height={plotH}
                fill="transparent"
                tabIndex={0}
                role="img"
                aria-label={`${bucketLabel(g)}: ${t('pnl.trend.revenue')} ${formatCurrency(g.revenue)}, ${t('pnl.trend.net')} ${formatCurrency(g.netProfit)}`}
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
            className="pointer-events-none absolute z-10 w-44 rounded-card border border-line bg-surface-2 p-3 text-xs shadow-card"
            style={{
              top: MARGIN.top,
              left: Math.min(Math.max(cx(activeIndex) - 88, 0), Math.max(width - 176, 0)),
            }}
          >
            <p className="mb-1.5 font-semibold text-ink">{bucketLabel(active)}</p>
            <p className="flex items-center justify-between gap-2 text-ink-soft">
              <span className="flex items-center gap-1.5">
                <span className="size-2.5 rounded-[3px] bg-brand-500 dark:bg-brand-400" aria-hidden="true" />
                {t('pnl.trend.revenue')}
              </span>
              <span className="font-semibold tabular-nums text-ink">{formatCurrency(active.revenue)}</span>
            </p>
            <p className="flex items-center justify-between gap-2 text-ink-soft">
              <span className="flex items-center gap-1.5">
                <span className="h-0.5 w-2.5 bg-gold-700 dark:bg-gold-300" aria-hidden="true" />
                {t('pnl.trend.net')}
              </span>
              <span className="font-semibold tabular-nums text-ink">{formatCurrency(active.netProfit)}</span>
            </p>
          </div>
        )}
      </div>

      <table className="sr-only">
        <caption>{t('charts.dataTable')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('range.label')}</th>
            <th scope="col">{t('pnl.trend.revenue')}</th>
            <th scope="col">{t('pnl.trend.net')}</th>
          </tr>
        </thead>
        <tbody>
          {groups.map((g) => (
            <tr key={g.groupKey}>
              <th scope="row">{bucketLabel(g)}</th>
              <td>{formatCurrency(g.revenue)}</td>
              <td>{formatCurrency(g.netProfit)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

