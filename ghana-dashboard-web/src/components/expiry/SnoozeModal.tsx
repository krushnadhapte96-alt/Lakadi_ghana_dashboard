import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { snoozeExpiry } from '@/api/expiryAlerts'
import type { ExpiryAlertItem } from '@/api/expiryAlerts'
import { Button, Modal } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'

const PRESETS = [3, 7, 14, 30]

export function SnoozeModal({ item, onClose }: { item: ExpiryAlertItem | null; onClose: () => void }) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [days, setDays] = useState(7)

  const save = useMutation({
    mutationFn: () => snoozeExpiry(item!.batchStockId, days),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['expiry-alerts'] })
      toast.success(t('expiry.snooze.success'))
      onClose()
    },
    onError: () => toast.error(t('expiry.snooze.failed')),
  })

  if (!item) return null

  return (
    <Modal isOpen={!!item} onClose={onClose} title={t('expiry.snooze.title')} size="sm">
      <div className="space-y-5">
        <div>
          <p className="text-sm font-medium text-ink">{item.productName}</p>
          <p className="text-xs text-ink-soft">{t('expiry.card.batch', { code: item.batchId })}</p>
        </div>

        <div>
          <p className="mb-2 text-sm font-medium text-ink">{t('expiry.snooze.for')}</p>
          <div className="flex flex-wrap gap-2">
            {PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                aria-pressed={days === preset}
                onClick={() => setDays(preset)}
                className={cn(
                  'rounded-pill border px-3.5 py-1.5 text-sm font-medium transition-colors',
                  days === preset
                    ? 'border-brand-600 bg-brand-600 text-white'
                    : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                )}
              >
                {t('expiry.snooze.days', { count: preset })}
              </button>
            ))}
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            {t('common.cancel')}
          </Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? t('expiry.snooze.saving') : t('expiry.snooze.confirm')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

