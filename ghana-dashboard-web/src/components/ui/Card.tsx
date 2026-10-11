import type { HTMLAttributes } from 'react'
import { cn } from '@/utils/cn'
import { Tilt } from './Tilt'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /**
   * Opt out of the pointer-driven 3D tilt. Cards that host forms, menus or other
   * popovers should set this to false: the tilt's transform creates a stacking
   * context that clips dropdowns and breaks sticky children.
   */
  tilt?: boolean
}

/** Glass card with a subtle pointer-driven 3D tilt and light glare. */
export function Card({ className, tilt = true, ...props }: CardProps) {
  const content = (
    <div
      className={cn(
        'glow-border rounded-card border border-line bg-surface-2 shadow-soft backdrop-blur-xl transition-shadow duration-300 hover:shadow-card',
        className,
      )}
      {...props}
    />
  )

  if (!tilt) return content
  return (
    <Tilt max={2.5} scale={1.004}>
      {content}
    </Tilt>
  )
}
