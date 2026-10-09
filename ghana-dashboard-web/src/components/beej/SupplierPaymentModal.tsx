import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { recordSupplierPayment } from '@/api/beej'
import type { Supplier, SupplierPaymentInput } from '@/api/beej'
import { PAYMENT_MODES } from '@/api/expenses'
import { Button, Input, Modal, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { todayISO } from '@/utils/dateRange'

function initial(): SupplierPaymentInput {
  return { amount: '', mode: 'CASH', paymentDate: todayISO(), referenceNo: '', note: '' }
}

export function SupplierPaymentModal({
  open,
  supplier,
  onClose,
}: {
  open: boolean
  supplier: Supplier | null
  onClose: () => void
}) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<SupplierPaymentInput>(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})

  // Reset the form each time the modal opens (adjust state during render, not in an effect).
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setForm(initial())
      setErrors({})
    }
  }

  const mutation = useMutation({
    mutationFn: () => recordSupplierPayment(supplier!.id, form),
    onSuccess: async () => {
      toast.success(t('beej.payment.recorded'))
      await Promise.all(
        [['suppliers'], ['payments'], ['cash-book']].map((queryKey) => queryClient.invalidateQueries({ queryKey })),
      )
      onClose()
    },
    onError: (error) => toast.error(normalizeError(error).message || t('beej.payment.failed')),
  })

  function set<K extends keyof SupplierPaymentInput>(key: K, value: SupplierPaymentInput[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const amount = Number(form.amount)
    if (!form.amount || !Number.isFinite(amount) || amount <= 0) {
      setErrors({ amount: t('beej.payment.errors.amount') })
      return
    }
    mutation.mutate()
  }

  return (
    <Modal isOpen={open} onClose={onClose} title={t('beej.payment.title')} size="sm">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {supplier && <p className="text-sm text-ink-soft">{supplier.name}</p>}
        <Input
          label={t('beej.payment.form.amount')}
          type="number"
          inputMode="decimal"
          min="0.01"
          step="0.01"
          value={form.amount}
          error={errors.amount}
          onChange={(e) => set('amount', e.target.value)}
        />
        <Input
          label={t('beej.payment.form.date')}
          type="date"
          max={todayISO()}
          value={form.paymentDate}
          onChange={(e) => set('paymentDate', e.target.value)}
        />

        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium text-ink">{t('beej.payment.form.mode')}</legend>
          <div className="grid grid-cols-4 gap-2" role="radiogroup" aria-label={t('beej.payment.form.mode')}>
            {PAYMENT_MODES.map((mode) => (
              <button
                key={mode}
                type="button"
                role="radio"
                aria-checked={form.mode === mode}
                onClick={() => set('mode', mode)}
                className={cn(
                  'focus-ring rounded-card border px-2 py-2 text-sm transition-colors',
                  form.mode === mode
                    ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300'
                    : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                )}
              >
                {t(`payment.mode.${mode}` as const)}
              </button>
            ))}
          </div>
        </fieldset>

        <Input
          label={t('beej.payment.form.reference')}
          value={form.referenceNo}
          onChange={(e) => set('referenceNo', e.target.value)}
        />
        <Textarea label={t('beej.payment.form.note')} value={form.note} onChange={(e) => set('note', e.target.value)} />

        <div className="flex justify-end gap-3 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? t('beej.payment.form.saving') : t('beej.payment.form.save')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

