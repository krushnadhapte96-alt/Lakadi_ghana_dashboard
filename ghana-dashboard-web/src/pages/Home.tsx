import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchDashboardSummary } from '@/api/dashboard'
import type { DashboardSummary, OilMixSlice } from '@/api/dashboard'
import { DonutChart } from '@/components/charts/DonutChart'
import { SalesAreaChart } from '@/components/charts/SalesAreaChart'
import { AlertStrip } from '@/components/home/AlertStrip'
import { CountUpCurrency, CountUpValue } from '@/components/home/CountUpValue'
import { GreetingHero } from '@/components/home/GreetingHero'
import { QuickActions } from '@/components/home/QuickActions'
import { Card, ErrorState, Skeleton, StatCard } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import { cn } from '@/utils/cn'
import { formatCurrency, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { oilColour } from '@/utils/oilColour'

/** Matches the backend's 60s summary cache; also refreshes when the tab regains focus. */
const REFRESH_MS = 60_000

export function HomePage() {
  const { t } = useLanguage()
  const userName = useAuthStore((s) => s.user?.name)
  const summary = useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: fetchDashboardSummary,
    refetchInterval: REFRESH_MS,
    refetchOnWindowFocus: true,
  })

  if (summary.isError) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6">
        <ErrorState
          icon={<Icon name="alert-circle" className="size-7" />}
          title={t('home.error.title')}
          message={t('home.error.body')}
          retryLabel={t('common.retry')}
          onRetry={() => void summary.refetch()}
          className="p-6"
        />
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-7xl space-y-6 px-4 py-6 sm:px-6">
      <GreetingHero name={userName ?? ''} />

      <QuickActions />

      {summary.isPending ? (
        <HomeSkeleton />
      ) : summary.data ? (
        <>
          <StatCards summary={summary.data} />
          <AlertStrip summary={summary.data} />
          <Charts summary={summary.data} />
        </>
      ) : null}

      {summary.data && (
        <p className="text-center text-xs text-ink-soft">
          {t('home.refreshNote')}
          <span className="mx-1.5" aria-hidden="true">
            ·
          </span>
          <Link to="/insights" className="focus-ring font-medium text-brand-700 hover:underline dark:text-brand-300">
            {t('home.seeInsights')}
          </Link>
        </p>
      )}
    </div>
  )
}

// ==================== cards ====================

function StatCards({ summary }: { summary: DashboardSummary }) {
  const { t } = useLanguage()

  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <StatCard
        icon={<Icon name="sales" className="size-5" />}
        label={t('home.card.todaySales')}
        value={<CountUpCurrency value={summary.todaySales.amount} />}
        trend={
          <span className="text-xs text-ink-soft">
            {t('home.card.billsLitres', {
              bills: summary.todaySales.bills,
              litres: formatQty(summary.todaySales.litres, 1),
            })}
          </span>
        }
        to="/sales"
        ariaLabel={t('home.card.todaySalesAria')}
      />

      <StatCard
        icon={<Icon name="inventory" className="size-5" />}
        label={t('home.card.lowStock')}
        value={<CountUpValue value={summary.lowStock.count} />}
        trend={
          summary.lowStock.count > 0 ? (
            <span className="line-clamp-2 text-xs text-clay-700 dark:text-clay-300">
              {summary.lowStock.top.map((item) => item.name).join(', ')}
            </span>
          ) : (
            <span className="text-xs text-brand-600 dark:text-brand-300">{t('home.card.allStocked')}</span>
          )
        }
        to="/inventory?filter=low"
        ariaLabel={t('home.card.lowStockAria')}
      />

      <StatCard
        icon={<Icon name="wallet" className="size-5" />}
        label={t('home.card.moneyToCollect')}
        value={<CountUpCurrency value={summary.moneyToCollect.amount} />}
        trend={
          <span className="text-xs text-ink-soft">
            {t('home.card.collectBreakdown', {
              bills: summary.moneyToCollect.unpaidBills,
              parties: summary.moneyToCollect.parties,
            })}
          </span>
        }
        to="/khata"
        ariaLabel={t('home.card.moneyToCollectAria')}
      />

      <StatCard
        icon={<Icon name="expiry" className="size-5" />}
        label={t('home.card.expiringSoon')}
        value={<CountUpValue value={summary.expiringSoon.count} />}
        trend={
          <span className="text-xs text-ink-soft">
            {summary.expiringSoon.count > 0
              ? t('home.card.qtyAtRisk', { qty: formatQty(summary.expiringSoon.qtyAtRisk, 1) })
              : t('home.card.noneExpiring')}
          </span>
        }
        to="/expiry-alerts"
        ariaLabel={t('home.card.expiringSoonAria')}
      />

      <StatCard
        icon={<Icon name="truck" className="size-5" />}
        label={t('home.card.pendingOrders')}
        value={<CountUpValue value={summary.pendingOrders} />}
        trend={<span className="text-xs text-ink-soft">{t('home.card.notDelivered')}</span>}
        to="/sales"
        ariaLabel={t('home.card.pendingOrdersAria')}
      />

      <StatCard
        icon={<Icon name="beej" className="size-5" />}
        label={t('home.card.seedStock')}
        value={
          <CountUpValue
            value={summary.seedStock.totalKg}
            format={(n) => `${formatQty(n, 1)} kg`}
          />
        }
        trend={
          <span className="text-xs text-ink-soft">
            {summary.seedStock.lowCount > 0
              ? t('home.card.seedTypesLow', {
                  count: summary.seedStock.seedTypes,
                  low: summary.seedStock.lowCount,
                })
              : t('home.card.seedTypes', { count: summary.seedStock.seedTypes })}
          </span>
        }
        to="/beej"
        ariaLabel={t('home.card.seedStockAria')}
      />

      <StatCard
        icon={<Icon name="licences" className="size-5" />}
        label={t('home.card.licenceAlerts')}
        value={
          <CountUpValue value={summary.licences.expired + summary.licences.expiringSoon} />
        }
        trend={
          <span className="text-xs text-ink-soft">
            {summary.licences.expired > 0 || summary.licences.expiringSoon > 0
              ? t('home.card.licenceBreakdown', {
                  expired: summary.licences.expired,
                  soon: summary.licences.expiringSoon,
                })
              : t('home.card.allLicencesValid')}
          </span>
        }
        to={licenceAlertHref(summary.licences)}
        ariaLabel={t('home.card.licenceAlertsAria')}
      />

      <StatCard
        icon={<Icon name="customers" className="size-5" />}
        label={t('home.card.refillDue')}
        value={<CountUpValue value={summary.refillDue.count} />}
        trend={
          summary.refillDue.count > 0 ? (
            <span className="line-clamp-2 text-xs text-ink-soft">
              {summary.refillDue.top.map((customer) => customer.name).join(', ')}
            </span>
          ) : (
            <span className="text-xs text-ink-soft">{t('home.card.noRefills')}</span>
          )
        }
        to="/customers"
        ariaLabel={t('home.card.refillDueAria')}
      />

      {/* Owner-only. The backend sends null for a staff account, so this card is not merely
          hidden for staff - the figure itself never exists in the response they get. */}
      {summary.monthNetProfit !== null && (
        <StatCard
          icon={<Icon name="pnl" className="size-5" />}
          label={t('home.card.monthProfit')}
          value={<CountUpCurrency value={summary.monthNetProfit} decimals={2} />}
          trend={
            <span
              className={cn(
                'text-xs font-medium',
                summary.monthNetProfit < 0 ? 'text-crimson-600' : 'text-brand-600 dark:text-brand-300',
              )}
            >
              {t('home.card.margin', { pct: formatQty(summary.monthMarginPercent ?? 0, 1) })}
            </span>
          }
          to="/pnl"
          ariaLabel={t('home.card.monthProfitAria')}
          iconClassName="bg-gold-50 text-gold-700 dark:bg-gold-500/20 dark:text-gold-300"
        />
      )}
    </div>
  )
}

/** Sends the owner to the tab that actually needs attention; nothing to chase means no filter. */
function licenceAlertHref(licences: DashboardSummary['licences']): string {
  if (licences.expired > 0) return '/licences?status=EXPIRED'
  if (licences.expiringSoon > 0) return '/licences?status=EXPIRING_SOON'
  return '/licences'
}

// ==================== charts ====================

function Charts({ summary }: { summary: DashboardSummary }) {
  const { t } = useLanguage()
  // DonutChart divides by the total, so a zero-sales window must not be handed to it.
  const oilSlices: OilMixSlice[] = summary.oilMix.filter((slice) => slice.amount > 0)
  const oilTotal = oilSlices.reduce((sum, slice) => sum + slice.amount, 0)

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
      <Card className="lg:col-span-3">
        <div className="mb-4 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-sm font-semibold text-ink">{t('home.chart.salesTitle')}</h2>
          <p className="text-xs text-ink-soft">
            {t('home.chart.seriesTotal', {
              amount: formatCurrency(
                summary.salesSeries.reduce((sum, point) => sum + point.amount, 0),
                { decimals: 0 },
              ),
            })}
          </p>
        </div>
        <SalesAreaChart points={summary.salesSeries} />
      </Card>

      <Card className="lg:col-span-2">
        <div className="mb-4 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-sm font-semibold text-ink">{t('home.chart.oilMix')}</h2>
          <p className="text-xs text-ink-soft">{t('home.chart.oilMixWindow')}</p>
        </div>
        {oilTotal > 0 ? (
          <DonutChart
            label={t('home.chart.oilMix')}
            slices={oilSlices.map((slice, index) => ({
              // oilTypeId is nullable for unclassified lines; fall back to the row position so
              // React keys stay unique and an "Other" bucket cannot collapse into one key.
              id: slice.oilTypeId ?? -(index + 1),
              label: slice.label,
              value: slice.amount,
              color: oilColour(slice.colourTag),
            }))}
          />
        ) : (
          <p className="py-8 text-center text-sm text-ink-soft">{t('home.chart.noOilSales')}</p>
        )}
      </Card>
    </div>
  )
}

function HomeSkeleton() {
  return (
    <>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 8 }, (_, i) => (
          <Skeleton key={i} className="h-24" />
        ))}
      </div>
      <Skeleton className="h-14" />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        <Skeleton className="h-72 lg:col-span-3" />
        <Skeleton className="h-72 lg:col-span-2" />
      </div>
    </>
  )
}
