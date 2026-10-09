import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { giveExpiryDiscount } from '@/api/expiryAlerts'
import type { ExpiryAlertItem } from '@/api/expiryAlerts'
import { Button, Input, Modal } from '@/components/ui'
import { useLanguage } from '@/i18n'

export function DiscountModal({ item, onClose }: { item: ExpiryAlertItem | null; onClose: () => void }) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [pct, setPct] = useState(item?.activeDiscountPct ?? 20)
  const [reason, setReason] = useState('')

  const save = useMutation({
    mutationFn: () => giveExpiryDiscount(item!.batchStockId, pct, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['expiry-alerts'] })
      toast.success(t('expiry.discount.success'))
      onClose()
    },
    onError: () => toast.error(t('expiry.discount.failed')),
  })

  if (!item) return null

  return (
    <Modal isOpen={!!item} onClose={onClose} title={t('expiry.discount.title')} size="sm">
      <div className="space-y-5">
        <div>
          <p className="text-sm font-medium text-ink">{item.productName}</p>
          <p className="text-xs text-ink-soft">{t('expiry.card.batch', { code: item.batchId })}</p>
        </div>

        <div>
          <div className="mb-2 flex items-center justify-between">
            <label htmlFor="discount-pct" className="text-sm font-medium text-ink">
              {t('expiry.discount.percent')}
            </label>
            <span className="text-lg font-bold tabular-nums text-brand-600 dark:text-brand-300">{pct}%</span>
          </div>
          <input
            id="discount-pct"
            type="range"
            min={1}
            max={90}
            step={1}
            value={pct}
            onChange={(e) => setPct(Number(e.target.value))}
            className="w-full accent-brand-600"
          />
        </div>

        <Input
          label={t('expiry.discount.reason')}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t('expiry.discount.reasonPlaceholder')}
        />

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            {t('common.cancel')}
          </Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? t('expiry.discount.saving') : t('expiry.discount.apply')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

