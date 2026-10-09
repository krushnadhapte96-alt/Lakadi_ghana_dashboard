import { forwardRef } from 'react'
import type { ButtonHTMLAttributes } from 'react'
import { cn } from '@/utils/cn'

type IconButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

type IconButtonSize = 'sm' | 'md' | 'lg'

const variantClasses: Record<IconButtonVariant, string> = {
  primary:
    'bg-brand-600 text-white shadow-soft hover:bg-brand-500 disabled:bg-brand-600/50 disabled:text-white/80',
  secondary:
    'border border-line bg-surface-2 text-ink shadow-soft hover:bg-surface-3',
  ghost: 'text-ink-soft hover:bg-surface-2 hover:text-ink',
  danger:
    'bg-crimson-600 text-white shadow-soft hover:bg-crimson-700 disabled:bg-crimson-600/50',
}

const sizeClasses: Record<IconButtonSize, string> = {
  sm: 'size-8',
  md: 'size-10',
  lg: 'size-12',
}

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: IconButtonVariant
  size?: IconButtonSize
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  ({ className, variant = 'secondary', size = 'md', type = 'button', ...props }, ref) => {
    return (
      <button
        ref={ref}
        type={type}
        className={cn(
          'focus-ring inline-flex items-center justify-center rounded-full font-semibold transition-colors duration-200 disabled:cursor-not-allowed disabled:shadow-none',
          variantClasses[variant],
          sizeClasses[size],
          className,
        )}
        {...props}
      />
    )
  },
)

IconButton.displayName = 'IconButton'

