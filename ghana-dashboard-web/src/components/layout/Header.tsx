import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useAuthStore, useAuth } from '@/stores/auth'
import { LanguageToggle } from '@/components/LanguageToggle'
import { ThemeToggle } from '@/components/ThemeToggle'
import { NotificationCenter } from '@/components/notifications/NotificationCenter'
import { CommandPalette } from '@/components/search/CommandPalette'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'
import { DropdownMenu } from '@/components/ui/DropdownMenu'
import { Avatar } from '@/components/ui/Avatar'
import { useLanguage } from '@/i18n'

export interface HeaderProps {
  onMenuClick: () => void
  collapsed: boolean
}

export function Header({ onMenuClick, collapsed }: HeaderProps) {
  const { t } = useLanguage()
  const { user } = useAuth()
  const [paletteOpen, setPaletteOpen] = useState(false)
  const navigate = useNavigate()

  // The whole keyboard-first sales floor expects Ctrl+K to find a bill number.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setPaletteOpen((current) => !current)
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [])

  const handleLogout = () => {
    useAuthStore.getState().clearSession()
    navigate('/login')
  }

  const profileItems = [
    {
      id: 'profile',
      label: t('nav.profile'),
      icon: <Icon name="user" className="size-4" />,
      onClick: () => navigate('/profile'),
    },
    {
      id: 'change-password',
      label: t('auth.changePassword'),
      icon: <Icon name="password" className="size-4" />,
      onClick: () => navigate('/change-password'),
    },
    {
      id: 'logout-all',
      label: 'Logout all devices',
      icon: <Icon name="logout" className="size-4" />,
      onClick: () => {
        // TODO: Implement logout all
        handleLogout()
      },
    },
    {
      id: 'logout',
      label: t('auth.logout'),
      icon: <Icon name="logout" className="size-4" />,
      onClick: handleLogout,
      destructive: true,
    },
  ]

  return (
    <motion.header
      initial={{ y: -100 }}
      animate={{ y: 0 }}
      transition={{ type: 'spring', damping: 25, stiffness: 300 }}
      className={cn(
        'glass fixed left-0 right-0 top-0 z-30 rounded-none border-x-0 border-t-0 transition-all duration-300',
        collapsed ? 'lg:pl-0' : 'lg:pl-64',
      )}
    >
      <div className="flex h-16 items-center justify-between gap-4 px-4">
        {/* Left */}
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={onMenuClick}
            aria-label="Toggle menu"
            className="focus-ring rounded-xl p-2 text-ink-soft transition-colors hover:bg-surface-3 hover:text-ink"
          >
            <Icon name="menu" className="size-5" />
          </button>

          {/* Shop info */}
          <div className="hidden flex-col lg:flex">
            <span className="text-sm font-semibold text-ink">{user?.shopName || 'Lakadi Ghana'}</span>
            <span className="text-xs text-ink-soft">{user?.role}</span>
          </div>
        </div>

        {/* Right */}
        <div className="flex items-center gap-2">
          {/* Global search */}
          <button
            type="button"
            onClick={() => setPaletteOpen(true)}
            className="focus-ring flex items-center gap-2 p-2 text-ink-soft hover:bg-surface-3 md:pr-1"
          >
            <Icon name="search" className="size-5" />
            <span className="hidden items-center gap-1 rounded border border-line px-1.5 py-0.5 text-[10px] md:flex">
              Ctrl K
            </span>
          </button>

          {/* Language */}
          <LanguageToggle />

          {/* Theme */}
          <ThemeToggle />

          {/* Notifications */}
          <NotificationCenter />

          {/* Profile */}
          <DropdownMenu
            trigger={
              <div className="focus-ring flex cursor-pointer items-center gap-2 p-1 rounded-card hover:bg-surface-3">
                <Avatar
                  fallback={user?.name?.charAt(0) || 'U'}
                  size="sm"
                />
                <div className="hidden text-left md:block">
                  <p className="text-sm font-medium text-ink">{user?.name || 'User'}</p>
                  <p className="text-xs text-ink-soft">{user?.phone}</p>
                </div>
                <Icon name="chevron-down" className="size-4 text-ink-soft hidden md:block" />
              </div>
            }
            items={profileItems}
            position="bottom-end"
          />
        </div>
      </div>

      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
    </motion.header>
  )
}

