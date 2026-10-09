import type { HTMLAttributes } from 'react'
import { cn } from '@/utils/cn'

type BadgeVariant = 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'danger' | 'info' | 'clay'

const variantClasses: Record<BadgeVariant, string> = {
  default: 'bg-surface-3 text-ink-soft',
  primary: 'bg-brand-50 text-brand-700 dark:bg-brand-500/20 dark:text-brand-300',
  secondary: 'bg-surface-3 text-ink-soft',
  success: 'bg-gold-50 text-gold-700 dark:bg-gold-500/20 dark:text-gold-300',
  warning: 'bg-amber-50 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300',
  danger: 'bg-crimson-50 text-crimson-700 dark:bg-crimson-500/20 dark:text-crimson-300',
  info: 'bg-blue-50 text-blue-700 dark:bg-blue-500/20 dark:text-blue-300',
  clay: 'bg-clay-50 text-clay-700 dark:bg-clay-500/20 dark:text-clay-300',
}

type BadgeSize = 'sm' | 'md' | 'lg'

const sizeClasses: Record<BadgeSize, string> = {
  sm: 'px-2 py-0.5 text-[10px] font-medium',
  md: 'px-2.5 py-0.5 text-xs font-medium',
  lg: 'px-3 py-1 text-xs font-medium',
}

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant
  size?: BadgeSize
  dot?: boolean
}

export function Badge({
  className,
  variant = 'default',
  size = 'md',
  dot = false,
  children,
  ...props
}: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-pill font-semibold',
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      {...props}
    >
      {dot && (
        <span
          className={cn('size-1.5 rounded-full', {
            'bg-brand-500': variant === 'primary',
            'bg-gold-500': variant === 'success',
            'bg-crimson-500': variant === 'danger',
            'bg-blue-500': variant === 'info',
            'bg-amber-500': variant === 'warning',
            'bg-clay-500': variant === 'clay',
            'bg-ink-soft': variant === 'default' || variant === 'secondary',
          })}
        />
      )}
      {children}
    </span>
  )
}

