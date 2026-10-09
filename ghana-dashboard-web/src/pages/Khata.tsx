import { useCallback, useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { keepPreviousData } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import {
  PARTY_TYPES,
  fetchKhataSummary,
  fetchParties,
  fetchProducts,
} from '@/api/khata'
import type { Party, PartyType } from '@/api/khata'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  PageHeader,
  Select,
  Skeleton,
  StatCard,
} from '@/components/ui'
import { CreditSaleDrawer } from '@/components/khata/CreditSaleDrawer'
import { PartyDetailDrawer } from '@/components/khata/PartyDetailDrawer'
import { PartyFormDrawer } from '@/components/khata/PartyFormDrawer'
import { PaymentDrawer } from '@/components/khata/PaymentDrawer'
import { TYPE_VARIANT } from '@/components/khata/helpers'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { readEntryAction } from '@/utils/entryPoint'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'

type ListFilter = 'ALL' | 'OWED' | 'OVERDUE'

const INVALIDATED = [
  ['khata'],
  ['sales'],
  ['inventory'],
  ['pnl'],
  ['payments'],
  ['cash-book'],
  ['customers'],
]

function PartyRow({
  party,
  onOpen,
}: {
  party: Party
  onOpen: (id: number) => void
}) {
  const { t, language } = useLanguage()
  const owed = party.balance > 0

  return (
    <li>
      <button
        type="button"
        onClick={() => onOpen(party.id)}
        className={cn(
          'focus-ring flex w-full items-center gap-3 px-4 py-3 text-left transition-colors',
          // Overdue parties read as clay so they stand out while scrolling.
          party.overdue
            ? 'bg-clay-50 hover:bg-clay-100 dark:bg-clay-500/10 dark:hover:bg-clay-500/20'
            : 'hover:bg-surface-3',
        )}
      >
        <span
          className={cn(
            'flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold',
            party.overdue
              ? 'bg-clay-500 text-white'
              : owed
                ? 'bg-clay-100 text-clay-700 dark:bg-clay-500/20 dark:text-clay-300'
                : 'bg-brand-50 text-brand-600 dark:bg-brand-500/20 dark:text-brand-300',
          )}
          aria-hidden="true"
        >
          {party.name.trim().charAt(0).toUpperCase() || '?'}
        </span>

        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-2">
            <span className="truncate text-sm font-medium text-ink">{party.name}</span>
            <Badge variant={TYPE_VARIANT[party.type]} size="sm">
              {t(`khata.type.${party.type}`)}
            </Badge>
            {party.overdue && (
              <Badge variant="danger" size="sm" dot>
                {t('khata.list.overdueDays', { days: party.overdueDays })}
              </Badge>
            )}
          </span>
          <span className="mt-0.5 block truncate text-xs text-ink-soft">
            {party.phone}
            {party.dueDate && owed ? ` · ${t('khata.list.due', { date: formatDate(party.dueDate, language) })}` : ''}
            {party.lastPaymentDate
              ? ` · ${t('khata.list.lastPayment', { date: formatDate(party.lastPaymentDate, language) })}`
              : ''}
          </span>
        </span>

        <span className="shrink-0 text-right">
          <span
            className={cn(
              'block text-sm font-semibold tabular-nums',
              party.overdue
                ? 'text-crimson-600 dark:text-crimson-400'
                : owed
                  ? 'text-clay-600 dark:text-clay-400'
                  : 'text-brand-600 dark:text-brand-400',
            )}
          >
            {owed ? formatCurrency(party.balance, { decimals: 2 }) : t('khata.list.settled')}
          </span>
          {party.creditLimit !== null && owed && (
            <span className="mt-0.5 block text-xs text-ink-soft">
              {t('khata.list.ofLimit', {
                percent: Math.round(Math.min((party.balance / party.creditLimit) * 100, 999)),
              })}
            </span>
          )}
        </span>

        <Icon name="chevron-right" className="size-4 shrink-0 text-ink-soft" aria-hidden="true" />
      </button>
    </li>
  )
}

export function KhataPage() {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()

  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState<PartyType | ''>('')
  const [listFilter, setListFilter] = useState<ListFilter>('OWED')

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Party | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [creditParty, setCreditParty] = useState<Party | null>(null)
  // Home dashboard quick action: /khata?action=credit opens the credit drawer with the party
  // still to be picked inside it.
  const [creditOpen, setCreditOpen] = useState(() => readEntryAction(searchParams) === 'credit')
  const [paymentParty, setPaymentParty] = useState<Party | null>(null)

  const closeDetail = useCallback(() => setDetailId(null), [])

  const summary = useQuery({
    queryKey: ['khata', 'summary'],
    queryFn: fetchKhataSummary,
  })

  const parties = useQuery({
    queryKey: ['khata', 'parties', typeFilter, listFilter === 'OVERDUE'],
    queryFn: () =>
      fetchParties({
        type: typeFilter,
        overdue: listFilter === 'OVERDUE' ? true : undefined,
        withBalance: listFilter !== 'ALL' ? true : undefined,
      }),
    placeholderData: keepPreviousData,
  })

  // Search is client-side: the API has no paginated name index for this size of list.
  const visible = useMemo(() => {
    const needle = search.trim().toLowerCase()
    const rows = parties.data ?? []
    if (!needle) return rows
    return rows.filter(
      (party) => party.name.toLowerCase().includes(needle) || party.phone.includes(needle),
    )
  }, [parties.data, search])

  const products = useQuery({
    queryKey: ['products'],
    queryFn: fetchProducts,
    staleTime: 5 * 60_000,
  })

  // Unfiltered party list for the credit-sale picker, which must offer every active
  // customer regardless of the filters currently applied to the list below it.
  const allParties = useQuery({
    queryKey: ['khata', 'parties', 'all'],
    queryFn: () => fetchParties({}),
    staleTime: 5 * 60_000,
  })

  const invalidateAll = useCallback(async () => {
    await Promise.all(INVALIDATED.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
  }, [queryClient])

  function openNew() {
    setEditing(null)
    setFormOpen(true)
  }

  function openEdit(party: Party) {
    setEditing(party)
    setFormOpen(true)
  }

  const counts = useMemo(() => {
    const rows = summary.data
    return {
      outstanding: rows?.totalOutstanding ?? 0,
      parties: rows?.partiesWithBalance ?? 0,
      overdue: rows?.overdueAmount ?? 0,
      overdueParties: rows?.overdueParties ?? 0,
    }
  }, [summary.data])

  return (
    <div>
      <PageHeader
        title={t('khata.title')}
        subtitle={t('khata.subtitle')}
        actions={
          <Button onClick={openNew}>
            <Icon name="plus" className="size-4" />
            {t('khata.form.newTitle')}
          </Button>
        }
      />

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard
          icon={<Icon name="wallet" className="size-5" />}
          label={t('khata.stat.outstanding')}
          value={formatCurrency(counts.outstanding, { decimals: 0 })}
          iconClassName="bg-clay-50 text-clay-600 dark:bg-clay-500/20 dark:text-clay-300"
        />
        <StatCard
          icon={<Icon name="khata" className="size-5" />}
          label={t('khata.stat.parties')}
          value={counts.parties}
          trend={t('khata.stat.partiesTrend')}
        />
        <StatCard
          icon={<Icon name="alert-circle" className="size-5" />}
          label={t('khata.stat.overdue')}
          value={formatCurrency(counts.overdue, { decimals: 0 })}
          iconClassName="bg-crimson-50 text-crimson-600 dark:bg-crimson-500/20 dark:text-crimson-300"
        />
        <StatCard
          icon={<Icon name="payments" className="size-5" />}
          label={t('khata.stat.collected')}
          value={formatCurrency(summary.data?.collectedThisMonth ?? 0, { decimals: 0 })}
          iconClassName="bg-brand-50 text-brand-600 dark:bg-brand-500/20 dark:text-brand-300"
        />
      </div>

      {summary.data && summary.data.topDebtors.length > 0 && (
        <Card className="mb-6 p-4">
          <div className="mb-3 flex items-center justify-between gap-3">
            <h2 className="text-sm font-semibold text-ink">{t('khata.topDebtors.title')}</h2>
            <span className="text-xs text-ink-soft">{t('khata.topDebtors.subtitle')}</span>
          </div>
          <ul className="flex flex-wrap gap-2">
            {summary.data.topDebtors.map((party) => (
              <li key={party.id}>
                <button
                  type="button"
                  onClick={() => setDetailId(party.id)}
                  className={cn(
                    'focus-ring rounded-pill border px-3 py-1.5 text-sm transition-colors',
                    party.overdue
                      ? 'border-crimson-500/40 bg-crimson-50 text-crimson-700 hover:bg-crimson-100 dark:bg-crimson-500/10 dark:text-crimson-300'
                      : 'border-line bg-surface-2 text-ink hover:bg-surface-3',
                  )}
                >
                  <span className="font-medium">{party.name}</span>
                  <span className="ml-2 tabular-nums">{formatCurrency(party.balance, { decimals: 0 })}</span>
                </button>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card className="p-4">
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
          <div className="min-w-0 flex-1">
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('khata.list.search')}
              aria-label={t('khata.list.search')}
              type="search"
            />
          </div>

          <div
            className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1"
            role="group"
            aria-label={t('khata.list.filter')}
          >
            {(['OWED', 'OVERDUE', 'ALL'] as const).map((option) => (
              <button
                key={option}
                type="button"
                aria-pressed={listFilter === option}
                onClick={() => setListFilter(option)}
                className={cn(
                  'focus-ring shrink-0 rounded-pill border px-3 py-1.5 text-sm transition-colors',
                  listFilter === option
                    ? 'border-brand-600 bg-brand-600 text-white'
                    : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
                )}
              >
                {t(`khata.list.filter.${option}`)}
              </button>
            ))}
          </div>

          <Select
            className="sm:w-48"
            options={[
              { value: '', label: t('khata.allTypes') },
              ...PARTY_TYPES.map((option) => ({ value: option, label: t(`khata.type.${option}`) })),
            ]}
            value={typeFilter}
            onChange={(value) => setTypeFilter(value as PartyType | '')}
          />
        </div>

        {parties.isError ? (
          <ErrorState
            icon={<Icon name="alert-circle" className="size-7" />}
            title={t('khata.error.title')}
            message={t('khata.error.body')}
            retryLabel={t('common.retry')}
            onRetry={() => void parties.refetch()}
          />
        ) : !parties.data ? (
          <ul className="divide-y divide-line">
            {Array.from({ length: 5 }, (_, index) => (
              <li key={index} className="px-4 py-3">
                <Skeleton className="h-10" />
              </li>
            ))}
          </ul>
        ) : visible.length === 0 ? (
          <EmptyState
            icon={<Icon name="khata" className="size-7" />}
            title={t('khata.empty.title')}
            description={t('khata.empty.body')}
            action={
              <Button size="sm" onClick={openNew}>
                <Icon name="plus" className="size-4" />
                {t('khata.form.newTitle')}
              </Button>
            }
          />
        ) : (
          <ul className="divide-y divide-line">
            {visible.map((party) => (
              <PartyRow key={party.id} party={party} onOpen={setDetailId} />
            ))}
          </ul>
        )}
      </Card>

      <PartyFormDrawer
        open={formOpen}
        party={editing}
        onClose={() => setFormOpen(false)}
        onSaved={() => void invalidateAll()}
      />

      <PartyDetailDrawer
        partyId={detailId}
        onClose={closeDetail}
        onEdit={openEdit}
        onCreditSale={(party) => {
          closeDetail()
          setCreditParty(party)
          setCreditOpen(true)
        }}
        onPayment={(party) => {
          closeDetail()
          setPaymentParty(party)
        }}
      />

      <CreditSaleDrawer
        open={creditOpen}
        party={creditParty}
        parties={allParties.data ?? []}
        products={products.data ?? []}
        onPartyChange={setCreditParty}
        onClose={() => setCreditOpen(false)}
        onDone={invalidateAll}
      />

      <PaymentDrawer
        open={paymentParty !== null}
        party={paymentParty}
        onClose={() => setPaymentParty(null)}
        onDone={invalidateAll}
      />
    </div>
  )
}
