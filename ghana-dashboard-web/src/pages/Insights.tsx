import { useDeferredValue, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchCustomers, CUSTOMER_TABS, type CustomerListItem, type CustomerTab } from '@/api/customers'
import { fetchDashboardSummary } from '@/api/dashboard'
import { Badge, Card, EmptyState, ErrorState, Input, PageHeader, Skeleton } from '@/components/ui'
import { cn } from '@/utils/cn'
import { formatCurrency, formatDate, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { useLanguage } from '@/i18n'

const TAB_LABELS: Record<CustomerTab, string> = {
  all: 'All customers',
  refill: 'Refill due',
  top: 'Top spenders',
  inactive: 'Inactive',
}

export function InsightsPage() {
  const { language } = useLanguage()
  const [tab, setTab] = useState<CustomerTab>('all')
  const [q, setQ] = useState('')
  const deferredQ = useDeferredValue(q)

  const summary = useQuery({ queryKey: ['dashboard'], queryFn: fetchDashboardSummary, staleTime: 60_000 })
  const customers = useQuery({
    queryKey: ['customers', tab, deferredQ],
    queryFn: () => fetchCustomers({ tab, q: deferredQ }),
    placeholderData: keepPreviousData,
  })

  const data = summary.data

  return (
    <div>
      <PageHeader title="Insights" subtitle="What's happening across your shop right now" />

      {summary.isPending ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-28" />)}
        </div>
      ) : summary.isError || !data ? (
        <ErrorState icon={<Icon name="alert-circle" className="size-7" />} title="Could not load insights" message="The dashboard feed is unavailable." retryLabel="Retry" onRetry={() => void summary.refetch()} />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <Kpi label="Today's sales" icon="sales" value={formatCurrency(data.todaySales.amount)} sub={`${formatQty(data.todaySales.litres)} L · ${data.todaySales.bills} bills`} />
            <Kpi label="Unpaid bills" icon="wallet" value={formatCurrency(data.moneyToCollect.amount)} sub={`${data.moneyToCollect.unpaidBills} bills · ${data.moneyToCollect.parties} parties`} tone={data.moneyToCollect.amount > 0 ? 'warn' : 'ok'} />
            <Kpi label="Pending orders" icon="truck" value={String(data.pendingOrders)} sub="to be delivered" />
            <Kpi label="Low stock" icon="stock" value={String(data.lowStock.count)} sub="products below threshold" tone={data.lowStock.count > 0 ? 'warn' : 'ok'} />
            <Kpi label="Expiring soon" icon="expiry" value={String(data.expiringSoon.count)} sub={data.expiringSoon.nearestExpiry ? `nearest ${formatDate(data.expiringSoon.nearestExpiry, language)}` : 'nothing at risk'} tone={data.expiringSoon.count > 0 ? 'warn' : 'ok'} />
            {data.monthNetProfit !== null ? (
              <Kpi label="Month net profit" icon="trending-up" value={formatCurrency(data.monthNetProfit)} sub={`margin ${data.monthMarginPercent ?? 0}%`} tone={data.monthNetProfit >= 0 ? 'ok' : 'warn'} />
            ) : (
              <Kpi label="Month net profit" icon="trending-up" value="—" sub="owner-only metric" />
            )}
          </div>

          <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
            <SalesTrend points={data.salesSeries} language={language} />
            <OilMix slices={data.oilMix} />
          </div>

          <Card className="mt-6">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-3">
              <div className="flex gap-1 overflow-x-auto">
                {CUSTOMER_TABS.map((cTab) => (
                  <button
                    key={cTab}
                    type="button"
                    onClick={() => setTab(cTab)}
                    className={cn(
                      'whitespace-nowrap border-b-2 px-3 py-1 text-sm font-medium transition-colors',
                      tab === cTab ? 'border-brand-500 text-brand-600' : 'border-transparent text-ink-soft hover:text-ink',
                    )}
                  >
                    {TAB_LABELS[cTab]}
                  </button>
                ))}
              </div>
              <div className="w-56">
                <Input type="search" placeholder="Search customers…" value={q} onChange={(e) => setQ(e.target.value)} />
              </div>
            </div>

            <CustomerTable
              rows={customers.data ?? []}
              isPending={customers.isPending && !customers.data}
              isError={customers.isError}
              onRetry={() => void customers.refetch()}
              language={language}
            />
          </Card>
        </>
      )}
    </div>
  )
}

function Kpi({ label, icon, value, sub, tone = 'ok' }: { label: string; icon: 'sales' | 'wallet' | 'truck' | 'stock' | 'expiry' | 'trending-up'; value: string; sub: string; tone?: 'ok' | 'warn' }) {
  return (
    <Card className="flex items-start gap-3 p-4">
      <div className={cn('rounded-card p-2.5', tone === 'warn' ? 'bg-amber-50 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300' : 'bg-brand-50 text-brand-700 dark:bg-brand-500/20 dark:text-brand-300')}>
        <Icon name={icon} className="size-5" />
      </div>
      <div className="min-w-0">
        <p className="text-xs text-ink-soft">{label}</p>
        <p className="mt-0.5 text-lg font-bold tabular-nums text-ink">{value}</p>
        <p className="mt-0.5 truncate text-xs text-ink-soft">{sub}</p>
      </div>
    </Card>
  )
}

function SalesTrend({ points, language }: { points: import('@/api/dashboard').SalesPoint[]; language: 'en' | 'hi' | 'mr' }) {
  const data = points.slice(-7)
  const max = Math.max(...data.map((p) => p.amount), 1)
  if (data.length === 0) {
    return <Card className="p-6"><EmptyState icon={<Icon name="sales" className="size-7" />} title="No sales yet" description="Sales from the last days will appear here." className="py-2" /></Card>
  }
  return (
    <Card className="p-4">
      <h3 className="text-sm font-semibold text-ink">Sales trend · last 7 days</h3>
      <div className="mt-4 flex h-40 items-end justify-between gap-2">
        {data.map((point) => (
          <div key={point.date} className="group flex h-full flex-1 flex-col items-center justify-end gap-1">
            <p className="text-[10px] tabular-nums text-ink-soft opacity-0 transition-opacity group-hover:opacity-100">{formatCurrency(point.amount)}</p>
            <div
              className="w-full rounded-t bg-gradient-to-b from-brand-400 to-brand-600 transition-opacity group-hover:opacity-80"
              style={{ height: `${Math.max((point.amount / max) * 100, 3)}%` }}
              title={`${formatDate(point.date, language)} · ${formatCurrency(point.amount)} · ${point.bills} bills`}
            />
            <p className="text-[10px] text-ink-soft">{formatDate(point.date, language, { day: 'numeric', month: 'short' })}</p>
          </div>
        ))}
      </div>
    </Card>
  )
}

function OilMix({ slices }: { slices: import('@/api/dashboard').OilMixSlice[] }) {
  const total = slices.reduce((sum, s) => sum + s.amount, 0)
  if (slices.length === 0) {
    return <Card className="p-6"><EmptyState icon={<Icon name="scale" className="size-7" />} title="No sales mixed yet" description="Your product mix by oil type will appear here." className="py-2" /></Card>
  }
  return (
    <Card className="p-4">
      <h3 className="text-sm font-semibold text-ink">Product mix by oil type</h3>
      <ul className="mt-4 space-y-3">
        {slices.map((slice) => {
          const share = total > 0 ? (slice.amount / total) * 100 : 0
          return (
            <li key={slice.oilTypeId ?? 'null'}>
              <div className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 text-ink">
                  <span className="size-2.5 rounded-full" style={{ backgroundColor: slice.colourTag || '#667085' }} />
                  {slice.label}
                </span>
                <span className="tabular-nums text-ink-soft">{formatCurrency(slice.amount)} · {share.toFixed(0)}%</span>
              </div>
              <div className="mt-1.5 h-2 overflow-hidden rounded-pill bg-surface-3">
                <div className="h-full rounded-pill" style={{ width: `${share}%`, backgroundColor: slice.colourTag || '#0EA5E9' }} />
              </div>
            </li>
          )
        })}
      </ul>
    </Card>
  )
}

function CustomerTable({ rows, isPending, isError, onRetry, language }: { rows: CustomerListItem[]; isPending: boolean; isError: boolean; onRetry: () => void; language: 'en' | 'hi' | 'mr' }) {
  if (isPending) {
    return <div className="divide-y divide-line">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="mx-4 my-3 h-12" />)}</div>
  }
  if (isError) {
    return <ErrorState icon={<Icon name="alert-circle" className="size-7" />} title="Could not load customers" message="The customer feed is unavailable." retryLabel="Retry" onRetry={onRetry} className="py-6" />
  }
  if (rows.length === 0) {
    return <EmptyState icon={<Icon name="customers" className="size-7" />} title="No customers here" description="Customers matching this view will appear here." className="py-8" />
  }
  return (
    <ul className="divide-y divide-line">
      {rows.map((customer) => (
        <li key={customer.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-ink">{customer.name}</p>
            <p className="mt-0.5 text-xs text-ink-soft">
              {customer.phone ?? '—'}
              {customer.favouriteProductName ? ` · likes ${customer.favouriteProductName}` : ''}
            </p>
          </div>
          {customer.refillDue && <Badge size="sm" variant="warning" dot>Refill due</Badge>}
          <p className="shrink-0 text-sm text-ink-soft">
            last {customer.daysSinceLastPurchase !== null ? `${customer.daysSinceLastPurchase}d ago` : 'never'}
            {customer.predictedRefillDate ? ` · ETA ${formatDate(customer.predictedRefillDate, language)}` : ''}
          </p>
          <p className="shrink-0 text-sm font-bold tabular-nums text-ink">{formatCurrency(customer.totalSpent)}</p>
        </li>
      ))}
    </ul>
  )
}