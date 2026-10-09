import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { RETURN_TYPES, createReturn, lookupReturn, uploadReturnPhotos } from '@/api/returns'
import type { ReturnLookup, ReturnType } from '@/api/returns'
import { PhotoPicker } from '@/components/returns/PhotoPicker'
import { TYPE_COLOUR, qtyProblem } from '@/components/returns/helpers'
import { Badge, Button, Input, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'

const INVALIDATED = [['returns'], ['inventory'], ['pnl'], ['payments'], ['cash-book'], ['customers']]

export function NewReturnDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()

  const [billNo, setBillNo] = useState('')
  const [lookup, setLookup] = useState<ReturnLookup | null>(null)
  const [lookupError, setLookupError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)

  const [qtys, setQtys] = useState<Record<number, string>>({})
  const [type, setType] = useState<ReturnType | null>(null)
  const [description, setDescription] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [errors, setErrors] = useState<Record<string, string>>({})

  // Reset on open so a half-finished attempt never leaks into the next one.
  // Adjusting state during render is React's documented pattern for reacting to
  // a prop change, and unlike an effect it cannot show a stale frame first.
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setBillNo('')
      setLookup(null)
      setLookupError(null)
      setSearching(false)
      setQtys({})
      setType(null)
      setDescription('')
      setFiles([])
      setErrors({})
    }
  }

  async function search(event: FormEvent) {
    event.preventDefault()
    const bill = billNo.trim()
    if (!bill) {
      setLookupError(t('returns.errors.billNo'))
      return
    }
    setSearching(true)
    setLookupError(null)
    try {
      const found = await lookupReturn(bill)
      setLookup(found)
      setQtys(Object.fromEntries(found.items.map((item) => [item.productId, ''])))
    } catch (error) {
      setLookup(null)
      setLookupError(normalizeError(error).message || t('returns.lookup.failed'))
    } finally {
      setSearching(false)
    }
  }

  function reset() {
    setLookup(null)
    setQtys({})
    setLookupError(null)
  }

  const qtyOf = (productId: number) => Number(qtys[productId] ?? 0) || 0
  const chosen = (lookup?.items ?? []).filter((item) => qtyOf(item.productId) > 0)
  const claimedValue = chosen.reduce((sum, item) => sum + qtyOf(item.productId) * item.rate, 0)
  // A row is invalid when it has a value that is not a positive, finite number
  // within the quantity still returnable. Submitting must never send these.
  const invalidQty = (lookup?.items ?? []).some((item) => qtyProblem(item, qtys[item.productId] ?? ''))

  const submit = useMutation({
    mutationFn: async () => {
      const created = await createReturn({
        sourceType: lookup!.sourceType,
        sourceId: lookup!.sourceId,
        type: type as ReturnType,
        description,
        items: chosen.map((item) => ({ productId: item.productId, qty: qtyOf(item.productId) })),
      })
      // The return exists from here on. If only the photo upload fails, report
      // it as a separate outcome and still move on - re-running the whole
      // mutation would create a duplicate return.
      if (files.length > 0) {
        try {
          await uploadReturnPhotos(created.id, files)
        } catch {
          toast.error(t('returns.photosFailed'))
        }
      }
      return created
    },
    onSuccess: async () => {
      toast.success(t('returns.created'))
      await Promise.all(INVALIDATED.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
      onClose()
    },
    onError: (error) => toast.error(normalizeError(error).message || t('returns.saveFailed')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (chosen.length === 0) found.items = t('returns.errors.items')
    else if (invalidQty) found.items = t('returns.errors.qtyRange')
    if (!type) found.type = t('returns.errors.type')
    setErrors(found)
    if (Object.keys(found).length > 0) return
    submit.mutate()
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={t('returns.new.title')}
      subtitle={
        lookup
          ? t('returns.new.subtitleFound', { bill: lookup.billNo })
          : t('returns.new.subtitle')
      }
      footer={
        <>
          {lookup && (
            <Button variant="ghost" onClick={reset} disabled={submit.isPending}>
              {t('returns.new.back')}
            </Button>
          )}
          <Button variant="secondary" onClick={onClose} disabled={submit.isPending}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="new-return-form" disabled={submit.isPending || !lookup}>
            {submit.isPending ? t('returns.new.saving') : t('returns.new.submit')}
          </Button>
        </>
      }
    >
      <div className="space-y-5 px-5 py-5">
        <form onSubmit={search} noValidate className="flex items-end gap-2">
          <Input
            label={t('returns.new.billNo')}
            placeholder={t('returns.new.billNoPlaceholder')}
            value={billNo}
            error={lookupError ?? undefined}
            onChange={(event) => setBillNo(event.target.value)}
            autoComplete="off"
          />
          <Button type="submit" variant="secondary" disabled={searching} className="shrink-0">
            <Icon
              name={searching ? 'loader' : 'search'}
              className={cn('size-4', searching && 'animate-spin')}
            />
            {t('returns.new.search')}
          </Button>
        </form>

        {lookup && lookup.items.length === 0 && (
          <p className="rounded-card bg-amber-50 px-3 py-2.5 text-sm text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
            {t('returns.new.nothingReturnable')}
          </p>
        )}

        {lookup && lookup.items.length > 0 && (
          <form id="new-return-form" onSubmit={handleSubmit} noValidate className="space-y-5">
            <div className="flex flex-wrap items-center gap-2 rounded-card bg-surface-3 px-3 py-2 text-xs text-ink-soft">
              <span className="font-semibold text-ink">{lookup.billNo}</span>
              <span aria-hidden="true">·</span>
              <Badge variant="secondary" size="sm">
                {t(`returns.source.${lookup.sourceType}`)}
              </Badge>
              {lookup.customerName && (
                <>
                  <span aria-hidden="true">·</span>
                  <span>{lookup.customerName}</span>
                </>
              )}
            </div>

            <fieldset className="flex flex-col gap-2">
              <legend className="text-sm font-medium text-ink">{t('returns.form.items')}</legend>
              <ul className="space-y-2">
                {lookup.items.map((item) => {
                  const value = qtys[item.productId] ?? ''
                  const over = qtyProblem(item, value)
                  return (
                    <li
                      key={item.productId}
                      className={cn(
                        'rounded-card border px-3 py-2.5 transition-colors',
                        over ? 'border-crimson-500 bg-crimson-50 dark:bg-crimson-500/10' : 'border-line bg-surface',
                      )}
                    >
                      <div className="flex items-center gap-3">
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium text-ink">{item.productName}</p>
                          <p className="text-xs text-ink-soft">
                            {t('returns.new.available', {
                              qty: formatQty(item.qty),
                              unit: item.unit,
                            })}
                            {' · '}
                            {formatCurrency(item.rate, { decimals: 2 })}
                            {item.batchId ? ` · ${item.batchId}` : ''}
                          </p>
                        </div>
                        <input
                          type="number"
                          inputMode="decimal"
                          min="0"
                          max={item.qty}
                          step="0.001"
                          value={value}
                          onChange={(event) =>
                            setQtys((prev) => ({ ...prev, [item.productId]: event.target.value }))
                          }
                          aria-label={t('returns.new.qtyFor', { name: item.productName })}
                          className="focus-ring w-24 shrink-0 rounded-card border border-line bg-surface-2 px-2.5 py-1.5 text-right text-sm tabular-nums text-ink"
                        />
                      </div>
                      {over && (
                        <p role="alert" className="mt-1.5 text-xs text-crimson-600">
                          {t('returns.errors.qty', { max: formatQty(item.qty) })}
                        </p>
                      )}
                    </li>
                  )
                })}
              </ul>
              {errors.items && (
                <p role="alert" className="text-xs text-crimson-600">
                  {errors.items}
                </p>
              )}
            </fieldset>

            <fieldset className="flex flex-col gap-2">
              <legend className="text-sm font-medium text-ink">{t('returns.form.type')}</legend>
              <div className="flex flex-wrap gap-2" role="radiogroup" aria-label={t('returns.form.type')}>
                {RETURN_TYPES.map((option) => {
                  const selected = type === option
                  return (
                    <button
                      key={option}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      onClick={() => {
                        setType(option)
                        setErrors((prev) => ({ ...prev, type: '' }))
                      }}
                      className={cn(
                        'focus-ring inline-flex items-center gap-2 rounded-pill border px-3 py-1.5 text-sm transition-colors',
                        selected
                          ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300'
                          : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                      )}
                    >
                      <span
                        className="size-2.5 rounded-full ring-1 ring-white/70 dark:ring-black/30"
                        style={{ backgroundColor: TYPE_COLOUR[option] }}
                        aria-hidden="true"
                      />
                      {t(`returns.type.${option}`)}
                    </button>
                  )
                })}
              </div>
              {errors.type && (
                <p role="alert" className="text-xs text-crimson-600">
                  {errors.type}
                </p>
              )}
            </fieldset>

            <Textarea
              label={t('returns.form.description')}
              placeholder={t('returns.form.descriptionPlaceholder')}
              rows={3}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />

            <PhotoPicker files={files} onChange={setFiles} />

            {chosen.length > 0 && (
              <div className="flex items-center justify-between gap-3 rounded-card bg-brand-50 px-3 py-2.5 text-sm dark:bg-brand-500/10">
                <span className="text-ink-soft">{t('returns.new.summary', { count: chosen.length })}</span>
                <span className="font-bold tabular-nums text-ink">
                  {formatCurrency(claimedValue, { decimals: 2 })}
                </span>
              </div>
            )}
          </form>
        )}
      </div>
    </Sheet>
  )
}

