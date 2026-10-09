import { Badge, Card } from '@/components/ui'
import { PhotoStrip } from '@/components/returns/PhotoStrip'
import { ResolutionBadge, StatusBadge, TypeChip } from '@/components/returns/TypeChip'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import type { ReturnSummary } from '@/api/returns'

export function ReturnCard({
  item,
  onOpen,
}: {
  item: ReturnSummary
  onOpen: (id: number) => void
}) {
  const { t, language } = useLanguage()
  const photos = item.photoIds.map((photoId, index) => ({
    id: photoId,
    fileName: t('returns.photo.name', { index: index + 1 }),
    fileSize: 0,
    fileType: 'image/*',
  }))

  return (
    <Card
      className={cn(
        'flex flex-col gap-3 p-4 transition-shadow hover:shadow-card',
        item.status === 'OPEN' && 'ring-1 ring-crimson-400',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <button
            type="button"
            onClick={() => onOpen(item.id)}
            className="focus-ring truncate text-left font-semibold text-ink hover:text-brand-600 dark:hover:text-brand-300"
          >
            {item.returnNo}
          </button>
          <p className="mt-0.5 truncate text-xs text-ink-soft">
            {t('returns.card.bill', { bill: item.sourceBillNo })}
            {item.customerName && (
              <>
                <span aria-hidden="true"> · </span>
                {item.customerName}
              </>
            )}
          </p>
        </div>
        <StatusBadge status={item.status} />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <TypeChip type={item.type} />
        <Badge variant="secondary">
          <Icon name="clock" className="size-3" aria-hidden="true" />
          {formatDate(item.createdAt, language)}
        </Badge>
        {item.resolution && <ResolutionBadge resolution={item.resolution} />}
      </div>

      <dl className="grid grid-cols-2 gap-2 text-sm">
        <div>
          <dt className="text-xs text-ink-soft">{t('returns.card.items')}</dt>
          <dd className="font-semibold tabular-nums text-ink">{item.itemCount}</dd>
        </div>
        <div>
          <dt className="text-xs text-ink-soft">{t('returns.card.value')}</dt>
          <dd className="font-semibold tabular-nums text-ink">
            {formatCurrency(item.totalValue, { decimals: 2 })}
          </dd>
        </div>
      </dl>

      <div className="flex items-end justify-between gap-3 pt-1">
        <PhotoStrip returnId={item.id} photos={photos} />
        <button
          type="button"
          onClick={() => onOpen(item.id)}
          className="focus-ring inline-flex shrink-0 items-center gap-1.5 rounded-pill px-2.5 py-1.5 text-xs font-semibold text-brand-600 transition-colors hover:bg-surface-3 dark:text-brand-300"
        >
          {t('returns.actions.open')}
          <Icon name="chevron-right" className="size-3.5" aria-hidden="true" />
        </button>
      </div>

      {item.status === 'OPEN' && (
        <p className="flex items-center gap-1.5 rounded-card bg-crimson-50 px-2.5 py-1.5 text-xs font-medium text-crimson-700 dark:bg-crimson-500/15 dark:text-crimson-300">
          <Icon name="alert-circle" className="size-3.5 shrink-0" aria-hidden="true" />
          {t('returns.card.needsReview')}
        </p>
      )}
      {item.status === 'IN_REVIEW' && (
        <p className="flex items-center gap-1.5 rounded-card bg-amber-50 px-2.5 py-1.5 text-xs font-medium text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
          <Icon name="clock" className="size-3.5 shrink-0" aria-hidden="true" />
          {t('returns.card.needsResolve')}
        </p>
      )}
    </Card>
  )
}

