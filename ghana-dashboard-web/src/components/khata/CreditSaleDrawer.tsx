import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { recordCreditSale } from '@/api/khata'
import type { Party, Product } from '@/api/khata'
import { Button, Input, Select, Sheet, Textarea, ConfirmDialog } from '@/components/ui'
import { useIsOwner } from '@/auth/useCan'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { lineProblem } from '@/components/khata/helpers'

interface DraftLine {
  productId: string
  qty: string
  rate: string
}

const BLANK: DraftLine = { productId: '', qty: '', rate: '' }

/**
 * Records an on-credit sale. The API books it as a real UDHAAR sale and deducts
 * stock FIFO, so a credit limit breach comes back as a 409 that only the owner
 * may override.
 *
 * <p>`party` may be null: the home dashboard's "Udhaar entry" quick action opens this
 * drawer without a party in context, so the party is picked here instead. When a party
 * *is* supplied (from the party's detail drawer) the picker is hidden and the choice is
 * fixed, so a credit sale can never silently land on the wrong customer.
 */
export function CreditSaleDrawer({
  open,
  party,
  parties = [],
  products,
  onClose,
  onPartyChange,
  onDone,
}: {
  open: boolean
  party: Party | null
  /** Candidates for the in-drawer picker; only used when `party` is null. */
  parties?: Party[]
  products: Product[]
  onClose: () => void
  /** Reports the party chosen in the picker, so the page can follow it. */
  onPartyChange?: (party: Party) => void
  onDone: () => Promise<void> | void
}) {
  const { t } = useLanguage()
  const isOwner = useIsOwner()

  const [lines, setLines] = useState<DraftLine[]>([BLANK])
  const [note, setNote] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [limitWarning, setLimitWarning] = useState<string | null>(null)
  const [picked, setPicked] = useState<Party | null>(party)

  // A party passed in from outside always wins over whatever was picked in here.
  const activeParty = party ?? picked

  // Re-seed whenever the drawer opens so a previous sale's lines never carry over.
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setLines([BLANK])
      setNote('')
      setDueDate('')
      setErrors({})
      setLimitWarning(null)
      setPicked(party)
    }
  }

  const active = useMemo(
    () =>
      products.filter((product) => product.active).sort((a, b) => a.name.localeCompare(b.name)),
    [products],
  )

  const byId = useMemo(() => new Map(active.map((product) => [product.id, product])), [active])

  const priced = useMemo(
    () =>
      lines
        .map((line) => {
          const product = byId.get(Number(line.productId))
          const qty = Number(line.qty)
          const rate = line.rate.trim() === '' ? product?.sellingPrice ?? 0 : Number(line.rate)
          return { line, product, qty, rate, amount: qty * rate }
        })
        .filter((entry) => entry.product !== undefined && !lineProblem({ qty: entry.qty, rate: entry.rate })),
    [lines, byId],
  )

  const total = priced.reduce((sum, entry) => sum + entry.amount, 0)

  const save = useMutation({
    mutationFn: (overrideCreditLimit: boolean) => {
      if (!activeParty) throw new Error('No party selected')
      return recordCreditSale(activeParty.id, {
        items: priced.map((entry) => ({
          productId: entry.product!.id,
          qty: entry.qty,
          rate: entry.rate,
        })),
        dueDate: dueDate || null,
        note,
        overrideCreditLimit,
      })
    },
    onSuccess: async () => {
      toast.success(t('khata.creditSale.saved'))
      setLimitWarning(null)
      await onDone()
      onClose()
    },
    onError: (error, overrideCreditLimit) => {
      const err = normalizeError(error)
      // A limit breach is a 409 that the owner can wave through. Anything else
      // is a real failure and must not offer an override.
      if (err.status === 409 && /credit limit/i.test(err.message) && !overrideCreditLimit) {
        if (!isOwner) {
          toast.error(err.message)
          return
        }
        setLimitWarning(err.message)
        return
      }
      toast.error(err.message || t('khata.creditSale.failed'))
    },
  })

  function updateLine(index: number, patch: Partial<DraftLine>) {
    setLines((prev) => prev.map((line, position) => (position === index ? { ...line, ...patch } : line)))
  }

  function addLine() {
    setLines((prev) => [...prev, BLANK])
  }

  function removeLine(index: number) {
    setLines((prev) => (prev.length === 1 ? [BLANK] : prev.filter((_, position) => position !== index)))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (priced.length === 0) found.items = t('khata.errors.noLines')
    const duplicate = priced.some((entry, index) =>
      priced.findIndex((other) => other.product!.id === entry.product!.id) !== index,
    )
    if (duplicate) found.items = t('khata.errors.duplicateProduct')
    setErrors(found)
    if (Object.keys(found).length > 0) return
    save.mutate(false)
  }

  const usedIds = new Set(priced.map((entry) => entry.product!.id))

  return (
    <>
      <Sheet
        open={open}
        onClose={onClose}
        title={t('khata.creditSale.title')}
        subtitle={
          party
            ? t('khata.creditSale.subtitle', { name: party.name })
            : activeParty
              ? t('khata.creditSale.subtitle', { name: activeParty.name })
              : undefined
        }
        footer={
          <>
            <Button variant="outline" onClick={onClose} disabled={save.isPending} className="sm:flex-1">
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              form="khata-credit-sale-form"
              disabled={save.isPending || priced.length === 0 || activeParty === null}
              className="sm:flex-1"
            >
              <Icon name={save.isPending ? 'loader' : 'check'} className={cn('size-4', save.isPending && 'animate-spin')} />
              {save.isPending ? t('khata.creditSale.saving') : t('khata.creditSale.submit')}
            </Button>
          </>
        }
      >
        <form id="khata-credit-sale-form" onSubmit={handleSubmit} noValidate className="space-y-4">
          {!party && (
            <Select
              label={t('khata.creditSale.party')}
              placeholder={t('khata.creditSale.pickParty')}
              options={parties.map((candidate) => ({
                value: String(candidate.id),
                label: candidate.phone ? `${candidate.name} · ${candidate.phone}` : candidate.name,
              }))}
              value={picked ? String(picked.id) : ''}
              onChange={(value) => {
                const next = parties.find((candidate) => String(candidate.id) === value)
                if (!next) return
                setPicked(next)
                onPartyChange?.(next)
              }}
            />
          )}

          {activeParty && activeParty.balance > 0 && (
            <div className="rounded-card bg-surface-3 px-3 py-2.5 text-sm text-ink-soft">
              {t('khata.creditSale.currentBalance', {
                amount: formatCurrency(activeParty.balance, { decimals: 2 }),
              })}
            </div>
          )}

          <fieldset className="flex flex-col gap-2">
            <legend className="text-sm font-medium text-ink">{t('khata.creditSale.items')}</legend>
            <ul className="space-y-2">
              {lines.map((line, index) => {
                const entry = priced.find((candidate) => candidate.line === line)
                const chosen = line.productId ? byId.get(Number(line.productId)) : undefined
                return (
                  <li
                    key={index}
                    className="rounded-card border border-line bg-surface p-2.5"
                  >
                    <div className="flex flex-col gap-2">
                      <div className="flex items-start gap-2">
                        <div className="min-w-0 flex-1">
                          <Select
                            label={index === 0 ? t('khata.creditSale.product') : undefined}
                            placeholder={t('khata.creditSale.pickProduct')}
                            options={active.map((product) => ({
                              value: String(product.id),
                              label: `${product.name} · ${product.sku}`,
                              disabled: product.id !== Number(line.productId) && usedIds.has(product.id),
                            }))}
                            value={line.productId}
                            onChange={(value) => {
                              const product = byId.get(Number(value))
                              updateLine(index, {
                                productId: value,
                                rate: product ? String(product.sellingPrice) : '',
                              })
                            }}
                          />
                        </div>
                        {lines.length > 1 && (
                          <button
                            type="button"
                            onClick={() => removeLine(index)}
                            aria-label={t('khata.creditSale.removeLine')}
                            className="focus-ring mt-6 rounded-pill p-2 text-ink-soft transition-colors hover:bg-surface-3 hover:text-crimson-600"
                          >
                            <Icon name="trash" className="size-4" />
                          </button>
                        )}
                      </div>

                      <div className="flex items-end gap-2">
                        <Input
                          label={index === 0 ? t('khata.creditSale.qty') : undefined}
                          className="flex-1"
                          value={line.qty}
                          onChange={(event) => updateLine(index, { qty: event.target.value })}
                          inputMode="decimal"
                          disabled={!chosen}
                          aria-label={chosen ? t('khata.creditSale.qtyFor', { name: chosen.name }) : undefined}
                        />
                        <Input
                          label={index === 0 ? t('khata.creditSale.rate') : undefined}
                          className="flex-1"
                          value={line.rate}
                          onChange={(event) => updateLine(index, { rate: event.target.value })}
                          inputMode="decimal"
                          disabled={!chosen}
                          aria-label={chosen ? t('khata.creditSale.rateFor', { name: chosen.name }) : undefined}
                        />
                      </div>

                      {entry && (
                        <p className="text-right text-sm font-semibold tabular-nums text-ink">
                          {formatCurrency(entry.amount, { decimals: 2 })}
                        </p>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>

            <Button type="button" variant="ghost" size="sm" onClick={addLine} className="self-start">
              <Icon name="plus" className="size-4" />
              {t('khata.creditSale.addLine')}
            </Button>

            {errors.items && (
              <p role="alert" className="text-xs text-crimson-600">
                {errors.items}
              </p>
            )}
          </fieldset>

          <Input
            label={t('khata.creditSale.dueDate')}
            type="date"
            value={dueDate}
            onChange={(event) => setDueDate(event.target.value)}
            hint={
              activeParty
                ? t('khata.creditSale.dueDateHint', { days: activeParty.paymentTermsDays })
                : undefined
            }
          />

          <Textarea
            label={t('khata.creditSale.note')}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder={t('khata.creditSale.notePlaceholder')}
            rows={2}
          />

          <div className="flex items-center justify-between rounded-card bg-brand-50 px-3 py-2.5 dark:bg-brand-500/10">
            <span className="text-sm font-medium text-ink">{t('khata.creditSale.total')}</span>
            <span className="text-lg font-semibold tabular-nums text-ink">
              {formatCurrency(total, { decimals: 2 })}
            </span>
          </div>

          {activeParty && activeParty.creditLimit !== null && (
            <p className="text-xs text-ink-soft">
              {t('khata.creditSale.limitNote', {
                amount: formatCurrency(activeParty.creditLimit, { decimals: 2 }),
              })}
            </p>
          )}
        </form>
      </Sheet>

      <ConfirmDialog
        isOpen={limitWarning !== null}
        onClose={() => setLimitWarning(null)}
        onConfirm={() => save.mutate(true)}
        title={t('khata.creditSale.limitTitle')}
        message={limitWarning ?? ''}
        confirmLabel={t('khata.creditSale.override')}
        cancelLabel={t('common.cancel')}
        confirmVariant="danger"
        loading={save.isPending}
      />
    </>
  )
}
