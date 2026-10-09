import type { DashboardSummary } from '@/api/dashboard'
import { Link } from 'react-router-dom'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import type { IconName } from '@/utils/icons'

type Translate = (key: string, vars?: Record<string, string | number>) => string

interface Alert {
  key: string
  icon: IconName
  tone: 'danger' | 'warning' | 'info'
  message: string
  to: string
  /** Reserved for the most urgent alert so the strip reads as a strip, not a marquee. */
  urgent: boolean
}

/**
 * One-line "what needs attention today" strip.
 *
 * <p>Built from the same summary payload the cards use, so the strip and the tiles can never
 * disagree. Alerts are ordered by how badly they need doing - expired licences first, then
 * stockouts and imminent expiries, then the softer warnings - and each links straight to the
 * module that can resolve it.
 */
export function AlertStrip({ summary }: { summary: DashboardSummary }) {
  const { t, language } = useLanguage()
  const alerts = buildAlerts(summary, t, language)

  if (alerts.length === 0) {
    return (
      <div className="flex items-center gap-2.5 rounded-card border border-brand-200 bg-brand-50 px-4 py-3 text-sm text-brand-700 dark:border-brand-500/30 dark:bg-brand-500/10 dark:text-brand-300">
        <Icon name="check" className="size-4 shrink-0" />
        {t('home.alerts.allClear')}
      </div>
    )
  }

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-card border border-line bg-surface-2 px-4 py-3">
      <span className="flex items-center gap-2 text-sm font-semibold text-ink">
        <span className="relative flex size-2.5" aria-hidden="true">
          {alerts.some((alert) => alert.urgent) && (
            <span className="absolute inline-flex size-full animate-ping rounded-full bg-clay-500 opacity-75 motion-reduce:animate-none" />
          )}
          <span
            className={cn(
              'relative inline-flex size-2.5 rounded-full',
              alerts.some((alert) => alert.urgent) ? 'bg-clay-500' : 'bg-gold-500',
            )}
          />
        </span>
        {t('home.alerts.title')}
      </span>

      <div className="flex flex-wrap items-center gap-2">
        {alerts.map((alert) => (
          <AlertPill key={alert.key} alert={alert} />
        ))}
      </div>
    </div>
  )
}

function AlertPill({ alert }: { alert: Alert }) {
  const tone = {
    danger: 'bg-crimson-50 text-crimson-700 dark:bg-crimson-500/20 dark:text-crimson-300',
    warning: 'bg-clay-50 text-clay-700 dark:bg-clay-500/20 dark:text-clay-300',
    info: 'bg-surface-3 text-ink-soft',
  }[alert.tone]

  return (
    <Link
      to={alert.to}
      className={cn(
        'focus-ring inline-flex items-center gap-1.5 rounded-pill px-2.5 py-1 text-xs font-medium transition-opacity hover:opacity-80',
        tone,
      )}
    >
      <Icon name={alert.icon} className="size-3.5 shrink-0" />
      {alert.message}
    </Link>
  )
}

function buildAlerts(
  summary: DashboardSummary,
  t: Translate,
  language: 'en' | 'hi' | 'mr',
): Alert[] {
  const alerts: Alert[] = []

  if (summary.licences.expired > 0) {
    alerts.push({
      key: 'licence-expired',
      icon: 'alert-circle',
      tone: 'danger',
      urgent: true,
      to: '/licences?status=EXPIRED',
      message: t('home.alerts.licenceExpired', { count: summary.licences.expired }),
    })
  }

  if (summary.licences.next) {
    const next = summary.licences.next
    const daysLeft = next.daysLeft
    alerts.push({
      key: 'licence-next',
      icon: 'licences',
      tone: daysLeft <= 7 ? 'danger' : 'warning',
      urgent: daysLeft <= 7,
      to: '/licences',
      message: t('home.alerts.licenceNext', {
        number: next.number,
        type: t(`licences.type.${next.type}`),
        days: daysLeft,
        date: formatDate(next.expiryDate, language, { day: 'numeric', month: 'short' }),
      }),
    })
  }

  if (summary.lowStock.count > 0) {
    alerts.push({
      key: 'low-stock',
      icon: 'inventory',
      tone: 'warning',
      urgent: true,
      to: '/inventory?filter=low',
      message: t('home.alerts.lowStock', { count: summary.lowStock.count }),
    })
  }

  if (summary.expiringSoon.count > 0) {
    alerts.push({
      key: 'expiring',
      icon: 'expiry',
      tone: 'warning',
      urgent: false,
      to: '/expiry-alerts',
      message: t('home.alerts.expiringSoon', { count: summary.expiringSoon.count }),
    })
  }

  if (summary.moneyToCollect.amount > 0) {
    alerts.push({
      key: 'collect',
      icon: 'wallet',
      tone: 'info',
      urgent: false,
      to: '/khata',
      message: t('home.alerts.moneyToCollect', {
        amount: formatCurrency(summary.moneyToCollect.amount, { decimals: 0 }),
      }),
    })
  }

  if (summary.refillDue.count > 0) {
    alerts.push({
      key: 'refill',
      icon: 'customers',
      tone: 'info',
      urgent: false,
      to: '/customers',
      message: t('home.alerts.refillDue', { count: summary.refillDue.count }),
    })
  }

  if (summary.seedStock.lowCount > 0) {
    alerts.push({
      key: 'seed-low',
      icon: 'beej',
      tone: 'warning',
      urgent: false,
      to: '/beej',
      message: t('home.alerts.seedLow', { count: summary.seedStock.lowCount }),
    })
  }

  if (summary.pendingOrders > 0) {
    alerts.push({
      key: 'pending-orders',
      icon: 'truck',
      tone: 'info',
      urgent: false,
      to: '/sales',
      message: t('home.alerts.pendingOrders', { count: summary.pendingOrders }),
    })
  }

  return alerts
}
