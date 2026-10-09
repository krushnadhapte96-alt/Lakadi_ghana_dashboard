import { cn } from '@/utils/cn'
import { formatCurrency } from '@/utils/format'

export interface CostSegment {
  key: string
  label: string
  value: number
  barClass: string
  dotClass: string
}

export function StackedCostBar({ segments }: { segments: CostSegment[] }) {
  const gross = segments.reduce((sum, s) => sum + Math.max(s.value, 0), 0)

  return (
    <div className="space-y-3">
      <div className="flex h-3 w-full overflow-hidden rounded-pill bg-surface-3" role="img" aria-hidden="true">
        {segments.map((s) =>
          s.value > 0 ? (
            <div
              key={s.key}
              className={cn('h-full', s.barClass)}
              style={{ width: `${gross > 0 ? (s.value / gross) * 100 : 0}%` }}
            />
          ) : null,
        )}
      </div>

      <ul className="space-y-1.5">
        {segments.map((s) => (
          <li key={s.key} className="flex items-center gap-2.5 text-sm">
            <span className={cn('size-2.5 shrink-0 rounded-full', s.dotClass)} aria-hidden="true" />
            <span className="min-w-0 flex-1 truncate text-ink-soft">{s.label}</span>
            <span
              className={cn(
                'tabular-nums font-medium',
                s.value < 0 ? '-' : 'text-ink',
              )}
            >
              {s.value < 0 ? '-' : ''}
              {formatCurrency(Math.abs(s.value))}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

