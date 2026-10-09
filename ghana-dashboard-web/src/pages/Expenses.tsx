import { useCallback, useMemo, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { viewBlob } from '@/api/download'
import {
  PAYMENT_MODES,
  deleteExpense,
  fetchExpenseBill,
  fetchExpenseCategories,
  fetchExpenses,
  fetchExpensesByCategory,
} from '@/api/expenses'
import type { ExpenseCategory, ExpenseSummary, PaymentMode } from '@/api/expenses'
import { DateRangeControl } from '@/components/DateRangeControl'
import { DonutChart } from '@/components/charts/DonutChart'
import { ExpenseDrawer } from '@/components/expenses/ExpenseDrawer'
import { FALLBACK_COLOUR, categoryLabel } from '@/components/expenses/helpers'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  PageHeader,
  Select,
  Skeleton,
} from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { initialRange, isValidRange } from '@/utils/dateRange'
import type { DateRangeValue } from '@/utils/dateRange'
import { readEntryAction } from '@/utils/entryPoint'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'

const ICON_BUTTON = 'focus-ring rounded-pill p-2 text-ink-soft transition-colors hover:bg-surface-3'

function ListSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }, (_, i) => (
        <Skeleton key={i} className="h-16" />
      ))}
    </div>
  )
}

export function ExpensesPage() {
  const { t, language } = useLanguage()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [dateRange, setDateRange] = useState<DateRangeValue>(initialRange)
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [paymentMode, setPaymentMode] = useState<PaymentMode | ''>('')
  const [drawer, setDrawer] = useState<{ open: boolean; expenseId: number | null }>(() => ({
    // Home dashboard quick action: /expenses?action=expense opens the blank drawer. Seeded
    // from the URL rather than set in an effect, so the drawer is open on the first render.
    open: readEntryAction(searchParams) === 'expense',
    expenseId: null,
  }))
  const [pendingDelete, setPendingDelete] = useState<ExpenseSummary | null>(null)

  const { range } = dateRange
  const valid = isValidRange(range)
  const closeDrawer = useCallback(() => setDrawer((d) => ({ ...d, open: false })), [])

  const categories = useQuery({ queryKey: ['expense-categories'], queryFn: fetchExpenseCategories, staleTime: 300_000 })
  const expenses = useQuery({
    queryKey: ['expenses', 'list', range.from, range.to, categoryId, paymentMode],
    queryFn: () => fetchExpenses({ dateFrom: range.from, dateTo: range.to, categoryId, paymentMode }),
    enabled: valid,
    placeholderData: keepPreviousData,
  })
  const byCategory = useQuery({
    queryKey: ['expenses', 'by-category', range.from, range.to],
    queryFn: () => fetchExpensesByCategory(range.from, range.to),
    enabled: valid,
    placeholderData: keepPreviousData,
  })

  const categoryById = useMemo(
    () => new Map<number, ExpenseCategory>((categories.data ?? []).map((c) => [c.id, c])),
    [categories.data],
  )
  const nameOf = (id: number, fallback: string) => {
    const category = categoryById.get(id)
    return category ? categoryLabel(category, language) : fallback
  }

  const removal = useMutation({
    mutationFn: (id: number) => deleteExpense(id),
    onSuccess: async () => {
      toast.success(t('expenses.deleted'))
      setPendingDelete(null)
      await Promise.all(
        [['expenses'], ['pnl'], ['payments'], ['cash-book']].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      )
    },
    onError: (error) => toast.error(normalizeError(error).message || t('expenses.deleteFailed')),
  })

  async function openBill(id: number) {
    const tab = window.open('', '_blank')
    try {
      viewBlob(await fetchExpenseBill(id), tab, `bill-${id}`)
    } catch {
      tab?.close()
      toast.error(t('expenses.bill.failed'))
    }
  }

  const rows = expenses.data
  const total = (rows ?? []).reduce((sum, e) => sum + e.amount, 0)
  const slices = (byCategory.data ?? []).map((c) => ({
    id: c.categoryId,
    label: nameOf(c.categoryId, c.categoryName),
    value: c.amount,
    color: c.categoryColour ?? FALLBACK_COLOUR,
  }))
  const chips: { id: number | null; label: string; colour: string | null }[] = [
    { id: null, label: t('expenses.allCategories'), colour: null },
    ...(categories.data ?? []).map((c) => ({
      id: c.id,
      label: categoryLabel(c, language),
      colour: c.colour ?? FALLBACK_COLOUR,
    })),
  ]
  const modeOptions = [
    { value: '', label: t('expenses.allModes') },
    ...PAYMENT_MODES.map((mode) => ({ value: mode, label: t(`payment.mode.${mode}` as const) })),
  ]

  return (
    <div>
      <PageHeader
        title={t('expenses.title')}
        subtitle={t('expenses.subtitle')}
        actions={
          <Button onClick={() => setDrawer({ open: true, expenseId: null })}>
            <Icon name="plus" className="size-4" />
            {t('expenses.add')}
          </Button>
        }
      />

      <div className="mb-6">
        <DateRangeControl value={dateRange} onChange={setDateRange} />
      </div>

      {valid && (
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
          <Card className="self-start p-4">
            <h2 className="mb-4 text-base font-semibold text-ink">{t('expenses.byCategory')}</h2>
            {byCategory.isError ? (
              <ErrorState
                title={t('expenses.error.title')}
                message={t('expenses.error.body')}
                retryLabel={t('common.retry')}
                onRetry={() => void byCategory.refetch()}
                className="py-6"
              />
            ) : !byCategory.data ? (
              <Skeleton className="h-52" />
            ) : slices.length === 0 ? (
              <EmptyState
                icon={<Icon name="expenses" className="size-7" />}
                title={t('expenses.empty.title')}
                className="py-6"
              />
            ) : (
              <DonutChart slices={slices} />
            )}
          </Card>

          <Card className="p-4">
            <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
              <div>
                <p className="text-xs text-ink-soft">{t('expenses.total')}</p>
                <p className="text-2xl font-bold tabular-nums text-ink">{formatCurrency(total, { decimals: 2 })}</p>
                {rows && <p className="text-xs text-ink-soft">{t('expenses.count', { count: rows.length })}</p>}
              </div>
              <Select
                className="w-40"
                options={modeOptions}
                value={paymentMode}
                onChange={(value) => setPaymentMode(value as PaymentMode | '')}
              />
            </div>

            <div
              className="-mx-1 mb-4 flex gap-2 overflow-x-auto px-1 pb-1"
              role="group"
              aria-label={t('expenses.categoryFilter')}
            >
              {chips.map((chip) => (
                <button
                  key={chip.id ?? 'all'}
                  type="button"
                  aria-pressed={categoryId === chip.id}
                  onClick={() => setCategoryId(chip.id)}
                  className={cn(
                    'focus-ring inline-flex shrink-0 items-center gap-2 rounded-pill border px-3 py-1.5 text-sm transition-colors',
                    categoryId === chip.id
                      ? 'border-brand-600 bg-brand-600 text-white'
                      : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                  )}
                >
                  {chip.colour && (
                    <span
                      className="size-2.5 rounded-full ring-1 ring-white/70"
                      style={{ backgroundColor: chip.colour }}
                      aria-hidden="true"
                    />
                  )}
                  {chip.label}
                </button>
              ))}
            </div>

            {expenses.isError ? (
              <ErrorState
                icon={<Icon name="alert-circle" className="size-7" />}
                title={t('expenses.error.title')}
                message={t('expenses.error.body')}
                retryLabel={t('common.retry')}
                onRetry={() => void expenses.refetch()}
              />
            ) : !rows ? (
              <ListSkeleton />
            ) : rows.length === 0 ? (
              <EmptyState
                icon={<Icon name="expenses" className="size-7" />}
                title={t('expenses.empty.title')}
                description={t('expenses.empty.body')}
                action={
                  <Button size="sm" onClick={() => setDrawer({ open: true, expenseId: null })}>
                    <Icon name="plus" className="size-4" />
                    {t('expenses.add')}
                  </Button>
                }
              />
            ) : (
              <ul aria-label={t('expenses.list')} className="divide-y divide-line">
                {rows.map((expense) => (
                  <li key={expense.id} className="flex items-center gap-3 py-3">
                    <span
                      className="h-10 w-1.5 shrink-0 rounded-pill"
                      style={{ backgroundColor: expense.categoryColour ?? FALLBACK_COLOUR }}
                      aria-hidden="true"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-ink">{expense.title}</p>
                      <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-soft">
                        <span>{nameOf(expense.categoryId, expense.categoryName)}</span>
                        <span aria-hidden="true">·</span>
                        <span>{formatDate(expense.expenseDate, language)}</span>
                        <Badge size="sm">{t(`payment.mode.${expense.paymentMode}` as const)}</Badge>
                      </div>
                    </div>
                    <p className="shrink-0 text-sm font-bold tabular-nums text-ink">
                      {formatCurrency(expense.amount, { decimals: 2 })}
                    </p>
                    <div className="flex shrink-0 items-center">
                      {expense.hasBill ? (
                        <button
                          type="button"
                          aria-label={t('expenses.bill.view')}
                          title={t('expenses.bill.view')}
                          onClick={() => void openBill(expense.id)}
                          className={cn(ICON_BUTTON, 'text-brand-600 dark:text-brand-300')}
                        >
                          <Icon name="file" className="size-4" />
                        </button>
                      ) : (
                        <span className="p-2 text-ink-soft/40" title={t('expenses.bill.none')} aria-label={t('expenses.bill.none')}>
                          <Icon name="file" className="size-4" />
                        </span>
                      )}
                      <button
                        type="button"
                        aria-label={t('common.edit')}
                        title={t('common.edit')}
                        onClick={() => setDrawer({ open: true, expenseId: expense.id })}
                        className={ICON_BUTTON}
                      >
                        <Icon name="pencil" className="size-4" />
                      </button>
                      <button
                        type="button"
                        aria-label={t('common.delete')}
                        title={t('common.delete')}
                        onClick={() => setPendingDelete(expense)}
                        className={cn(ICON_BUTTON, 'hover:text-crimson-600')}
                      >
                        <Icon name="trash" className="size-4" />
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      )}

      <ExpenseDrawer
        open={drawer.open}
        expenseId={drawer.expenseId}
        categories={categories.data ?? []}
        onClose={closeDrawer}
      />

      <ConfirmDialog
        isOpen={pendingDelete !== null}
        onClose={() => setPendingDelete(null)}
        onConfirm={() => pendingDelete && removal.mutate(pendingDelete.id)}
        title={t('expenses.delete.title')}
        message={t('expenses.delete.message', { title: pendingDelete?.title ?? '' })}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        confirmVariant="danger"
        loading={removal.isPending}
      />
    </div>
  )
}

