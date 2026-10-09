import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { writeOffExpiry } from '@/api/expiryAlerts'
import type { ExpiryAlertItem } from '@/api/expiryAlerts'
import { Button, Input, Modal, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatQty } from '@/utils/format'

export function WriteOffModal({ item, onClose }: { item: ExpiryAlertItem | null; onClose: () => void }) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [qty, setQty] = useState(item?.qtyAvailable ?? 0)
  const [note, setNote] = useState('')

  const save = useMutation({
    mutationFn: () => writeOffExpiry(item!.batchStockId, qty, note),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['expiry-alerts'] })
      toast.success(t('expiry.writeOff.success'))
      onClose()
    },
    onError: () => toast.error(t('expiry.writeOff.failed')),
  })

  if (!item) return null

  const value = qty * item.costPrice
  const invalid = qty <= 0 || qty > item.qtyAvailable

  return (
    <Modal isOpen={!!item} onClose={onClose} title={t('expiry.writeOff.title')} size="sm">
      <div className="space-y-5">
        <div>
          <p className="text-sm font-medium text-ink">{item.productName}</p>
          <p className="text-xs text-ink-soft">
            {t('expiry.writeOff.available', { qty: formatQty(item.qtyAvailable), unit: item.unit })}
          </p>
        </div>

        <Input
          label={t('expiry.writeOff.qty')}
          type="number"
          min="0"
          max={item.qtyAvailable}
          step="0.001"
          inputMode="decimal"
          value={qty}
          onChange={(e) => setQty(Number(e.target.value))}
          error={invalid ? t('expiry.writeOff.qtyInvalid') : undefined}
        />

        <Textarea
          label={t('expiry.writeOff.note')}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={t('expiry.writeOff.notePlaceholder')}
        />

        <p className="text-sm text-ink-soft">
          {t('expiry.writeOff.lossPreview', { value: formatCurrency(value) })}
        </p>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            {t('common.cancel')}
          </Button>
          <Button variant="danger" onClick={() => save.mutate()} disabled={save.isPending || invalid}>
            {save.isPending ? t('expiry.writeOff.saving') : t('expiry.writeOff.confirm')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

