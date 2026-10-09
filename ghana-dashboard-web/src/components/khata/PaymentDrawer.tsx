import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { PAYMENT_MODES, recordPayment } from '@/api/khata'
import type { Party, PaymentMode } from '@/api/khata'
import { Button, Input, Select, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency } from '@/utils/format'
import { Icon } from '@/utils/icons'

/**
 * Records money received against the outstanding balance. The API books an IN
 * payment, so the cash book and the ledger stay in step.
 */
export function PaymentDrawer({
  open,
  party,
  onClose,
  onDone,
}: {
  open: boolean
  party: Party | null
  onClose: () => void
  onDone: () => Promise<void> | void
}) {
  const { t } = useLanguage()

  const [amount, setAmount] = useState('')
  const [mode, setMode] = useState<PaymentMode>('CASH')
  const [date, setDate] = useState('')
  const [referenceNo, setReferenceNo] = useState('')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setAmount('')
      setMode('CASH')
      setDate('')
      setReferenceNo('')
      setNote('')
      setErrors({})
    }
  }

  const balance = party?.balance ?? 0

  const save = useMutation({
    mutationFn: () => {
      if (!party) throw new Error('No party selected')
      return recordPayment(party.id, {
        amount: Number(amount),
        mode,
        date: date || null,
        referenceNo,
        note,
      })
    },
    onSuccess: async () => {
      toast.success(t('khata.payment.saved', { amount: formatCurrency(Number(amount), { decimals: 2 }) }))
      await onDone()
      onClose()
    },
    onError: (error) => toast.error(normalizeError(error).message || t('khata.payment.failed')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const found: Record<string, string> = {}
    const value = Number(amount)
    if (!Number.isFinite(value) || value <= 0) found.amount = t('khata.errors.paymentAmount')
    else if (balance > 0 && value > balance) {
      found.amount = t('khata.errors.overpay', { amount: formatCurrency(balance, { decimals: 2 }) })
    }
    setErrors(found)
    if (Object.keys(found).length > 0) return
    save.mutate()
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={t('khata.payment.title')}
      subtitle={party ? t('khata.payment.subtitle', { name: party.name }) : undefined}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={save.isPending} className="sm:flex-1">
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="khata-payment-form" disabled={save.isPending} className="sm:flex-1">
            <Icon name={save.isPending ? 'loader' : 'check'} className={cn('size-4', save.isPending && 'animate-spin')} />
            {save.isPending ? t('khata.payment.saving') : t('khata.payment.submit')}
          </Button>
        </>
      }
    >
      <form id="khata-payment-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        {party && balance > 0 && (
          <div className="flex items-center justify-between rounded-card bg-surface-3 px-3 py-2.5">
            <span className="text-sm text-ink-soft">{t('khata.payment.outstanding')}</span>
            <span className="text-base font-semibold tabular-nums text-ink">
              {formatCurrency(balance, { decimals: 2 })}
            </span>
          </div>
        )}

        {party && balance <= 0 && (
          <p className="rounded-card bg-brand-50 px-3 py-2.5 text-sm text-brand-700 dark:bg-brand-500/10 dark:text-brand-300">
            {t('khata.payment.nothingOwed')}
          </p>
        )}

        <Input
          label={t('khata.payment.amount')}
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          error={errors.amount}
          inputMode="decimal"
          disabled={balance <= 0}
        />

        {balance > 0 && (
          <div className="flex flex-wrap gap-2">
            {[0.25, 0.5, 1].map((fraction) => (
              <Button
                key={fraction}
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setAmount(String(Number((balance * fraction).toFixed(2))))}
              >
                {fraction === 1
                  ? t('khata.payment.fullAmount')
                  : t('khata.payment.fraction', { percent: Math.round(fraction * 100) })}
              </Button>
            ))}
          </div>
        )}

        <Select
          label={t('khata.payment.mode')}
          options={PAYMENT_MODES.map((option) => ({ value: option, label: t(`payment.mode.${option}`) }))}
          value={mode}
          onChange={(value) => setMode(value as PaymentMode)}
          disabled={balance <= 0}
        />

        <Input
          label={t('khata.payment.date')}
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
          hint={t('khata.payment.dateHint')}
        />

        <Input
          label={t('khata.payment.reference')}
          value={referenceNo}
          onChange={(event) => setReferenceNo(event.target.value)}
          autoComplete="off"
        />

        <Textarea
          label={t('khata.payment.note')}
          value={note}
          onChange={(event) => setNote(event.target.value)}
          placeholder={t('khata.payment.notePlaceholder')}
          rows={2}
        />
      </form>
    </Sheet>
  )
}
