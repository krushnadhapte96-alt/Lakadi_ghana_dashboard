import type { SeedStock } from '@/api/beej'
import { Badge, Card, IconButton } from '@/components/ui'
import { Sparkline } from '@/components/beej/Sparkline'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'

export function StockCard({
  stock,
  onAdjust,
  onRateHistory,
}: {
  stock: SeedStock
  onAdjust: () => void
  onRateHistory: () => void
}) {
  const { t } = useLanguage()

  return (
    <Card className="relative overflow-hidden border-gold-200/60 bg-gradient-to-br from-gold-50 to-surface-2 p-4 dark:border-gold-800/40 dark:from-gold-900/20 dark:to-surface-2">
      <div className="mb-3 flex items-start justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-ink">{stock.seedTypeName}</p>
          {stock.lowStock && (
            <Badge variant="warning" size="sm" className="mt-1.5">
              {t('beej.stock.low')}
            </Badge>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <IconButton
            variant="ghost"
            size="sm"
            aria-label={t('beej.stock.rateHistory')}
            title={t('beej.stock.rateHistory')}
            onClick={onRateHistory}
          >
            <Icon name="trending-up" className="size-4" />
          </IconButton>
          <IconButton
            variant="ghost"
            size="sm"
            aria-label={t('beej.stock.adjust')}
            title={t('beej.stock.adjust')}
            onClick={onAdjust}
          >
            <Icon name="scale" className="size-4" />
          </IconButton>
        </div>
      </div>

      <div className="mb-3 flex items-end justify-between gap-3">
        <div>
          <p className="text-xs text-ink-soft">{t('beej.stock.available')}</p>
          <p className="text-2xl font-bold tabular-nums text-ink">
            {formatQty(stock.availableKg)} <span className="text-sm font-medium text-ink-soft">kg</span>
          </p>
        </div>
        <div className="text-right">
          <p className="text-xs text-ink-soft">{t('beej.stock.avgRate')}</p>
          <p className="text-sm font-semibold tabular-nums text-ink">
            {formatCurrency(stock.avgRate, { decimals: 2 })}
            <span className="text-ink-soft">{t('beej.stock.perKg')}</span>
          </p>
        </div>
      </div>

      <Sparkline values={stock.stockTrend} className="w-full" />
    </Card>
  )
}

