import { motion } from 'framer-motion'
import type { LedgerEntry } from '@/api/khata'
import { Badge, EmptyState } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency, formatDate, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { ENTRY_VARIANT, deltaClass } from '@/components/khata/helpers'

function entryIcon(type: LedgerEntry['type']) {
  if (type === 'CREDIT_SALE') return 'trending-up' as const
  if (type === 'PAYMENT_RECEIVED') return 'payments' as const
  if (type === 'ADJUSTMENT') return 'pencil' as const
  return 'file' as const
}

/**
 * The party's account book: each row is one ledger entry with the balance
 * carried forward, newest first.
 */
export function LedgerTimeline({
  entries,
  language,
}: {
  entries: LedgerEntry[]
  language: 'en' | 'hi' | 'mr'
}) {
  const { t } = useLanguage()

  if (entries.length === 0) {
    return (
      <EmptyState
        icon={<Icon name="khata" className="size-7" />}
        title={t('khata.ledger.emptyTitle')}
        description={t('khata.ledger.emptyBody')}
      />
    )
  }

  // Newest first reads better in a drawer than oldest-first.
  const ordered = [...entries].reverse()

  return (
    <ol className="relative space-y-0">
      {ordered.map((entry, index) => {
        const debit = entry.delta > 0
        return (
          <motion.li
            key={entry.id ?? `opening-${index}`}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.22, delay: Math.min(index * 0.04, 0.3) }}
            className="relative flex gap-3 pb-4 last:pb-0"
          >
            {/* Timeline spine */}
            <div className="flex flex-col items-center">
              <span
                className={cn(
                  'flex size-8 shrink-0 items-center justify-center rounded-full',
                  debit
                    ? 'bg-clay-50 text-clay-600 dark:bg-clay-500/20 dark:text-clay-300'
                    : 'bg-brand-50 text-brand-600 dark:bg-brand-500/20 dark:text-brand-300',
                )}
              >
                <Icon name={entryIcon(entry.type)} className="size-4" />
              </span>
              {index < ordered.length - 1 && (
                <span className="mt-1 w-px flex-1 bg-line" aria-hidden="true" />
              )}
            </div>

            <div className="min-w-0 flex-1 pt-1">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={ENTRY_VARIANT[entry.type]} size="sm">
                  {t(`khata.entry.${entry.type}`)}
                </Badge>
                <span className="text-xs text-ink-soft">{formatDate(entry.date, language)}</span>
                {entry.dueDate && (
                  <span className="text-xs text-ink-soft">
                    {t('khata.ledger.due', { date: formatDate(entry.dueDate, language) })}
                  </span>
                )}
              </div>

              {(entry.billNo || entry.note) && (
                <p className="mt-1 truncate text-sm text-ink">
                  {entry.billNo ?? entry.note}
                </p>
              )}
              {entry.paymentMode && (
                <p className="mt-0.5 text-xs text-ink-soft">
                  {t('khata.ledger.via', { mode: t(`payment.mode.${entry.paymentMode}`) })}
                </p>
              )}

              {entry.items.length > 0 && (
                <ul className="mt-2 space-y-0.5 rounded-card bg-surface-3 px-2.5 py-2">
                  {entry.items.map((item, position) => (
                    <li
                      key={`${item.productId ?? item.productName}-${position}`}
                      className="flex justify-between gap-2 text-xs text-ink-soft"
                    >
                      <span className="min-w-0 truncate">
                        {item.productName}
                        {' · '}
                        {formatQty(item.qty)}
                        {item.unit ? ` ${item.unit}` : ''}
                      </span>
                      <span className="shrink-0 tabular-nums">
                        {formatCurrency(item.amount, { decimals: 2 })}
                      </span>
                    </li>
                  ))}
                </ul>
              )}

              <div className="mt-2 flex items-baseline justify-between gap-2">
                <span className={cn('text-sm font-semibold tabular-nums', deltaClass(entry.delta))}>
                  {debit ? '+' : '-'}
                  {formatCurrency(Math.abs(entry.delta), { decimals: 2 })}
                </span>
                <span className="text-xs text-ink-soft">
                  {t('khata.ledger.balance')}
                  <span className="ml-1 font-medium tabular-nums text-ink">
                    {formatCurrency(entry.runningBalance, { decimals: 2 })}
                  </span>
                </span>
              </div>
            </div>
          </motion.li>
        )
      })}
    </ol>
  )
}
