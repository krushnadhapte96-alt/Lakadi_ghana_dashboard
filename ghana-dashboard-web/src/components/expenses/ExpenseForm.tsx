import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { PAYMENT_MODES, createExpense, serverFieldErrors, updateExpense } from '@/api/expenses'
import type { ExpenseCategory, ExpenseDetail, PaymentMode } from '@/api/expenses'
import { BillUpload } from '@/components/expenses/BillUpload'
import { FALLBACK_COLOUR, billFileError, categoryLabel } from '@/components/expenses/helpers'
import { Button, Input, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { todayISO } from '@/utils/dateRange'

interface FormState {
  categoryId: number | null
  amount: string
  title: string
  description: string
  expenseDate: string
  paymentMode: PaymentMode
  referenceNo: string
  billFile: File | null
  removeBill: boolean
}

function initialState(expense: ExpenseDetail | null): FormState {
  return {
    categoryId: expense?.categoryId ?? null,
    amount: expense ? String(expense.amount) : '',
    title: expense?.title ?? '',
    description: expense?.description ?? '',
    expenseDate: expense?.expenseDate ?? todayISO(),
    paymentMode: expense?.paymentMode ?? 'CASH',
    referenceNo: expense?.referenceNo ?? '',
    billFile: null,
    removeBill: false,
  }
}

const INVALIDATED_KEYS = [['expenses'], ['pnl'], ['payments'], ['cash-book']]

export function ExpenseForm({
  expense,
  categories,
  onDone,
}: {
  expense: ExpenseDetail | null
  categories: ExpenseCategory[]
  onDone: () => void
}) {
  const { t, language } = useLanguage()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(() => initialState(expense))
  const [errors, setErrors] = useState<Record<string, string>>({})

  const mutation = useMutation({
    mutationFn: (state: FormState & { categoryId: number }) =>
      expense ? updateExpense(expense.id, state) : createExpense(state),
    onSuccess: async () => {
      toast.success(t(expense ? 'expenses.updated' : 'expenses.created'))
      await Promise.all(INVALIDATED_KEYS.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
      onDone()
    },
    onError: (error) => {
      const fieldErrors = serverFieldErrors(error)
      setErrors(fieldErrors)
      toast.error(normalizeError(error).message || t('expenses.saveFailed'))
    },
  })

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  function validate(): Record<string, string> {
    const next: Record<string, string> = {}
    if (form.categoryId === null) next.categoryId = t('expenses.errors.category')
    const amount = Number(form.amount)
    if (!form.amount || !Number.isFinite(amount) || amount <= 0) next.amount = t('expenses.errors.amount')
    if (!form.title.trim()) next.title = t('expenses.errors.title')
    if (!form.expenseDate) next.expenseDate = t('expenses.errors.date')
    else if (form.expenseDate > todayISO()) next.expenseDate = t('expenses.errors.dateFuture')
    if (form.billFile) {
      const problem = billFileError(form.billFile)
      if (problem) next.billFile = t(problem === 'fileType' ? 'expenses.errors.fileType' : 'expenses.errors.fileSize')
    }
    return next
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0 || form.categoryId === null) return
    mutation.mutate({ ...form, categoryId: form.categoryId })
  }

  const hasExistingBill = Boolean(expense?.hasBill) && !form.removeBill

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-1 flex-col overflow-hidden">
      <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium text-ink">{t('expenses.form.category')}</legend>
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label={t('expenses.form.category')}>
            {categories.map((category) => {
              const selected = form.categoryId === category.id
              return (
                <button
                  key={category.id}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  onClick={() => set('categoryId', category.id)}
                  className={cn(
                    'focus-ring inline-flex items-center gap-2 rounded-pill border px-3 py-1.5 text-sm transition-colors',
                    selected
                      ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300'
                      : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                  )}
                >
                  <span
                    className="size-2.5 rounded-full"
                    style={{ backgroundColor: category.colour ?? FALLBACK_COLOUR }}
                    aria-hidden="true"
                  />
                  {categoryLabel(category, language)}
                </button>
              )
            })}
          </div>
          {errors.categoryId && (
            <p role="alert" className="text-xs text-crimson-600">
              {errors.categoryId}
            </p>
          )}
        </fieldset>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label={t('expenses.form.amount')}
            type="number"
            inputMode="decimal"
            min="0.01"
            step="0.01"
            value={form.amount}
            error={errors.amount}
            onChange={(e) => set('amount', e.target.value)}
          />
          <Input
            label={t('expenses.form.date')}
            type="date"
            max={todayISO()}
            value={form.expenseDate}
            error={errors.expenseDate}
            onChange={(e) => set('expenseDate', e.target.value)}
          />
        </div>

        <Input
          label={t('expenses.form.title')}
          placeholder={t('expenses.form.titlePlaceholder')}
          maxLength={200}
          value={form.title}
          error={errors.title}
          onChange={(e) => set('title', e.target.value)}
        />

        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium text-ink">{t('expenses.form.paymentMode')}</legend>
          <div className="grid grid-cols-4 gap-2" role="radiogroup" aria-label={t('expenses.form.paymentMode')}>
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

        <Input
          label={t('expenses.form.reference')}
          maxLength={100}
          value={form.referenceNo}
          error={errors.referenceNo}
          onChange={(e) => set('referenceNo', e.target.value)}
        />
        <Textarea
          label={t('expenses.form.notes')}
          value={form.description}
          error={errors.description}
          onChange={(e) => set('description', e.target.value)}
        />

        <BillUpload
          file={form.billFile}
          existing={hasExistingBill ? { name: expense?.billFileName ?? null, size: expense?.billFileSize ?? null } : null}
          error={errors.billFile}
          onFile={(file) => {
            setForm((prev) => ({ ...prev, billFile: file, removeBill: file ? false : prev.removeBill }))
            const problem = file ? billFileError(file) : null
            setErrors((prev) => ({
              ...prev,
              billFile: problem ? t(problem === 'fileType' ? 'expenses.errors.fileType' : 'expenses.errors.fileSize') : '',
            }))
          }}
          onRemoveExisting={() => set('removeBill', true)}
        />

        {!expense && <p className="text-xs text-ink-soft">{t('expenses.form.outNote')}</p>}
      </div>

      <div className="flex justify-end gap-3 border-t border-line bg-surface-2 px-5 py-4">
        <Button variant="secondary" onClick={onDone} disabled={mutation.isPending}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? t('expenses.form.saving') : t('expenses.form.save')}
        </Button>
      </div>
    </form>
  )
}

