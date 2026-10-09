import { TONE_STROKE, TONE_TEXT, TONE_TRACK, remainingFraction, toneFor } from '@/components/licences/helpers'
import type { Licence } from '@/api/licences'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'

interface DaysLeftRingProps {
  licence: Licence
  size?: number
  className?: string
}

const STROKE = 6

/**
 * Circular gauge of how much of the licence's validity window is left, tinted by
 * status: leaf while healthy, mustard inside the 30-day window, clay once lapsed.
 */
export function DaysLeftRing({ licence, size = 72, className }: DaysLeftRingProps) {
  const { t } = useLanguage()
  const tone = toneFor(licence.status)
  const radius = (size - STROKE) / 2
  const circumference = 2 * Math.PI * radius
  // An expired licence still draws a full ring; only the tint reads as a problem.
  const fraction = licence.status === 'EXPIRED' ? 1 : remainingFraction(licence)
  const offset = circumference * (1 - fraction)
  const lapsed = licence.status === 'EXPIRED'

  const caption = lapsed
    ? t('licences.ring.expiredDays', { count: Math.abs(licence.daysLeft) })
    : t('licences.ring.daysLeft', { count: licence.daysLeft })

  return (
    <div
      className={cn('relative shrink-0', className)}
      style={{ width: size, height: size }}
      role="img"
      aria-label={caption}
      title={caption}
    >
      <svg width={size} height={size} className="-rotate-90" aria-hidden="true">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={STROKE}
          className={TONE_TRACK[tone]}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={STROKE}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          className={cn(TONE_STROKE[tone], 'transition-[stroke-dashoffset] duration-700 ease-out')}
        />
      </svg>
      <span
        className={cn(
          'absolute inset-0 flex flex-col items-center justify-center font-semibold tabular-nums',
          TONE_TEXT[tone],
        )}
      >
        <span className={lapsed ? 'text-xs uppercase leading-none' : 'text-lg leading-none'}>
          {Math.abs(licence.daysLeft)}
        </span>
        {!lapsed && <span className="mt-0.5 text-[10px] font-medium uppercase leading-none text-ink-soft">d</span>}
      </span>
    </div>
  )
}
