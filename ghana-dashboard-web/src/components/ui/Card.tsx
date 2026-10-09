import type { HTMLAttributes } from 'react'
import { cn } from '@/utils/cn'
import { Tilt } from './Tilt'

export type CardProps = HTMLAttributes<HTMLDivElement>

/** Glass card with a subtle pointer-driven 3D tilt and light glare. */
export function Card({ className, ...props }: CardProps) {
  return (
    <Tilt max={2.5} scale={1.004}>
      <div
        className={cn(
          'glow-border rounded-card border border-line bg-surface-2 shadow-soft backdrop-blur-xl transition-shadow duration-300 hover:shadow-card',
          className,
        )}
        {...props}
      />
    </Tilt>
  )
}
