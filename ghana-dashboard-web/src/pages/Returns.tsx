import { useCallback, useMemo, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchReturns, fetchReturnStats, RETURN_STATUSES, RETURN_TYPES } from '@/api/returns'
import type { ReturnStatus, ReturnType } from '@/api/returns'
import { DateRangeControl } from '@/components/DateRangeControl'
import { DonutChart } from '@/components/charts/DonutChart'
import { NewReturnDrawer } from '@/components/returns/NewReturnDrawer'
import { ResolveDrawer } from '@/components/returns/ResolveDrawer'
import { ReturnCard } from '@/components/returns/ReturnCard'
import { ReturnDetailDrawer } from '@/components/returns/ReturnDetailDrawer'
import { TYPE_COLOUR } from '@/components/returns/helpers'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  PageHeader,
  Skeleton,
  StatCard,
  Tabs,
} from '@/components/ui'
import type { TabItem } from '@/components/ui'
import { useCountUp } from '@/hooks/useCountUp'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { initialRange, isValidRange } from '@/utils/dateRange'
import type { DateRangeValue } from '@/utils/dateRange'
import { formatCurrency, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'

type StatusTab = ReturnStatus | 'ALL'

function StatValue({ value }: { value: number }) {
  const animated = useCountUp(value)
  return <span className="tabular-nums">{formatQty(animated, 0)}</span>
}

export function ReturnsPage() {
  const { t } = useLanguage()
  const [dateRange, setDateRange] = useState<DateRangeValue>(initialRange)
  const [statusTab, setStatusTab] = useState<StatusTab>('ALL')
  const [typeFilter, setTypeFilter] = useState<ReturnType | ''>('')
  const [detailId, setDetailId] = useState<number | null>(null)
  const [resolveId, setResolveId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)

  const { range } = dateRange
  const valid = isValidRange(range)

  const returns = useQuery({
    queryKey: ['returns', 'list', range.from, range.to],
    queryFn: () => fetchReturns({ dateFrom: range.from, dateTo: range.to }),
    enabled: valid,
    placeholderData: keepPreviousData,
  })

  const stats = useQuery({
    queryKey: ['returns', 'stats', range.from, range.to],
    queryFn: () => fetchReturnStats(range.from, range.to),
    enabled: valid,
    placeholderData: keepPreviousData,
  })

  const closeDetail = useCallback(() => setDetailId(null), [])

  const all = useMemo(() => returns.data ?? [], [returns.data])
  const byStatus = useMemo(() => {
    const counts: Record<StatusTab, number> = {
      ALL: all.length,
      OPEN: 0,
      IN_REVIEW: 0,
      RESOLVED: 0,
    }
    for (const item of all) counts[item.status] += 1
    return counts
  }, [all])

  const tabs: TabItem[] = useMemo(
    () => [
      { id: 'ALL', label: t('returns.tabs.all'), count: byStatus.ALL },
      ...RETURN_STATUSES.map((status) => ({
        id: status,
        label: t(`returns.status.${status}`),
        count: byStatus[status],
      })),
    ],
    [byStatus, t],
  )

  const visible = all.filter(
    (item) =>
      (statusTab === 'ALL' || item.status === statusTab) &&
      (typeFilter === '' || item.type === typeFilter),
  )

  const totals = useMemo(() => {
    const openValue = all
      .filter((item) => item.status === 'OPEN' || item.status === 'IN_REVIEW')
      .reduce((sum, item) => sum + item.totalValue, 0)
    const refunded = all
      .filter((item) => item.resolution === 'REFUND')
      .reduce((sum, item) => sum + item.totalValue, 0)
    return { openValue, refunded }
  }, [all])

  // DonutChart keys slices by number, so map each complaint type to its index.
  const slices = (stats.data?.byType ?? [])
    .filter((row) => row.count > 0)
    .map((row) => ({
      id: RETURN_TYPES.indexOf(row.type),
      label: t(`returns.type.${row.type}`),
      value: row.count,
      color: TYPE_COLOUR[row.type],
    }))

  const topBatches = stats.data?.byBatch ?? []
  const worstBatchCount = topBatches[0]?.count ?? 1

  return (
    <div>
      <PageHeader
        title={t('returns.title')}
        subtitle={t('returns.subtitle')}
        actions={
          <Button onClick={() => setCreating(true)}>
            <Icon name="plus" className="size-4" />
            {t('returns.new.title')}
          </Button>
        }
      />

      <div className="mb-6">
        <DateRangeControl value={dateRange} onChange={setDateRange} />
      </div>

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard
          icon={<Icon name="returns" className="size-5" />}
          label={t('returns.stat.total')}
          value={<StatValue value={all.length} />}
        />
        <StatCard
          icon={<Icon name="alert-circle" className="size-5" />}
          label={t('returns.stat.open')}
          value={<StatValue value={byStatus.OPEN} />}
          iconClassName="bg-crimson-50 text-crimson-600 dark:bg-crimson-500/20 dark:text-crimson-300"
        />
        <StatCard
          icon={<Icon name="clock" className="size-5" />}
          label={t('returns.stat.inReview')}
          value={<StatValue value={byStatus.IN_REVIEW} />}
          iconClassName="bg-amber-50 text-amber-600 dark:bg-amber-500/20 dark:text-amber-300"
        />
        <StatCard
          icon={<Icon name="wallet" className="size-5" />}
          label={t('returns.stat.refunded')}
          value={formatCurrency(totals.refunded, { decimals: 0 })}
          iconClassName="bg-gold-50 text-gold-700 dark:bg-gold-500/20 dark:text-gold-300"
        />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <div className="space-y-6">
          <Card className="self-start p-4">
            <h2 className="mb-4 text-base font-semibold text-ink">{t('returns.byType')}</h2>
            {stats.isError ? (
              <ErrorState
                title={t('returns.error.title')}
                message={t('returns.error.body')}
                retryLabel={t('common.retry')}
                onRetry={() => void stats.refetch()}
                className="py-6"
              />
            ) : !stats.data ? (
              <Skeleton className="h-52" />
            ) : slices.length === 0 ? (
              <EmptyState
                icon={<Icon name="returns" className="size-7" />}
                title={t('returns.empty.title')}
                className="py-6"
              />
            ) : (
              <DonutChart slices={slices} label={t('returns.byType')} />
            )}
          </Card>

          <Card className="p-4">
            <h2 className="mb-1 text-base font-semibold text-ink">{t('returns.topBatches.title')}</h2>
            <p className="mb-4 text-xs text-ink-soft">{t('returns.topBatches.subtitle')}</p>
            {!stats.data ? (
              <Skeleton className="h-32" />
            ) : topBatches.length === 0 ? (
              <EmptyState
                icon={<Icon name="stock" className="size-7" />}
                title={t('returns.topBatches.empty')}
                className="py-6"
              />
            ) : (
              <ol className="space-y-2.5">
                {topBatches.map((batch) => (
                  <li key={batch.batchId}>
                    <div className="flex items-baseline justify-between gap-3 text-sm">
                      <span className="min-w-0 truncate font-medium text-ink">
                        {batch.productName}
                      </span>
                      <span className="shrink-0 tabular-nums text-ink-soft">
                        {t('returns.topBatches.count', { count: batch.count })}
                      </span>
                    </div>
                    <div className="mt-1 flex items-center gap-2">
                      <div className="h-2 flex-1 overflow-hidden rounded-pill bg-surface-3">
                        <div
                          className="h-full rounded-pill bg-crimson-500 dark:bg-crimson-400"
                          style={{ width: `${Math.max(8, (batch.count / worstBatchCount) * 100)}%` }}
                        />
                      </div>
                      <span className="w-24 shrink-0 text-right text-xs tabular-nums text-ink-soft">
                        {formatCurrency(batch.totalValue, { decimals: 0 })}
                      </span>
                    </div>
                    <p className="mt-0.5 truncate text-xs text-ink-soft">{batch.batchId}</p>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>

        <Card className="p-4">
          <Tabs
            tabs={tabs}
            activeTab={statusTab}
            onTabChange={(id) => setStatusTab(id as StatusTab)}
            className="mb-4"
          />

          <div
            className="-mx-1 mb-4 flex gap-2 overflow-x-auto px-1 pb-1"
            role="group"
            aria-label={t('returns.typeFilter')}
          >
            <button
              type="button"
              aria-pressed={typeFilter === ''}
              onClick={() => setTypeFilter('')}
              className={cn(
                'focus-ring shrink-0 rounded-pill border px-3 py-1.5 text-sm transition-colors',
                typeFilter === ''
                  ? 'border-brand-600 bg-brand-600 text-white'
                  : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
              )}
            >
              {t('returns.allTypes')}
            </button>
            {RETURN_TYPES.map((type) => (
              <button
                key={type}
                type="button"
                aria-pressed={typeFilter === type}
                onClick={() => setTypeFilter(typeFilter === type ? '' : type)}
                className={cn(
                  'focus-ring inline-flex shrink-0 items-center gap-2 rounded-pill border px-3 py-1.5 text-sm transition-colors',
                  typeFilter === type
                    ? 'border-brand-600 bg-brand-600 text-white'
                    : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                )}
              >
                <span
                  className="size-2.5 rounded-full ring-1 ring-white/70 dark:ring-black/30"
                  style={{ backgroundColor: TYPE_COLOUR[type] }}
                  aria-hidden="true"
                />
                {t(`returns.type.${type}`)}
              </button>
            ))}
          </div>

          {returns.isError ? (
            <ErrorState
              icon={<Icon name="alert-circle" className="size-7" />}
              title={t('returns.error.title')}
              message={t('returns.error.body')}
              retryLabel={t('common.retry')}
              onRetry={() => void returns.refetch()}
            />
          ) : !returns.data ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {Array.from({ length: 4 }, (_, index) => (
                <Skeleton key={index} className="h-52" />
              ))}
            </div>
          ) : visible.length === 0 ? (
            <EmptyState
              icon={<Icon name="returns" className="size-7" />}
              title={t('returns.empty.title')}
              description={t('returns.empty.body')}
              action={
                <Button size="sm" onClick={() => setCreating(true)}>
                  <Icon name="plus" className="size-4" />
                  {t('returns.new.title')}
                </Button>
              }
            />
          ) : (
            <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {visible.map((item) => (
                <li key={item.id}>
                  <ReturnCard item={item} onOpen={setDetailId} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <NewReturnDrawer open={creating} onClose={() => setCreating(false)} />

      <ReturnDetailDrawer
        returnId={detailId}
        onClose={closeDetail}
        onResolve={(id) => {
          setDetailId(null)
          setResolveId(id)
        }}
      />

      <ResolveDrawer returnId={resolveId} onClose={() => setResolveId(null)} />
    </div>
  )
}

