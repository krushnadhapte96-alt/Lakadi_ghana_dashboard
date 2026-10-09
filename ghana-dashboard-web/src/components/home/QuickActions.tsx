import { Link } from 'react-router-dom'
import { Icon } from '@/utils/icons'
import type { IconName } from '@/utils/icons'
import { entryHref, type EntryAction } from '@/utils/entryPoint'
import { useLanguage } from '@/i18n'

interface QuickAction {
  action: EntryAction
  path: string
  icon: IconName
  labelKey: string
}

/**
 * The six things a shopkeeper does most: ring up a sale, take an order, start a press
 * run, buy seed, book udhaar, log an expense.
 *
 * <p>Each is a real link, not a button with a handler - middle-click and open-in-new-tab
 * work, and the target module reads `?action=` off the URL to open its own drawer, so the
 * destination owns its entry point instead of this page reaching into it.
 */
const ACTIONS: QuickAction[] = [
  { action: 'sale', path: '/sales', icon: 'sale', labelKey: 'home.quick.sale' },
  { action: 'order', path: '/sales', icon: 'truck', labelKey: 'home.quick.order' },
  { action: 'batch', path: '/production', icon: 'production', labelKey: 'home.quick.batch' },
  { action: 'purchase', path: '/beej', icon: 'beej', labelKey: 'home.quick.purchase' },
  { action: 'credit', path: '/khata', icon: 'khata', labelKey: 'home.quick.credit' },
  { action: 'expense', path: '/expenses', icon: 'expenses', labelKey: 'home.quick.expense' },
]

export function QuickActions() {
  const { t } = useLanguage()

  return (
    <div>
      <h2 className="mb-3 text-sm font-semibold text-ink-soft">{t('home.quick.title')}</h2>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {ACTIONS.map((item) => (
          <Link
            key={item.action}
            to={entryHref(item.path, item.action)}
            className="focus-ring group flex flex-col items-center gap-2 rounded-card border border-line bg-surface-2 p-4 text-center shadow-soft transition-shadow hover:shadow-card motion-reduce:transition-none"
          >
            <span className="flex size-10 items-center justify-center rounded-card bg-brand-50 text-brand-600 transition-transform group-hover:scale-105 motion-reduce:transition-none dark:bg-brand-500/20 dark:text-brand-300">
              <Icon name={item.icon} className="size-5" />
            </span>
            <span className="flex items-center gap-1 text-sm font-medium text-ink">
              <Icon name="plus" className="size-3.5 text-gold-600 dark:text-gold-300" />
              {t(item.labelKey)}
            </span>
          </Link>
        ))}
      </div>
    </div>
  )
}
