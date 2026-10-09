import { useQuery } from '@tanstack/react-query'
import { fetchRateHistory } from '@/api/beej'
import { EmptyState, ErrorState, Modal, Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'

const HEIGHT = 200
const MARGIN = { top: 10, right: 12, bottom: 28, left: 56 }

export function RateHistoryModal({
  open,
  seedTypeId,
  seedTypeName,
  onClose,
}: {
  open: boolean
  seedTypeId: number | null
  seedTypeName: string
  onClose: () => void
}) {
  const { t, language } = useLanguage()

  const history = useQuery({
    queryKey: ['seed-types', seedTypeId, 'rate-history'],
    queryFn: () => fetchRateHistory(seedTypeId as number),
    enabled: open && seedTypeId !== null,
  })

  const points = history.data ?? []
  const rates = points.map((p) => p.ratePerKg)
  const min = rates.length ? Math.min(...rates) : 0
  const max = rates.length ? Math.max(...rates) : 1
  const span = max - min || 1
  const plotW = 100
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom
  const step = points.length > 1 ? plotW / (points.length - 1) : 0
  const y = (v: number) => MARGIN.top + plotH - ((v - min) / span) * plotH
  const linePoints = points.map((p, i) => `${MARGIN.left + i * step},${y(p.ratePerKg)}`).join(' ')

  return (
    <Modal isOpen={open} onClose={onClose} title={t('beej.rateHistory.title', { name: seedTypeName })} size="lg">
      {history.isError ? (
        <ErrorState
          title={t('beej.rateHistory.error.title')}
          onRetry={() => void history.refetch()}
          retryLabel={t('common.retry')}
        />
      ) : !history.data ? (
        <Skeleton className="h-52" />
      ) : points.length === 0 ? (
        <EmptyState icon={<Icon name="trending-up" className="size-7" />} title={t('beej.rateHistory.empty')} />
      ) : (
        <div>
          <svg viewBox={`0 0 ${plotW + MARGIN.left + MARGIN.right} ${HEIGHT}`} width="100%" height={HEIGHT} role="img">
            <line
              x1={MARGIN.left}
              x2={plotW + MARGIN.left}
              y1={y(min)}
              y2={y(min)}
              className="stroke-line"
              strokeWidth={1}
            />
            <text x={MARGIN.left - 8} y={y(max)} textAnchor="end" dominantBaseline="middle" className="fill-ink-soft text-[10px] tabular-nums">
              {formatCurrency(max, { decimals: 2 })}
            </text>
            <text x={MARGIN.left - 8} y={y(min)} textAnchor="end" dominantBaseline="middle" className="fill-ink-soft text-[10px] tabular-nums">
              {formatCurrency(min, { decimals: 2 })}
            </text>
            {points.length > 1 && (
              <polyline
                points={linePoints}
                fill="none"
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                className="stroke-gold-600 dark:stroke-gold-300"
                vectorEffect="non-scaling-stroke"
              />
            )}
            {points.map((p, i) => (
              <circle
                key={p.purchaseId}
                cx={MARGIN.left + i * step}
                cy={y(p.ratePerKg)}
                r={3}
                className="fill-gold-600 stroke-surface-2 dark:fill-gold-300"
                strokeWidth={1.5}
              >
                <title>
                  {formatDate(p.purchaseDate, language)}: {formatCurrency(p.ratePerKg, { decimals: 2 })}/kg
                </title>
              </circle>
            ))}
          </svg>

          <table className="mt-4 w-full text-sm">
            <caption className="sr-only">{t('beej.rateHistory.title', { name: seedTypeName })}</caption>
            <thead>
              <tr className="text-left text-xs text-ink-soft">
                <th scope="col" className="pb-2 font-medium">{t('beej.ledger.columns.date')}</th>
                <th scope="col" className="pb-2 font-medium">{t('beej.purchase.form.supplier')}</th>
                <th scope="col" className="pb-2 text-right font-medium">{t('beej.purchase.form.qty')}</th>
                <th scope="col" className="pb-2 text-right font-medium">{t('beej.purchase.form.rate')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {points
                .slice()
                .reverse()
                .map((p) => (
                  <tr key={p.purchaseId}>
                    <td className="py-2 text-ink-soft">{formatDate(p.purchaseDate, language)}</td>
                    <td className="py-2 text-ink">{p.supplierName}</td>
                    <td className="py-2 text-right tabular-nums text-ink">{p.qtyKg}</td>
                    <td className="py-2 text-right font-semibold tabular-nums text-ink">
                      {formatCurrency(p.ratePerKg, { decimals: 2 })}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      )}
    </Modal>
  )
}

