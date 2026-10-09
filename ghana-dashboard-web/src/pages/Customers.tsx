import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CustomerDetailDrawer } from '@/components/customers/CustomerDetailDrawer'
import { inactiveState, refillState } from '@/components/customers/helpers'
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Input,
  PageHeader,
  Skeleton,
  Tabs,
} from '@/components/ui'
import { CUSTOMER_TABS, fetchCustomers } from '@/api/customers'
import type { CustomerListItem, CustomerTab } from '@/api/customers'
import { normalizeError } from '@/api/axios'
import { useDebounced } from '@/hooks/useDebounced'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'

export function CustomersPage() {
  const { t, language } = useLanguage()
  const [tab, setTab] = useState<CustomerTab>('all')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<number | null>(null)
  const debouncedSearch = useDebounced(search, 300)

  const query = useQuery({
    queryKey: ['customers', tab, debouncedSearch],
    queryFn: () => fetchCustomers({ tab, q: debouncedSearch }),
  })

  const items = query.data ?? []

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6">
      <PageHeader
        title={t('customers.title')}
        subtitle={t('customers.subtitle')}
        actions={
          <Button variant="secondary" size="sm" onClick={() => setTab('refill')}>
            <Icon name="calendar" className="size-4" />
            {t('customers.refillDue')}
          </Button>
        }
      />

      <Tabs
        className="mb-4"
        activeTab={tab}
        onTabChange={(next) => setTab(next as CustomerTab)}
        tabs={CUSTOMER_TABS.map((id) => ({
          id,
          label: t(`customers.tabs.${id}`),
        }))}
      />

      <div className="mb-4 max-w-md">
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('customers.search')}
          aria-label={t('customers.search')}
          type="search"
        />
      </div>

      {query.isPending && (
        <div className="flex flex-col gap-3">
          {[0, 1, 2, 3].map((row) => (
            <Skeleton key={row} className="h-20 w-full" />
          ))}
        </div>
      )}

      {query.isError && (
        <ErrorState
          title={t('customers.error.title')}
          message={normalizeError(query.error).message || t('customers.error.body')}
          onRetry={() => query.refetch()}
          retryLabel={t('common.retry')}
        />
      )}

      {query.isSuccess && items.length === 0 && (
        <EmptyState
          icon={<Icon name="customers" className="size-8" />}
          title={t(`customers.empty.${tab}.title`)}
          description={t(`customers.empty.${tab}.body`)}
          action={
            search ? (
              <Button variant="outline" size="sm" onClick={() => setSearch('')}>
                {t('customers.search.clear')}
              </Button>
            ) : undefined
          }
        />
      )}

      {query.isSuccess && items.length > 0 && (
        <ul className="flex flex-col gap-2">
          {items.map((customer) => (
            <li key={customer.id}>
              <CustomerRow
                customer={customer}
                language={language}
                onOpen={() => setSelected(customer.id)}
              />
            </li>
          ))}
        </ul>
      )}

      <CustomerDetailDrawer customerId={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

function CustomerRow({
  customer,
  language,
  onOpen,
}: {
  customer: CustomerListItem
  language: 'en' | 'hi' | 'mr'
  onOpen: () => void
}) {
  const { t } = useLanguage()
  const idle = inactiveState(customer.daysSinceLastPurchase)
  const refill = refillState(customer)

  // Only flag what the active tab is not already telling the user.
  const flags: { key: string; variant: 'clay' | 'warning' | 'default' }[] = []
  if (idle === 'due') flags.push({ key: 'inactive', variant: 'clay' })
  if (refill === 'due' && idle !== 'due') flags.push({ key: 'refillDue', variant: 'clay' })
  if (refill === 'soon') flags.push({ key: 'refillSoon', variant: 'warning' })
  if (customer.favouriteProductName) flags.push({ key: 'favourite', variant: 'default' })

  return (
    <button
      type="button"
      onClick={onOpen}
      className="focus-ring flex w-full items-center gap-4 rounded-card border border-line bg-surface-2 p-4 text-left shadow-soft transition-colors hover:border-line-strong hover:bg-surface-3"
    >
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="truncate text-sm font-semibold text-ink">{customer.name}</p>
          {customer.phone && <p className="shrink-0 text-xs text-ink-soft">{customer.phone}</p>}
        </div>

        <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-ink-soft">
          <span>
            {t('customers.row.purchases', { count: customer.totalOrders })}
          </span>
          {customer.lastPurchaseAt && (
            <span>
              · {t('customers.row.lastPurchase', { date: formatDate(customer.lastPurchaseAt, language) })}
            </span>
          )}
          {customer.daysSinceLastPurchase !== null && idle !== 'due' && (
            <span>· {t('customers.row.daysAgo', { days: customer.daysSinceLastPurchase })}</span>
          )}
        </div>

        {flags.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {flags.map((flag) => (
              <Badge key={flag.key} variant={flag.variant} size="sm" dot={flag.variant !== 'default'}>
                {t(`customers.row.${flag.key}`, {
                  product: customer.favouriteProductName ?? '',
                })}
              </Badge>
            ))}
          </div>
        )}
      </div>

      <div className="shrink-0 text-right">
        <p className="text-sm font-bold text-ink">{formatCurrency(customer.totalSpent)}</p>
        <p className="mt-0.5 text-[11px] text-ink-soft">{t('customers.row.totalSpent')}</p>
        {customer.predictedRefillDate && (
          <p className="mt-1 text-[11px] text-ink-soft">
            {t('customers.row.nextRefill', { date: formatDate(customer.predictedRefillDate, language) })}
          </p>
        )}
      </div>

      <Icon name="chevron-right" className="size-4 shrink-0 text-ink-soft" />
    </button>
  )
}
