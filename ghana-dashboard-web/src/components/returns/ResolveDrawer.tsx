import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import {
  PAYMENT_MODES,
  RETURN_ITEM_ACTIONS,
  RETURN_RESOLUTIONS,
  fetchReturn,
  resolveReturn,
} from '@/api/returns'
import type { ReturnItemAction, ReturnResolution, PaymentMode } from '@/api/returns'
import { ActionBadge } from '@/components/returns/TypeChip'
import { Button, ErrorState, Input, Sheet, Skeleton, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'

const INVALIDATED = [
  ['returns'],
  ['inventory'],
  ['pnl'],
  ['payments'],
  ['cash-book'],
  ['loss-ledger'],
  ['sales'],
]

export function ResolveDrawer({
  returnId,
  onClose,
  onDone,
}: {
  returnId: number | null
  onClose: () => void
  onDone?: (id: number) => void
}) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()

  const [resolution, setResolution] = useState<ReturnResolution | null>(null)
  const [actions, setActions] = useState<Record<number, ReturnItemAction | undefined>>({})
  const [refundAmount, setRefundAmount] = useState('')
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const detail = useQuery({
    queryKey: ['returns', 'detail', returnId],
    queryFn: () => fetchReturn(returnId as number),
    enabled: returnId !== null,
  })

  const data = detail.data
  const totalValue = (data?.items ?? []).reduce(
    (sum, item) => sum + item.qty * item.unitPrice,
    0,
  )
  const refundValue = refundAmount === '' ? totalValue : Number(refundAmount)
  // REJECT closes the complaint without touching stock, so no per-item choice.
  const needsActions = resolution !== null && resolution !== 'REJECT'
  const missingAction = data?.items.some((item) => !actions[item.id]) ?? false

  // Clear the form when the drawer opens so a previous attempt does not linger.
  const [wasOpen, setWasOpen] = useState(returnId !== null)
  if ((returnId !== null) !== wasOpen) {
    setWasOpen(returnId !== null)
    if (returnId !== null) {
      setResolution(null)
      setActions({})
      setRefundAmount('')
      setPaymentMode('CASH')
      setNote('')
      setErrors({})
    }
  }

  function close() {
    onClose()
  }

  const submit = useMutation({
    mutationFn: () =>
      resolveReturn(returnId as number, {
        resolution: resolution as ReturnResolution,
        items: needsActions
          ? data!.items.map((item) => ({
              returnItemId: item.id,
              action: actions[item.id] as ReturnItemAction,
            }))
          : undefined,
        refundAmount: resolution === 'REFUND' ? refundValue : null,
        paymentMode: resolution === 'REFUND' ? paymentMode : undefined,
        note,
      }),
    onSuccess: async (resolved) => {
      toast.success(t('returns.resolve.success'))
      await Promise.all(INVALIDATED.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
      onDone?.(resolved.id)
      close()
    },
    onError: (error) => toast.error(normalizeError(error).message || t('returns.resolve.failed')),
  })

  function handleSubmit() {
    const found: Record<string, string> = {}
    if (!resolution) found.resolution = t('returns.errors.resolution')
    else if (needsActions && missingAction) found.actions = t('returns.errors.actions')
    if (resolution === 'REFUND') {
      if (!Number.isFinite(refundValue) || refundValue <= 0) found.refundAmount = t('returns.errors.refund')
      else if (refundValue > totalValue) found.refundAmount = t('returns.errors.refundTooMuch')
    }
    setErrors(found)
    if (Object.keys(found).length > 0) return
    submit.mutate()
  }

  return (
    <Sheet
      open={returnId !== null}
      onClose={close}
      title={t('returns.resolve.title')}
      subtitle={data ? t('returns.resolve.subtitle', { bill: data.sourceBillNo }) : undefined}
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={submit.isPending}>
            {t('common.cancel')}
          </Button>
          <Button
            variant={resolution === 'REJECT' ? 'danger' : 'primary'}
            onClick={handleSubmit}
            disabled={submit.isPending}
          >
            {submit.isPending ? t('returns.resolve.working') : t('returns.resolve.confirm')}
          </Button>
        </>
      }
    >
      {detail.isError ? (
        <ErrorState
          title={t('returns.error.title')}
          message={t('returns.error.body')}
          retryLabel={t('common.retry')}
          onRetry={() => void detail.refetch()}
        />
      ) : !data ? (
        <div className="space-y-3 p-5">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-24" />
          <Skeleton className="h-32" />
        </div>
      ) : (
        <div className="space-y-5 px-5 py-5">
          <div className="rounded-card bg-surface-3 px-3 py-2.5 text-sm">
            <div className="flex items-center justify-between gap-3">
              <span className="font-semibold text-ink">{data.returnNo}</span>
              <span className="tabular-nums text-ink">{formatCurrency(totalValue, { decimals: 2 })}</span>
            </div>
          </div>

          <fieldset className="flex flex-col gap-2">
            <legend className="text-sm font-medium text-ink">{t('returns.resolve.resolution')}</legend>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
              {RETURN_RESOLUTIONS.map((option) => {
                const selected = resolution === option
                return (
                  <button
                    key={option}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    onClick={() => {
                      setResolution(option)
                      setErrors((prev) => ({ ...prev, resolution: '' }))
                    }}
                    className={cn(
                      'focus-ring flex flex-col gap-1 rounded-card border px-3 py-2.5 text-left transition-colors',
                      selected
                        ? 'border-brand-600 bg-brand-50 dark:bg-brand-500/20'
                        : 'border-line bg-surface hover:bg-surface-3',
                    )}
                  >
                    <span className="text-sm font-semibold text-ink">
                      {t(`returns.resolution.${option}`)}
                    </span>
                    <span className="text-xs text-ink-soft">
                      {t(`returns.resolve.help.${option}`)}
                    </span>
                  </button>
                )
              })}
            </div>
            {errors.resolution && (
              <p role="alert" className="text-xs text-crimson-600">
                {errors.resolution}
              </p>
            )}
          </fieldset>

          {needsActions && (
            <fieldset className="flex flex-col gap-2">
              <legend className="text-sm font-medium text-ink">{t('returns.resolve.perItem')}</legend>
              <ul className="space-y-2">
                {data.items.map((item) => (
                  <li key={item.id} className="rounded-card border border-line bg-surface px-3 py-2.5">
                    <div className="flex items-center gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-ink">{item.productName}</p>
                        <p className="text-xs text-ink-soft">
                          {formatQty(item.qty)}  ×  {formatCurrency(item.unitPrice, { decimals: 2 })}
                          {item.batchId ? ` · ${item.batchId}` : ''}
                        </p>
                      </div>
                      {item.action && <ActionBadge action={item.action} />}
                    </div>
                    <div className="mt-2 grid grid-cols-2 gap-2">
                      {RETURN_ITEM_ACTIONS.map((option) => {
                        const selected = actions[item.id] === option
                        return (
                          <button
                            key={option}
                            type="button"
                            role="radio"
                            aria-checked={selected}
                            onClick={() =>
                              setActions((prev) => ({ ...prev, [item.id]: option }))
                            }
                            className={cn(
                              'focus-ring rounded-card border px-2.5 py-2 text-xs font-semibold transition-colors',
                              selected
                                ? option === 'RESTOCK'
                                  ? 'border-gold-600 bg-gold-50 text-gold-700 dark:bg-gold-500/20 dark:text-gold-300'
                                  : 'border-crimson-600 bg-crimson-50 text-crimson-700 dark:bg-crimson-500/20 dark:text-crimson-300'
                                : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                            )}
                          >
                            {t(`returns.action.${option}`)}
                          </button>
                        )
                      })}
                    </div>
                  </li>
                ))}
              </ul>
              {errors.actions && (
                <p role="alert" className="text-xs text-crimson-600">
                  {errors.actions}
                </p>
              )}
            </fieldset>
          )}

          {resolution === 'REFUND' && (
            <div className="space-y-4 rounded-card border border-line bg-surface p-3">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <Input
                  label={t('returns.resolve.refundAmount')}
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  max={totalValue}
                  step="0.01"
                  value={refundAmount}
                  placeholder={refundValue.toFixed(2)}
                  error={errors.refundAmount}
                  hint={t('returns.resolve.refundHint', { max: formatCurrency(totalValue, { decimals: 2 }) })}
                  onChange={(event) => setRefundAmount(event.target.value)}
                />
                <div className="flex flex-col gap-1.5">
                  <span className="text-sm font-medium text-ink">{t('returns.resolve.paymentMode')}</span>
                  <div className="grid grid-cols-4 gap-1.5">
                    {PAYMENT_MODES.map((mode) => (
                      <button
                        key={mode}
                        type="button"
                        role="radio"
                        aria-checked={paymentMode === mode}
                        onClick={() => setPaymentMode(mode)}
                        className={cn(
                          'focus-ring rounded-card border px-1 py-2 text-xs font-semibold transition-colors',
                          paymentMode === mode
                            ? 'border-brand-600 bg-brand-50 text-brand-700 dark:bg-brand-500/20 dark:text-brand-300'
                            : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                        )}
                      >
                        {t(`payment.mode.${mode}`)}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
              <p className="flex items-start gap-1.5 text-xs text-ink-soft">
                <Icon name="wallet" className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                {t('returns.resolve.refundNote')}
              </p>
            </div>
          )}

          {resolution === 'REPLACE' && (
            <p className="flex items-start gap-1.5 rounded-card bg-brand-50 px-3 py-2.5 text-xs text-ink-soft dark:bg-brand-500/10">
              <Icon name="sales" className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              {t('returns.resolve.replaceNote')}
            </p>
          )}

          {resolution === 'REJECT' && (
            <p className="flex items-start gap-1.5 rounded-card bg-amber-50 px-3 py-2.5 text-xs text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
              <Icon name="alert-circle" className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              {t('returns.resolve.rejectNote')}
            </p>
          )}

          <Textarea
            label={t('returns.resolve.note')}
            placeholder={t('returns.resolve.notePlaceholder')}
            rows={2}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
        </div>
      )}
    </Sheet>
  )
}

