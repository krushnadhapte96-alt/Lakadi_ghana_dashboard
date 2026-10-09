import type { ExpiryAlertItem } from '@/api/expiryAlerts'
import { Badge, Modal } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatDate, formatQty } from '@/utils/format'

export function BatchDetailModal({ item, onClose }: { item: ExpiryAlertItem | null; onClose: () => void }) {
  const { t, language } = useLanguage()
  if (!item) return null

  const rows: Array<[string, string]> = [
    [t('expiry.detail.batch'), item.batchId],
    [t('expiry.detail.sku'), item.sku],
    [t('expiry.detail.qty'), `${formatQty(item.qtyAvailable)} ${item.unit}`],
    [t('expiry.detail.cost'), formatCurrency(item.costPrice)],
    [t('expiry.detail.value'), formatCurrency(item.valueAtRisk)],
    [t('expiry.detail.expiry'), formatDate(item.expiryDate, language)],
    [t('expiry.detail.daysLeft'), String(item.daysLeft)],
  ]

  return (
    <Modal isOpen={!!item} onClose={onClose} title={item.productName} size="sm">
      <div className="space-y-4">
        {item.activeDiscountPct !== null && (
          <Badge variant="clay">{t('expiry.card.discountActive', { pct: item.activeDiscountPct })}</Badge>
        )}
        <dl className="space-y-2">
          {rows.map(([label, value]) => (
            <div key={label} className="flex items-center justify-between text-sm">
              <dt className="text-ink-soft">{label}</dt>
              <dd className="font-medium tabular-nums text-ink">{value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </Modal>
  )
}

