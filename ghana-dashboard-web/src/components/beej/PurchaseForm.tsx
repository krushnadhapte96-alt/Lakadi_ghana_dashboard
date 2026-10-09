import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { PAYMENT_MODES } from '@/api/expenses'
import type { PaymentMode } from '@/api/expenses'
import {
  GRADES,
  createSeedPurchase,
  serverFieldErrors,
  updateSeedPurchase,
} from '@/api/beej'
import type { Grade, SeedPurchaseDetail, SeedType, Supplier } from '@/api/beej'
import { BillUpload } from '@/components/expenses/BillUpload'
import { billFileError } from '@/components/expenses/helpers'
import { Button, Input, Select } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { todayISO } from '@/utils/dateRange'
import { formatCurrency } from '@/utils/format'

interface FormState {
  seedTypeId: number | null
  supplierId: number | null
  qtyKg: string
  ratePerKg: string
  moisturePercent: string
  grade: Grade
  purchaseDate: string
  amountPaid: string
  paymentMode: PaymentMode
  billFile: File | null
  removeBill: boolean
}

function initialState(purchase: SeedPurchaseDetail | null): FormState {
  return {
    seedTypeId: purchase?.seedTypeId ?? null,
    supplierId: purchase?.supplierId ?? null,
    qtyKg: purchase ? String(purchase.qtyKg) : '',
    ratePerKg: purchase ? String(purchase.ratePerKg) : '',
    moisturePercent: purchase?.moisturePercent != null ? String(purchase.moisturePercent) : '',
    grade: purchase?.grade ?? 'A',
    purchaseDate: purchase?.purchaseDate ?? todayISO(),
    amountPaid: purchase ? String(purchase.amountPaid) : '0',
    paymentMode: 'CASH',
    billFile: null,
    removeBill: false,
  }
}

const INVALIDATED_KEYS = [['seed-purchases'], ['seed-stock'], ['suppliers'], ['payments'], ['cash-book']]

export function PurchaseForm({
  purchase,
  seedTypes,
  suppliers,
  onDone,
}: {
  purchase: SeedPurchaseDetail | null
  seedTypes: SeedType[]
  suppliers: Supplier[]
  onDone: () => void
}) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(() => initialState(purchase))
  const [errors, setErrors] = useState<Record<string, string>>({})

  const total = (Number(form.qtyKg) || 0) * (Number(form.ratePerKg) || 0)

  const mutation = useMutation({
    mutationFn: () => {
      if (purchase) {
        return updateSeedPurchase(purchase.id, {
          supplierId: form.supplierId as number,
          moisturePercent: form.moisturePercent,
          grade: form.grade,
          purchaseDate: form.purchaseDate,
          amountPaid: form.amountPaid,
          paymentMode: form.paymentMode,
          billFile: form.billFile,
          removeBill: form.removeBill,
        })
      }
      return createSeedPurchase({
        seedTypeId: form.seedTypeId as number,
        supplierId: form.supplierId as number,
        qtyKg: form.qtyKg,
        ratePerKg: form.ratePerKg,
        moisturePercent: form.moisturePercent,
        grade: form.grade,
        purchaseDate: form.purchaseDate,
        amountPaid: form.amountPaid,
        paymentMode: form.paymentMode,
        billFile: form.billFile,
      })
    },
    onSuccess: async () => {
      toast.success(t(purchase ? 'beej.purchases.updated' : 'beej.purchases.created'))
      await Promise.all(INVALIDATED_KEYS.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
      onDone()
    },
    onError: (error) => {
      setErrors(serverFieldErrors(error))
      toast.error(normalizeError(error).message || t('beej.purchases.saveFailed'))
    },
  })

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  function validate(): Record<string, string> {
    const next: Record<string, string> = {}
    if (!purchase && form.seedTypeId === null) next.seedTypeId = t('beej.purchase.errors.seedType')
    if (form.supplierId === null) next.supplierId = t('beej.purchase.errors.supplier')
    if (!purchase) {
      const qty = Number(form.qtyKg)
      if (!form.qtyKg || !Number.isFinite(qty) || qty <= 0) next.qtyKg = t('beej.purchase.errors.qty')
      const rate = Number(form.ratePerKg)
      if (form.ratePerKg === '' || !Number.isFinite(rate) || rate < 0) next.ratePerKg = t('beej.purchase.errors.rate')
    }
    if (!form.purchaseDate) next.purchaseDate = t('beej.purchase.errors.date')
    else if (form.purchaseDate > todayISO()) next.purchaseDate = t('beej.purchase.errors.dateFuture')
    if (form.moisturePercent.trim()) {
      const moisture = Number(form.moisturePercent)
      if (!Number.isFinite(moisture) || moisture < 0 || moisture > 100) {
        next.moisturePercent = t('beej.purchase.errors.moisture')
      }
    }
    const amountPaid = Number(form.amountPaid || '0')
    if (!Number.isFinite(amountPaid) || amountPaid < 0 || amountPaid > total + 0.01) {
      next.amountPaid = t('beej.purchase.errors.amountPaid')
    }
    if (form.billFile) {
      const problem = billFileError(form.billFile)
      if (problem) next.billFile = t(problem === 'fileType' ? 'beej.purchase.errors.fileType' : 'beej.purchase.errors.fileSize')
    }
    return next
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0) return
    mutation.mutate()
  }

  const seedTypeOptions = seedTypes.map((s) => ({ value: String(s.id), label: s.nameEn }))
  const supplierOptions = suppliers.filter((s) => s.active).map((s) => ({ value: String(s.id), label: s.name }))
  const hasExistingBill = Boolean(purchase?.hasBill) && !form.removeBill

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-1 flex-col overflow-hidden">
      <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
        <Select
          label={t('beej.purchase.form.seedType')}
          options={seedTypeOptions}
          value={form.seedTypeId !== null ? String(form.seedTypeId) : undefined}
          error={errors.seedTypeId}
          disabled={Boolean(purchase)}
          onChange={(value) => set('seedTypeId', Number(value))}
        />
        <Select
          label={t('beej.purchase.form.supplier')}
          options={supplierOptions}
          value={form.supplierId !== null ? String(form.supplierId) : undefined}
          error={errors.supplierId}
          onChange={(value) => set('supplierId', Number(value))}
        />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label={t('beej.purchase.form.qty')}
            type="number"
            inputMode="decimal"
            min="0.001"
            step="0.001"
            value={form.qtyKg}
            error={errors.qtyKg}
            disabled={Boolean(purchase)}
            onChange={(e) => set('qtyKg', e.target.value)}
          />
          <Input
            label={t('beej.purchase.form.rate')}
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={form.ratePerKg}
            error={errors.ratePerKg}
            disabled={Boolean(purchase)}
            onChange={(e) => set('ratePerKg', e.target.value)}
          />
        </div>
        {purchase && <p className="text-xs text-ink-soft">{t('beej.purchase.form.qtyRateLocked')}</p>}

        <div className="rounded-card border border-line bg-surface-3 px-3.5 py-2.5 text-sm">
          <span className="text-ink-soft">{t('beej.purchase.form.total')}: </span>
          <span className="font-semibold tabular-nums text-ink">{formatCurrency(total, { decimals: 2 })}</span>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label={t('beej.purchase.form.moisture')}
            type="number"
            inputMode="decimal"
            min="0"
            max="100"
            step="0.1"
            value={form.moisturePercent}
            error={errors.moisturePercent}
            onChange={(e) => set('moisturePercent', e.target.value)}
          />
          <Input
            label={t('beej.purchase.form.date')}
            type="date"
            max={todayISO()}
            value={form.purchaseDate}
            error={errors.purchaseDate}
            onChange={(e) => set('purchaseDate', e.target.value)}
          />
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium text-ink">{t('beej.purchase.form.grade')}</legend>
          <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label={t('beej.purchase.form.grade')}>
            {GRADES.map((grade) => (
              <button
                key={grade}
                type="button"
                role="radio"
                aria-checked={form.grade === grade}
                onClick={() => set('grade', grade)}
                className={cn(
                  'focus-ring rounded-card border px-2 py-2 text-sm transition-colors',
                  form.grade === grade
                    ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300'
                    : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                )}
              >
                {t(`beej.grade.${grade}`)}
              </button>
            ))}
          </div>
        </fieldset>

        <Input
          label={t('beej.purchase.form.amountPaid')}
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          value={form.amountPaid}
          error={errors.amountPaid}
          onChange={(e) => set('amountPaid', e.target.value)}
        />

        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium text-ink">{t('beej.purchase.form.paymentMode')}</legend>
          <div className="grid grid-cols-4 gap-2" role="radiogroup" aria-label={t('beej.purchase.form.paymentMode')}>
            {PAYMENT_MODES.map((mode) => (
              <button
                key={mode}
                type="button"
                role="radio"
                aria-checked={form.paymentMode === mode}
                onClick={() => set('paymentMode', mode)}
                className={cn(
                  'focus-ring rounded-card border px-2 py-2 text-sm transition-colors',
                  form.paymentMode === mode
                    ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300'
                    : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                )}
              >
                {t(`payment.mode.${mode}` as const)}
              </button>
            ))}
          </div>
        </fieldset>

        <BillUpload
          file={form.billFile}
          existing={hasExistingBill ? { name: purchase?.billFileName ?? null, size: null } : null}
          error={errors.billFile}
          onFile={(file) => {
            setForm((prev) => ({ ...prev, billFile: file, removeBill: file ? false : prev.removeBill }))
            const problem = file ? billFileError(file) : null
            setErrors((prev) => ({
              ...prev,
              billFile: problem ? t(problem === 'fileType' ? 'beej.purchase.errors.fileType' : 'beej.purchase.errors.fileSize') : '',
            }))
          }}
          onRemoveExisting={() => set('removeBill', true)}
        />
      </div>

      <div className="flex justify-end gap-3 border-t border-line bg-surface-2 px-5 py-4">
        <Button variant="secondary" onClick={onDone} disabled={mutation.isPending}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? t('beej.purchase.form.saving') : t('beej.purchase.form.save')}
        </Button>
      </div>
    </form>
  )
}

