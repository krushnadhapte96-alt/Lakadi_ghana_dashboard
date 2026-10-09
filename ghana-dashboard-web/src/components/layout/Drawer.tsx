import { motion, AnimatePresence } from 'framer-motion'
import { NavLink } from 'react-router-dom'
import { useIsOwner } from '@/auth/useCan'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'
import { moreDrawerItems } from '@/types/navigation'

export interface DrawerProps {
  open: boolean
  onClose: () => void
}

export function Drawer({ open, onClose }: DrawerProps) {
  const isOwner = useIsOwner()

  const filteredItems = moreDrawerItems.filter(
    (item) => !item.ownerOnly || isOwner,
  )

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ x: '100%' }}
          animate={{ x: 0 }}
          exit={{ x: '100%' }}
          transition={{ type: 'spring', damping: 30, stiffness: 300 }}
          className="fixed inset-0 z-50 lg:hidden"
        >
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="absolute inset-0 bg-black/50"
          />

          {/* Drawer */}
          <motion.div
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            className="absolute right-0 top-0 h-full w-80 bg-surface-2 shadow-2xl"
          >
            {/* Header */}
            <div className="flex h-16 items-center justify-between border-b border-line px-4">
              <h2 className="text-lg font-bold text-ink">Menu</h2>
              <button
                type="button"
                onClick={onClose}
                className="focus-ring p-2 text-ink-soft hover:bg-surface-3"
              >
                <Icon name="close" className="size-5" />
              </button>
            </div>

            {/* Navigation */}
            <nav className="flex-1 overflow-y-auto py-4">
              <ul className="space-y-1 px-2">
                {filteredItems.map((item) => {
                  const isLogout = item.id === 'logout'
                  return (
                    <li key={item.id}>
                      {isLogout ? (
                        <button
                          type="button"
                          onClick={() => {
                            // Handle logout
                            onClose()
                          }}
                          className="focus-ring flex w-full items-center gap-3 rounded-pill px-3 py-2.5 text-sm font-medium text-crimson-600 transition-colors hover:bg-crimson-50"
                        >
                          <span
                            className={cn(
                              'flex size-8 items-center justify-center rounded-card bg-crimson-50',
                            )}
                          >
                            <Icon name={item.icon} className="size-4" />
                          </span>
                          <span>{item.label}</span>
                        </button>
                      ) : (
                        <NavLink
                          to={item.path}
                          onClick={onClose}
                          className={({ isActive }) =>
                            cn(
                              'focus-ring flex items-center gap-3 rounded-pill px-3 py-2.5 text-sm font-medium transition-colors',
                              isActive
                                ? 'bg-brand-50 text-brand-600 dark:bg-brand-500/20 dark:text-brand-300'
                                : 'text-ink-soft hover:bg-surface-3 hover:text-ink',
                            )
                          }
                        >
                          <span
                            className={cn(
                              'flex size-8 items-center justify-center rounded-card bg-surface-3',
                            )}
                          >
                            <Icon name={item.icon} className="size-4" />
                          </span>
                          <span>{item.label}</span>
                        </NavLink>
                      )}
                    </li>
                  )
                })}
              </ul>
            </nav>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

