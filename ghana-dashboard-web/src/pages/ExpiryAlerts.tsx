import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchExpiryAlerts } from '@/api/expiryAlerts'
import type { ExpiryAlertItem, ExpiryBucket } from '@/api/expiryAlerts'
import { BatchDetailModal } from '@/components/expiry/BatchDetailModal'
import { DiscountModal } from '@/components/expiry/DiscountModal'
import { SnoozeModal } from '@/components/expiry/SnoozeModal'
import { WriteOffModal } from '@/components/expiry/WriteOffModal'
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, Skeleton, Tabs } from '@/components/ui'
import type { TabItem } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency, formatDate, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'

const URGENT: ExpiryBucket[] = ['EXPIRED', 'D7']

function AlertCard({
  item,
  onDiscount,
  onWriteOff,
  onSnooze,
  onView,
}: {
  item: ExpiryAlertItem
  onDiscount: () => void
  onWriteOff: () => void
  onSnooze: () => void
  onView: () => void
}) {
  const { t, language } = useLanguage()
  const urgent = URGENT.includes(item.bucket)

  return (
    <Card
      className={cn(
        'space-y-3 p-4',
        urgent && 'ring-1 ring-clay-400 animate-pulse-soft motion-reduce:animate-none',
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-medium text-ink">{item.productName}</p>
          <p className="text-xs text-ink-soft">{t('expiry.card.batch', { code: item.batchId })}</p>
        </div>
        <Badge variant={item.bucket === 'EXPIRED' ? 'danger' : urgent ? 'clay' : 'warning'}>
          {item.bucket === 'EXPIRED'
            ? t('expiry.bucket.expiredDays', { count: Math.abs(item.daysLeft) })
            : t('expiry.bucket.daysLeft', { count: item.daysLeft })}
        </Badge>
      </div>

      <div className="grid grid-cols-2 gap-2 text-sm">
        <div>
          <p className="text-ink-soft">{t('expiry.card.qty')}</p>
          <p className="font-medium tabular-nums text-ink">{formatQty(item.qtyAvailable)} {item.unit}</p>
        </div>
        <div>
          <p className="text-ink-soft">{t('expiry.card.valueAtRisk')}</p>
          <p className="font-semibold tabular-nums text-ink">{formatCurrency(item.valueAtRisk)}</p>
        </div>
      </div>

      <p className="text-xs text-ink-soft">
        {t('expiry.card.expiresOn', { date: formatDate(item.expiryDate, language) })}
      </p>

      {item.activeDiscountPct !== null && (
        <Badge variant="clay" size="sm">
          {t('expiry.card.discountActive', { pct: item.activeDiscountPct })}
        </Badge>
      )}

      <div className="flex flex-wrap gap-2 pt-1">
        <Button size="sm" variant="secondary" onClick={onDiscount}>
          {t('expiry.actions.discount')}
        </Button>
        <Button size="sm" variant="secondary" onClick={onWriteOff}>
          {t('expiry.actions.writeOff')}
        </Button>
        <Button size="sm" variant="ghost" onClick={onSnooze}>
          {t('expiry.actions.snooze')}
        </Button>
        <Button size="sm" variant="ghost" onClick={onView}>
          {t('expiry.actions.view')}
        </Button>
      </div>
    </Card>
  )
}

export function ExpiryAlertsPage() {
  const { t } = useLanguage()
  const [activeBucket, setActiveBucket] = useState<ExpiryBucket>('EXPIRED')
  const [discountItem, setDiscountItem] = useState<ExpiryAlertItem | null>(null)
  const [writeOffItem, setWriteOffItem] = useState<ExpiryAlertItem | null>(null)
  const [snoozeItem, setSnoozeItem] = useState<ExpiryAlertItem | null>(null)
  const [viewItem, setViewItem] = useState<ExpiryAlertItem | null>(null)

  const alerts = useQuery({ queryKey: ['expiry-alerts'], queryFn: fetchExpiryAlerts })

  const tabs: TabItem[] = useMemo(() => {
    const bucketLabels: Record<ExpiryBucket, string> = {
      EXPIRED: t('expiry.bucket.expired'),
      D7: t('expiry.bucket.d7'),
      D15: t('expiry.bucket.d15'),
      D30: t('expiry.bucket.d30'),
    }
    return (['EXPIRED', 'D7', 'D15', 'D30'] as const).map((bucket) => ({
      id: bucket,
      label: bucketLabels[bucket],
      count: alerts.data?.buckets.find((b) => b.bucket === bucket)?.items.length ?? 0,
    }))
  }, [alerts.data, t])

  const activeItems = alerts.data?.buckets.find((b) => b.bucket === activeBucket)?.items ?? []

  return (
    <div>
      <PageHeader title={t('expiry.title')} subtitle={t('expiry.subtitle')} />

      {alerts.isError ? (
        <Card>
          <ErrorState
            icon={<Icon name="alert-circle" className="size-7" />}
            title={t('expiry.error.title')}
            message={t('expiry.error.body')}
            retryLabel={t('common.retry')}
            onRetry={() => void alerts.refetch()}
            className="p-6"
          />
        </Card>
      ) : !alerts.data ? (
        <div className="space-y-4">
          <Skeleton className="h-10" />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="h-44" />
            ))}
          </div>
        </div>
      ) : (
        <>
          <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-4 text-sm text-ink-soft">
              <span>
                {t('expiry.summary.units')}{' '}
                <span className="font-semibold text-ink">{formatQty(alerts.data.totalUnitsAtRisk)}</span>
              </span>
              <span>
                {t('expiry.summary.value')}{' '}
                <span className="font-semibold text-ink">{formatCurrency(alerts.data.totalValueAtRisk)}</span>
              </span>
            </div>
          </div>

          <Tabs tabs={tabs} activeTab={activeBucket} onTabChange={(id) => setActiveBucket(id as ExpiryBucket)} className="mb-6" />

          {activeItems.length === 0 ? (
            <Card>
              <EmptyState
                icon={<Icon name="expiry" className="size-7" />}
                title={t('expiry.empty.title')}
                description={t('expiry.empty.body')}
                className="p-6"
              />
            </Card>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {activeItems.map((item) => (
                <AlertCard
                  key={item.batchStockId}
                  item={item}
                  onDiscount={() => setDiscountItem(item)}
                  onWriteOff={() => setWriteOffItem(item)}
                  onSnooze={() => setSnoozeItem(item)}
                  onView={() => setViewItem(item)}
                />
              ))}
            </div>
          )}
        </>
      )}

      <DiscountModal item={discountItem} onClose={() => setDiscountItem(null)} />
      <WriteOffModal item={writeOffItem} onClose={() => setWriteOffItem(null)} />
      <SnoozeModal item={snoozeItem} onClose={() => setSnoozeItem(null)} />
      <BatchDetailModal item={viewItem} onClose={() => setViewItem(null)} />
    </div>
  )
}

