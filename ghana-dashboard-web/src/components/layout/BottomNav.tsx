import { NavLink } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'
import { bottomNavItems } from '@/types/navigation'

export interface BottomNavProps {
  onMoreClick: () => void
}

export function BottomNav({ onMoreClick }: BottomNavProps) {
  return (
    <motion.nav
      initial={{ y: 100 }}
      animate={{ y: 0 }}
      transition={{ type: 'spring', damping: 25, stiffness: 300 }}
      className="fixed bottom-0 left-0 right-0 z-40 border-t border-line bg-surface-2 lg:hidden"
    >
      <ul className="flex justify-around px-2 py-1">
        {bottomNavItems.map((item) => {
          const isMore = item.id === 'more'
          return (
            <li key={item.id}>
              <NavLink
                to={isMore ? '#' : item.path}
                onClick={(e) => {
                  if (isMore) {
                    e.preventDefault()
                    onMoreClick()
                  }
                }}
                className={({ isActive }) =>
                  cn(
                    'focus-ring flex flex-col items-center gap-1 py-2 px-4 text-xs font-medium transition-colors',
                    isActive
                      ? 'text-brand-600'
                      : 'text-ink-soft hover:text-ink',
                  )
                }
              >
                <motion.span
                  className={cn(
                    'flex size-8 items-center justify-center rounded-card',
                    isMore ? 'bg-surface-3' : 'bg-surface-3',
                  )}
                  whileTap={{ scale: 0.95 }}
                >
                  <Icon name={item.icon} className="size-5" />
                </motion.span>
                <span>{item.label}</span>
              </NavLink>
            </li>
          )
        })}
      </ul>
    </motion.nav>
  )
}

