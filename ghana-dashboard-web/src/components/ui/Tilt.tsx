import { useRef } from 'react'
import type { ReactNode } from 'react'
import { motion, useMotionValue, useReducedMotion, useSpring, useTransform } from 'framer-motion'
import { cn } from '@/utils/cn'

export interface TiltProps {
  children: ReactNode
  className?: string
  /** Max rotation in degrees on each axis. */
  max?: number
  /** Hover zoom factor. */
  scale?: number
}

/**
 * Wraps content in a pointer-driven 3D tilt with a moving light glare.
 * Renders children unchanged under reduced-motion; touch pointers never tilt.
 */
export function Tilt({ children, className, max = 8, scale = 1.02 }: TiltProps) {
  const ref = useRef<HTMLDivElement>(null)
  const reduce = useReducedMotion()
  const px = useMotionValue(0.5)
  const py = useMotionValue(0.5)
  const spring = { stiffness: 220, damping: 20, mass: 0.6 }
  const rotateX = useSpring(useTransform(py, [0, 1], [max, -max]), spring)
  const rotateY = useSpring(useTransform(px, [0, 1], [-max, max]), spring)
  const glare = useTransform(
    [px, py],
    ([x, y]: number[]) =>
      `radial-gradient(240px circle at ${x * 100}% ${y * 100}%, oklch(1 0 0 / 0.16), transparent 60%)`,
  )

  if (reduce) {
    return <div className={cn('h-full', className)}>{children}</div>
  }

  return (
    <div className="perspective-1200 h-full">
      <motion.div
        ref={ref}
        className={cn('group/tilt relative h-full will-change-transform', className)}
        style={{ rotateX, rotateY, transformStyle: 'preserve-3d' }}
        whileHover={{ scale }}
        transition={{ type: 'spring', stiffness: 300, damping: 24 }}
        onPointerMove={(e) => {
          if (e.pointerType === 'touch' || !ref.current) return
          const r = ref.current.getBoundingClientRect()
          px.set((e.clientX - r.left) / r.width)
          py.set((e.clientY - r.top) / r.height)
        }}
        onPointerLeave={() => {
          px.set(0.5)
          py.set(0.5)
        }}
      >
        {children}
        <motion.span
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 rounded-card opacity-0 transition-opacity duration-300 group-hover/tilt:opacity-100"
          style={{ background: glare }}
        />
      </motion.div>
    </div>
  )
}
