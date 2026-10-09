import { cn } from '@/utils/cn'

export interface SkeletonProps {
  className?: string
  variant?: 'text' | 'circular' | 'rectangular'
}

export function Skeleton({ className, variant = 'rectangular' }: SkeletonProps) {
  const baseClasses = 'shimmer-surface animate-shimmer rounded-card'

  const variantClasses = {
    text: 'h-4 rounded-pill',
    circular: 'aspect-square rounded-full',
    rectangular: '',
  }

  return (
    <div
      aria-hidden="true"
      className={cn(baseClasses, variantClasses[variant], className)}
    />
  )
}

