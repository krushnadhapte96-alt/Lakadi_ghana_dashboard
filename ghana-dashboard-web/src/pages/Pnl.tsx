import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { exportPnlCsv, exportPnlPdf, fetchPnl } from '@/api/pnl'
import type { PnlGroup, PnlSummary } from '@/api/pnl'
import { saveBlob } from '@/api/download'
import { DateRangeControl } from '@/components/DateRangeControl'
import { TrendChart } from '@/components/charts/TrendChart'
import { Button, Card, EmptyState, ErrorState, PageHeader, Skeleton, StatCard } from '@/components/ui'
import { useCountUp } from '@/hooks/useCountUp'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { daysBetween, initialRange, isValidRange } from '@/utils/dateRange'
import type { DateRangeValue } from '@/utils/dateRange'
import { formatCurrency } from '@/utils/format'
import { Icon } from '@/utils/icons'

const MAX_DAILY_RANGE_DAYS = 62

function AnimatedAmount({ value }: { value: number }) {
  const animated = useCountUp(value)
  return <span className="tabular-nums">{formatCurrency(animated, { decimals: 2 })}</span>
}

function toneClasses(value: number): string {
  return value < 0
    ? 'bg-crimson-50 text-crimson-600 dark:bg-crimson-500/20 dark:text-crimson-300'
    : 'bg-brand-50 text-brand-600 dark:bg-brand-500/20 dark:text-brand-300'
}

function Margin({ pct, kind, negative }: { pct: number; kind: 'gross' | 'net'; negative: boolean }) {
  const { t } = useLanguage()
  return (
    <span
      className={cn(
        'text-xs font-semibold tabular-nums',
        negative ? 'text-crimson-600 dark:text-crimson-300' : 'text-brand-600 dark:text-brand-300',
      )}
    >
      {t(kind === 'gross' ? 'pnl.kpi.grossMargin' : 'pnl.kpi.netMargin', { pct: pct.toFixed(1) })}
    </span>
  )
}

function KpiGrid({ pnl }: { pnl: PnlSummary }) {
  const { t } = useLanguage()
  const cards = [
    { key: 'revenue', label: t('pnl.kpi.revenue'), value: pnl.totalRevenue, icon: 'trending-up' as const },
    { key: 'cogs', label: t('pnl.kpi.cogs'), value: pnl.totalCogs, icon: 'stock' as const },
    {
      key: 'gross',
      label: t('pnl.kpi.gross'),
      value: pnl.grossProfit,
      icon: 'pnl' as const,
      trend: <Margin pct={pnl.grossMarginPercent} kind="gross" negative={pnl.grossProfit < 0} />,
      tone: pnl.grossProfit,
    },
    { key: 'expenses', label: t('pnl.kpi.expenses'), value: pnl.totalExpenses, icon: 'expenses' as const },
    { key: 'losses', label: t('pnl.kpi.losses'), value: pnl.totalLosses, icon: 'trending-down' as const },
    {
      key: 'net',
      label: t('pnl.kpi.net'),
      value: pnl.netProfit,
      icon: 'payments' as const,
      trend: <Margin pct={pnl.netMarginPercent} kind="net" negative={pnl.netProfit < 0} />,
      tone: pnl.netProfit,
    },
  ]

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {cards.map((card) => (
        <StatCard
          key={card.key}
          icon={<Icon name={card.icon} className="size-5" />}
          label={card.label}
          value={<AnimatedAmount value={card.value} />}
          trend={card.trend}
          iconClassName={card.tone === undefined ? undefined : toneClasses(card.tone)}
          className={card.key === 'net' ? 'ring-1 ring-brand-500/30' : undefined}
        />
      ))}
    </div>
  )
}

function KpiSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {Array.from({ length: 6 }, (_, i) => (
        <Skeleton key={i} className="h-24" />
      ))}
    </div>
  )
}

function OilBreakdown({ pnl }: { pnl: PnlSummary }) {
  const { t } = useLanguage()
  const groups: PnlGroup[] = [...pnl.groups].sort((a, b) => b.revenue - a.revenue)
  const maxRevenue = Math.max(...groups.map((g) => g.revenue), 1)
  const hasGeneralRow = groups.some((g) => g.groupKey === 'unallocated')
  const numCell = 'px-3 py-2.5 text-right tabular-nums'
  const tone = (v: number) => (v < 0 ? 'text-crimson-600 dark:text-crimson-300' : 'text-ink')

  return (
    <div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[640px] text-sm">
          <thead>
            <tr className="border-b border-line text-xs text-ink-soft">
              <th scope="col" className="px-3 py-2 text-left font-medium">{t('pnl.oil.name')}</th>
              <th scope="col" className="px-3 py-2 text-left font-medium">{t('pnl.kpi.revenue')}</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">{t('pnl.kpi.cogs')}</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">{t('pnl.oil.gross')}</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">{t('pnl.kpi.expenses')}</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">{t('pnl.kpi.losses')}</th>
              <th scope="col" className="px-3 py-2 text-right font-medium">{t('pnl.kpi.net')}</th>
            </tr>
          </thead>
          <tbody>
            {groups.map((g) => (
              <tr key={g.groupKey} className="border-b border-line/60">
                <th scope="row" className="px-3 py-2.5 text-left font-medium text-ink">{g.groupLabel}</th>
                <td className="min-w-40 px-3 py-2.5">
                  <div className="flex flex-col gap-1">
                    <span className="tabular-nums text-ink">{formatCurrency(g.revenue)}</span>
                    <div className="h-1.5 w-full overflow-hidden rounded-pill bg-surface-3" aria-hidden="true">
                      <div
                        className="h-full rounded-pill bg-brand-500 dark:bg-brand-400"
                        style={{ width: `${(Math.max(g.revenue, 0) / maxRevenue) * 100}%` }}
                      />
                    </div>
                  </div>
                </td>
                <td className={cn(numCell, 'text-ink-soft')}>{formatCurrency(g.cogs)}</td>
                <td className={cn(numCell, tone(g.grossProfit))}>{formatCurrency(g.grossProfit)}</td>
                <td className={cn(numCell, 'text-ink-soft')}>{formatCurrency(g.expenses)}</td>
                <td className={cn(numCell, 'text-ink-soft')}>{formatCurrency(g.losses)}</td>
                <td className={cn(numCell, 'font-semibold', tone(g.netProfit))}>{formatCurrency(g.netProfit)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t-2 border-line-strong font-semibold">
              <th scope="row" className="px-3 py-2.5 text-left text-ink">{t('pnl.oil.total')}</th>
              <td className="px-3 py-2.5 tabular-nums text-ink">{formatCurrency(pnl.totalRevenue)}</td>
              <td className={numCell}>{formatCurrency(pnl.totalCogs)}</td>
              <td className={cn(numCell, tone(pnl.grossProfit))}>{formatCurrency(pnl.grossProfit)}</td>
              <td className={numCell}>{formatCurrency(pnl.totalExpenses)}</td>
              <td className={numCell}>{formatCurrency(pnl.totalLosses)}</td>
              <td className={cn(numCell, tone(pnl.netProfit))}>{formatCurrency(pnl.netProfit)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
      {hasGeneralRow && <p className="mt-3 text-xs text-ink-soft">{t('pnl.oil.unallocatedNote')}</p>}
    </div>
  )
}

export function PnlPage() {
  const { t } = useLanguage()
  const [dateRange, setDateRange] = useState<DateRangeValue>(initialRange)
  const [groupChoice, setGroupChoice] = useState<'day' | 'month'>('day')
  const [exporting, setExporting] = useState<'csv' | 'pdf' | null>(null)

  const { range } = dateRange
  const valid = isValidRange(range)
  const dailyAllowed = valid && daysBetween(range) <= MAX_DAILY_RANGE_DAYS
  const groupBy: 'day' | 'month' = groupChoice === 'day' && dailyAllowed ? 'day' : 'month'

  const trend = useQuery({
    queryKey: ['pnl', 'trend', range.from, range.to, groupBy],
    queryFn: () => fetchPnl({ from: range.from, to: range.to, groupBy }),
    enabled: valid,
    placeholderData: keepPreviousData,
  })
  const oil = useQuery({
    queryKey: ['pnl', 'oilType', range.from, range.to],
    queryFn: () => fetchPnl({ from: range.from, to: range.to, groupBy: 'oilType' }),
    enabled: valid,
    placeholderData: keepPreviousData,
  })

  const pnl = trend.data
  const isEmpty =
    pnl !== undefined &&
    pnl.totalRevenue === 0 &&
    pnl.totalCogs === 0 &&
    pnl.totalExpenses === 0 &&
    pnl.totalLosses === 0

  async function runExport(kind: 'csv' | 'pdf') {
    const query = { from: range.from, to: range.to, groupBy }
    setExporting(kind)
    try {
      const blob = kind === 'csv' ? await exportPnlCsv(query) : await exportPnlPdf(query)
      saveBlob(blob, `pnl_${range.from}_to_${range.to}.${kind}`)
    } catch {
      toast.error(t('pnl.export.failed'))
    } finally {
      setExporting(null)
    }
  }

  const exportLabel = (kind: 'csv' | 'pdf') =>
    exporting === kind ? t('pnl.export.preparing') : t(kind === 'csv' ? 'pnl.export.csv' : 'pnl.export.pdf')
  const exportDisabled = !valid || !pnl || exporting !== null

  return (
    <div>
      <PageHeader
        title={t('pnl.title')}
        subtitle={t('pnl.subtitle')}
        actions={
          <>
            <Button variant="secondary" size="sm" disabled={exportDisabled} onClick={() => void runExport('csv')}>
              <Icon name="download" className="size-4" />
              {exportLabel('csv')}
            </Button>
            <Button variant="secondary" size="sm" disabled={exportDisabled} onClick={() => void runExport('pdf')}>
              <Icon name="file" className="size-4" />
              {exportLabel('pdf')}
            </Button>
          </>
        }
      />

      <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <DateRangeControl value={dateRange} onChange={setDateRange} />
        <div className="flex items-center gap-2" role="group" aria-label={t('pnl.groupBy')}>
          <span className="text-sm text-ink-soft">{t('pnl.groupBy')}</span>
          {(['day', 'month'] as const).map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={groupBy === option}
              disabled={option === 'day' && !dailyAllowed}
              onClick={() => setGroupChoice(option)}
              className={cn(
                'focus-ring rounded-pill border px-3.5 py-1.5 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50',
                groupBy === option
                  ? 'border-brand-600 bg-brand-600 text-white'
                  : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
              )}
            >
              {t(option === 'day' ? 'pnl.group.day' : 'pnl.group.month')}
            </button>
          ))}
        </div>
      </div>

      {!valid ? null : trend.isError ? (
        <Card>
          <ErrorState
            icon={<Icon name="alert-circle" className="size-7" />}
            title={t('pnl.error.title')}
            message={t('expenses.error.body')}
            retryLabel={t('common.retry')}
            onRetry={() => {
              void trend.refetch()
              void oil.refetch()
            }}
          />
        </Card>
      ) : !pnl ? (
        <div className="space-y-6">
          <KpiSkeleton />
          <Skeleton className="h-72" />
        </div>
      ) : isEmpty ? (
        <Card>
          <EmptyState
            icon={<Icon name="pnl" className="size-7" />}
            title={t('pnl.empty.title')}
            description={t('pnl.empty.body')}
          />
        </Card>
      ) : (
        <div className="space-y-6">
          <KpiGrid pnl={pnl} />
          <p className="text-xs text-ink-soft">{t('pnl.reconcile')}</p>

          <Card className="p-4">
            <h2 className="mb-3 text-base font-semibold text-ink">{t('pnl.trend.title')}</h2>
            <TrendChart groups={pnl.groups} />
          </Card>

          <Card className="p-4">
            <h2 className="mb-3 text-base font-semibold text-ink">{t('pnl.oil.title')}</h2>
            {oil.isError ? (
              <ErrorState
                title={t('pnl.error.title')}
                message={t('expenses.error.body')}
                retryLabel={t('common.retry')}
                onRetry={() => void oil.refetch()}
                className="py-6"
              />
            ) : oil.data ? (
              <OilBreakdown pnl={oil.data} />
            ) : (
              <Skeleton className="h-40" />
            )}
          </Card>
        </div>
      )}
    </div>
  )
}

