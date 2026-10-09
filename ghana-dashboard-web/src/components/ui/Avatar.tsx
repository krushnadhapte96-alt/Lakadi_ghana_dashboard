import type { HTMLAttributes } from 'react'
import { cn } from '@/utils/cn'

export interface AvatarProps extends HTMLAttributes<HTMLDivElement> {
  src?: string
  alt?: string
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
  fallback?: string
}

const sizeClasses = {
  xs: 'size-6 text-xs',
  sm: 'size-8 text-sm',
  md: 'size-10 text-base',
  lg: 'size-12 text-lg',
  xl: 'size-16 text-2xl',
}

export function Avatar({
  src,
  alt = 'Avatar',
  size = 'md',
  fallback,
  className,
  ...props
}: AvatarProps) {
  const displayFallback = !src || fallback

  return (
    <div
      className={cn(
        'inline-flex items-center justify-center rounded-full bg-brand-50 text-brand-600 font-semibold',
        sizeClasses[size],
        className,
      )}
      {...props}
    >
      {src && !displayFallback ? (
        <img
          src={src}
          alt={alt}
          className="h-full w-full rounded-full object-cover"
        />
      ) : (
        <span className="uppercase">
          {fallback?.charAt(0) || alt.charAt(0)}
        </span>
      )}
    </div>
  )
}

