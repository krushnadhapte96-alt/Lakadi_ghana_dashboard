import { motion } from 'framer-motion'
import { NavLink } from 'react-router-dom'
import { useIsOwner } from '@/auth/useCan'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'
import { sidebarNavItems } from '@/types/navigation'

export interface SidebarProps {
  collapsed: boolean
  onToggle: () => void
}

export function Sidebar({ collapsed, onToggle }: SidebarProps) {
  const isOwner = useIsOwner()

  const filteredItems = sidebarNavItems.filter(
    (item) => !item.ownerOnly || isOwner,
  )

  return (
    <motion.aside
      className="glass fixed left-0 top-0 z-40 hidden h-screen overflow-hidden rounded-none border-y-0 border-l-0 lg:block"
      initial={false}
      animate={{ width: collapsed ? 0 : 256, opacity: collapsed ? 0 : 1 }}
      transition={{ type: 'spring', stiffness: 260, damping: 30 }}
    >
      <div className="flex h-full w-64 flex-col">
        {/* Logo */}
        <div className="flex h-16 items-center border-b border-line px-4">
          <div className="flex items-center gap-3">
            <motion.div
              className="gradient-brand flex size-10 items-center justify-center rounded-2xl text-white shadow-glow"
              whileHover={{ rotateY: 180, scale: 1.08 }}
              transition={{ type: 'spring', stiffness: 200, damping: 14 }}
              style={{ transformStyle: 'preserve-3d' }}
            >
              <Icon name="dashboard" className="size-5" />
            </motion.div>
            <span className="font-display text-lg font-bold tracking-tight text-ink">
              Lakadi <span className="gradient-text">Ghana</span>
            </span>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto py-4">
          <ul className="space-y-1 px-3">
            {filteredItems.map((item) => (
              <li key={item.id}>
                <NavLink
                  to={item.path}
                  className={({ isActive }) =>
                    cn(
                      'focus-ring group relative flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-medium transition-colors',
                      isActive ? 'text-ink' : 'text-ink-soft hover:text-ink',
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      {isActive && (
                        <motion.span
                          layoutId="sidebar-active"
                          className="absolute inset-0 rounded-2xl border border-line bg-gradient-to-r from-brand-400/20 to-gold-400/10 shadow-soft"
                          transition={{ type: 'spring', stiffness: 380, damping: 32 }}
                        />
                      )}
                      <span
                        className={cn(
                          'relative flex size-8 items-center justify-center rounded-xl transition-all duration-200 group-hover:-rotate-3 group-hover:scale-110',
                          isActive
                            ? 'gradient-brand text-white shadow-glow'
                            : 'bg-surface-3 text-ink-soft',
                        )}
                      >
                        <Icon name={item.icon} className="size-4" />
                      </span>
                      <span className="relative">{item.label}</span>
                      {item.badge && (
                        <span className="relative ml-auto rounded-full bg-crimson-500/15 px-2 py-0.5 text-xs font-medium text-crimson-600 dark:text-crimson-300">
                          {item.badge}
                        </span>
                      )}
                    </>
                  )}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        {/* Collapse toggle */}
        <div className="border-t border-line p-3">
          <button
            type="button"
            onClick={onToggle}
            className="focus-ring flex w-full items-center justify-center gap-2 rounded-2xl p-2 text-sm text-ink-soft transition-colors hover:bg-surface-3 hover:text-ink"
          >
            <Icon name={collapsed ? 'chevron-right' : 'chevron-left'} className="size-4" />
            <span>Collapse</span>
          </button>
        </div>
      </div>
    </motion.aside>
  )
}
