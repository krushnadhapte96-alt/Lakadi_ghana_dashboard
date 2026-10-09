import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { adjustSeedStock } from '@/api/beej'
import type { StockMovementType } from '@/api/beej'
import { Button, Input, Modal, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'

interface FormState {
  type: StockMovementType
  qty: string
  reason: string
  note: string
}

const initial: FormState = { type: 'ADJUST', qty: '', reason: '', note: '' }

export function StockAdjustModal({
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
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})

  // Reset the form each time the modal opens (adjust state during render, not in an effect).
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setForm(initial)
      setErrors({})
    }
  }

  const mutation = useMutation({
    mutationFn: (state: FormState) => adjustSeedStock(seedTypeId as number, state),
    onSuccess: async () => {
      toast.success(t('beej.adjust.done'))
      await queryClient.invalidateQueries({ queryKey: ['seed-stock'] })
      onClose()
    },
    onError: (error) => toast.error(normalizeError(error).message || t('beej.adjust.failed')),
  })

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  function validate(): Record<string, string> {
    const next: Record<string, string> = {}
    const qty = Number(form.qty)
    if (!form.qty || !Number.isFinite(qty) || qty === 0) next.qty = t('beej.adjust.errors.qty')
    else if (form.type === 'WASTE' && qty > 0) next.qty = t('beej.adjust.errors.qtyWaste')
    if (!form.reason.trim()) next.reason = t('beej.adjust.errors.reason')
    return next
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0) return
    const qty = form.type === 'WASTE' ? -Math.abs(Number(form.qty)) : Number(form.qty)
    mutation.mutate({ ...form, qty: String(qty) })
  }

  return (
    <Modal isOpen={open} onClose={onClose} title={t('beej.adjust.title', { name: seedTypeName })} size="sm">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium text-ink">{t('beej.adjust.form.type')}</legend>
          <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label={t('beej.adjust.form.type')}>
            {(['ADJUST', 'WASTE'] as StockMovementType[]).map((type) => (
              <button
                key={type}
                type="button"
                role="radio"
                aria-checked={form.type === type}
                onClick={() => set('type', type)}
                className={cn(
                  'focus-ring rounded-card border px-3 py-2 text-sm transition-colors',
                  form.type === type
                    ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300'
                    : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                )}
              >
                {t(`beej.adjust.type.${type}`)}
              </button>
            ))}
          </div>
          {form.type === 'WASTE' && <p className="text-xs text-ink-soft">{t('beej.adjust.hintWaste')}</p>}
        </fieldset>

        <Input
          label={t('beej.adjust.form.qty')}
          type="number"
          inputMode="decimal"
          step="0.001"
          value={form.qty}
          error={errors.qty}
          onChange={(e) => set('qty', e.target.value)}
        />
        <Input
          label={t('beej.adjust.form.reason')}
          value={form.reason}
          error={errors.reason}
          onChange={(e) => set('reason', e.target.value)}
        />
        <Textarea
          label={t('beej.adjust.form.note')}
          value={form.note}
          onChange={(e) => set('note', e.target.value)}
        />

        <div className="flex justify-end gap-3 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? t('beej.adjust.form.saving') : t('beej.adjust.form.save')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

