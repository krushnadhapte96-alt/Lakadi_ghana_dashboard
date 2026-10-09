import { motion } from 'framer-motion'
import type { ReactNode } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

export interface PageTransitionProps {
  children?: ReactNode
}

export function PageTransition({ children }: PageTransitionProps) {
  const { pathname } = useLocation()
  return (
    <div className="perspective-1200">
      <motion.div
        key={pathname}
        initial={{ opacity: 0, y: 24, rotateX: 6, scale: 0.985 }}
        animate={{ opacity: 1, y: 0, rotateX: 0, scale: 1 }}
        transition={{ duration: 0.5, ease: [0.2, 0.8, 0.2, 1] }}
        style={{ transformOrigin: '50% 0%' }}
      >
        {children ?? <Outlet />}
      </motion.div>
    </div>
  )
}
