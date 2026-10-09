import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { cn } from '@/utils/cn'
import { Tilt } from './Tilt'

export interface StatCardProps {
  icon: ReactNode
  label: string
  value: ReactNode
  trend?: ReactNode
  className?: string
  iconClassName?: string
  /** Turns the card into a link to a module screen, e.g. "/inventory?filter=low". */
  to?: string
  /** Marks the card as a link for assistive tech; provide when `to` is used. */
  ariaLabel?: string
}

export function StatCard({
  icon,
  label,
  value,
  trend,
  className,
  iconClassName,
  to,
  ariaLabel,
}: StatCardProps) {
  const body = (
    <div
      className={cn(
        'glow-border relative flex h-full flex-col gap-4 overflow-hidden rounded-card border border-line bg-surface-2 p-4 shadow-soft backdrop-blur-xl',
        to && 'focus-ring transition-shadow hover:shadow-card motion-reduce:transition-none',
        className,
      )}
    >
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-0 left-0 w-1/3 animate-sheen bg-gradient-to-r from-transparent via-white/10 to-transparent"
      />
      <div className="relative flex items-center gap-3">
        <div
          className={cn(
            'flex size-11 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-400/25 to-gold-400/20 text-brand-600 shadow-[inset_0_1px_0_var(--glass-hi)] dark:text-brand-300',
            iconClassName,
          )}
        >
          {icon}
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-xs text-ink-soft">{label}</p>
          <p className="font-display text-xl font-bold tracking-tight text-ink">{value}</p>
        </div>
        {trend && <div className="shrink-0 text-right">{trend}</div>}
      </div>
    </div>
  )

  if (!to) {
    return <Tilt>{body}</Tilt>
  }

  return (
    <Link to={to} aria-label={ariaLabel} className="focus-ring block h-full rounded-card">
      <Tilt>{body}</Tilt>
    </Link>
  )
}
