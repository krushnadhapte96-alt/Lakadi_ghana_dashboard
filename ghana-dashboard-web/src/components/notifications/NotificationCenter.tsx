import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notifications'
import type { NotificationItem } from '@/api/notifications'
import { useNotificationBell, NOTIFICATIONS_QUERY_KEY } from '@/hooks/useNotificationBell'
import { Badge } from '@/components/ui/Badge'
import { Icon } from '@/utils/icons'
import type { IconName } from '@/utils/icons'
import { Switch } from '@/components/ui/Switch'
import { cn } from '@/utils/cn'
import { useLanguage } from '@/i18n'
import { setSoundEnabled, soundEnabled } from '@/utils/sound'

const TYPE_ICONS: Record<string, IconName> = {
  LOW_STOCK: 'stock',
  NEW_ORDER: 'sale',
  ORDER_OVERDUE: 'clock',
  EXPIRY: 'expiry',
  LICENCE: 'licences',
  UDHAAR_OVERDUE: 'khata',
  SEED_LOW: 'beej',
  SUPPLIER_DUE: 'truck',
  REFILL_DUE: 'customers',
  DAY_NOT_CLOSED: 'payments',
}

const SEVERITY_VARIANT = {
  INFO: 'info',
  SUCCESS: 'success',
  WARNING: 'warning',
  CRITICAL: 'danger',
} as const

function relativeTime(iso: string, language: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const minutes = Math.round((Date.now() - then) / 60_000)
  if (minutes < 1) return language === 'hi' ? 'abhi' : 'just now'
  if (minutes < 60) return language === 'hi' ? `${minutes} minute pehle` : `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return language === 'hi' ? `${hours} ghante pehle` : `${hours}h ago`
  const days = Math.round(hours / 24)
  return language === 'hi' ? `${days} din pehle` : `${days}d ago`
}

/**
 * The bell in the top bar. The badge is a number with a little bump animation, the panel groups
 * what is new against what came earlier, and every row is one tap from both "mark read" and the
 * screen it deep-links to.
 */
export function NotificationCenter() {
  const { t, language } = useLanguage()
  const { unreadCount } = useNotificationBell()
  const [open, setOpen] = useState(false)
  const [sound, setSound] = useState(soundEnabled())
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const list = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => fetchNotifications({ size: 20 }),
    enabled: open,
  })

  const readOne = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY })
    },
  })
  const readAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY })
    },
  })

  useEffect(() => {
    if (!open) return
    function handleClick(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  const items = list.data?.items ?? []
  const unreadItems = items.filter((item) => !item.read)
  const earlierItems = items.filter((item) => item.read)

  function handleOpen(item: NotificationItem) {
    if (!item.read) {
      readOne.mutate(item.id)
    }
    setOpen(false)
    if (item.link) {
      navigate(item.link)
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        aria-label={t('notifications.aria')}
        onClick={() => setOpen((current) => !current)}
        className="focus-ring relative p-2 text-ink-soft hover:bg-surface-3"
      >
        <Icon name="bell" className="size-5" />
        {unreadCount > 0 && (
          <Badge
            key={unreadCount}
            variant="danger"
            size="sm"
            className="absolute -right-1 -top-1 h-4 min-w-4 animate-badge-bump p-0 px-0.5 text-[10px] font-bold motion-reduce:animate-none"
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </Badge>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-1 w-[22rem] max-w-[calc(100vw-2rem)] rounded-card border border-line bg-surface-2 shadow-card">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <h2 className="text-sm font-semibold text-ink">{t('notifications.title')}</h2>
            <button
              type="button"
              onClick={() => readAll.mutate()}
              disabled={unreadCount === 0 || readAll.isPending}
              className="text-xs font-medium text-brand-600 hover:underline disabled:opacity-40"
            >
              {t('notifications.markAllRead')}
            </button>
          </div>

          <div className="max-h-[24rem] overflow-y-auto p-1">
            {list.isLoading ? (
              <p className="px-3 py-8 text-center text-sm text-ink-soft">{t('common.loading')}</p>
            ) : items.length === 0 ? (
              <p className="px-3 py-8 text-center text-sm text-ink-soft">{t('notifications.empty')}</p>
            ) : (
              <>
                {unreadItems.length > 0 && (
                  <>
                    <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-ink-soft">
                      {t('notifications.section.new')}
                    </p>
                    {unreadItems.map((item) => (
                      <Row key={item.id} item={item} language={language} onOpen={handleOpen} />
                    ))}
                  </>
                )}
                {earlierItems.length > 0 && (
                  <>
                    <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-ink-soft">
                      {t('notifications.section.earlier')}
                    </p>
                    {earlierItems.map((item) => (
                      <Row key={item.id} item={item} language={language} onOpen={handleOpen} />
                    ))}
                  </>
                )}
              </>
            )}
          </div>

          <div className="flex items-center justify-between border-t border-line px-4 py-2.5">
            <label htmlFor="notification-sound" className="text-xs font-medium text-ink-soft">
              {t('notifications.soundLabel')}
            </label>
            <Switch
              id="notification-sound"
              checked={sound}
              onChange={(event) => {
                setSound(event.target.checked)
                setSoundEnabled(event.target.checked)
              }}
            />
          </div>
        </div>
      )}
    </div>
  )
}

function Row({
  item,
  language,
  onOpen,
}: {
  item: NotificationItem
  language: string
  onOpen: (item: NotificationItem) => void
}) {
  const icon = TYPE_ICONS[item.type] ?? 'bell'
  return (
    <button
      type="button"
      onClick={() => onOpen(item)}
      className={cn(
        'flex w-full items-start gap-3 rounded-card px-3 py-2.5 text-left transition-colors hover:bg-surface-3',
        !item.read && 'bg-brand-50/40 dark:bg-brand-500/5',
      )}
    >
      <span className="mt-0.5 text-ink-soft">
        <Icon name={icon} className="size-4" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2">
          <span className="truncate text-sm font-medium text-ink">{item.title}</span>
          {!item.read && <span className="size-1.5 shrink-0 rounded-full bg-brand-500" />}
        </span>
        {item.message && <span className="line-clamp-2 text-xs text-ink-soft">{item.message}</span>}
      </span>
      <span className="flex shrink-0 flex-col items-end gap-1">
        <Badge variant={SEVERITY_VARIANT[item.severity] ?? 'default'} size="sm">
          {item.severity}
        </Badge>
        <span className="text-[11px] text-ink-soft">{relativeTime(item.createdAt, language)}</span>
      </span>
    </button>
  )
}

