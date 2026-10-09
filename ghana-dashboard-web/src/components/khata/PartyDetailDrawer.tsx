import { useState } from 'react'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { fetchLedger, fetchReminder, fetchStatementPdf } from '@/api/khata'
import type { Ledger, Party } from '@/api/khata'
import { viewBlob } from '@/api/download'
import { Badge, Button, ErrorState, Sheet, Skeleton } from '@/components/ui'
import { useIsOwner } from '@/auth/useCan'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { LedgerTimeline } from '@/components/khata/LedgerTimeline'
import { TYPE_VARIANT } from '@/components/khata/helpers'

function CreditMeter({ party }: { party: Party }) {
  const { t } = useLanguage()
  if (party.creditLimit === null) return null
  const used = Math.min((party.balance / party.creditLimit) * 100, 100)
  return (
    <div
      className="mt-3 h-1.5 overflow-hidden rounded-full bg-surface-2"
      role="meter"
      aria-valuenow={Math.round(used)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={t('khata.detail.creditUsed')}
    >
      <div
        className={cn(
          'h-full rounded-full transition-all',
          party.balance > party.creditLimit ? 'bg-crimson-500' : 'bg-clay-500',
        )}
        style={{ width: `${used}%` }}
      />
    </div>
  )
}

function PartyPanel({
  data,
  onCreditSale,
  onPayment,
}: {
  data: Ledger
  onCreditSale: (party: Party) => void
  onPayment: (party: Party) => void
}) {
  const { t, language } = useLanguage()
  const isOwner = useIsOwner()
  const party = data.party
  const owed = party.balance > 0

  return (
    <div className="space-y-5">
      <div className="rounded-card bg-surface-3 p-4">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={TYPE_VARIANT[party.type]} size="sm">
            {t(`khata.type.${party.type}`)}
          </Badge>
          {party.overdue && (
            <Badge variant="danger" size="sm" dot>
              {t('khata.detail.overdue', { days: party.overdueDays })}
            </Badge>
          )}
        </div>

        <p className="mt-3 text-xs text-ink-soft">{t('khata.detail.balance')}</p>
        <p
          className={cn(
            'text-3xl font-semibold tabular-nums',
            owed ? 'text-clay-600 dark:text-clay-400' : 'text-brand-600 dark:text-brand-400',
          )}
        >
          {formatCurrency(party.balance, { decimals: 2 })}
        </p>

        <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
          <div>
            <dt className="text-ink-soft">{t('khata.detail.phone')}</dt>
            <dd className="font-medium text-ink">{party.phone}</dd>
          </div>
          <div>
            <dt className="text-ink-soft">{t('khata.detail.terms')}</dt>
            <dd className="font-medium text-ink">
              {t('khata.detail.days', { count: party.paymentTermsDays })}
            </dd>
          </div>
          {party.dueDate && (
            <div>
              <dt className="text-ink-soft">{t('khata.detail.dueDate')}</dt>
              <dd className="font-medium text-ink">{formatDate(party.dueDate, language)}</dd>
            </div>
          )}
          {party.lastPaymentDate && (
            <div>
              <dt className="text-ink-soft">{t('khata.detail.lastPayment')}</dt>
              <dd className="font-medium text-ink">
                {formatDate(party.lastPaymentDate, language)}
              </dd>
            </div>
          )}
          {party.overdueAmount > 0 && (
            <div className="col-span-2">
              <dt className="text-ink-soft">{t('khata.detail.overdueAmount')}</dt>
              <dd className="font-medium tabular-nums text-crimson-600 dark:text-crimson-400">
                {formatCurrency(party.overdueAmount, { decimals: 2 })}
              </dd>
            </div>
          )}
          {party.creditLimit !== null && (
            <div className="col-span-2">
              <dt className="text-ink-soft">{t('khata.detail.creditLimit')}</dt>
              <dd className="font-medium tabular-nums text-ink">
                {formatCurrency(party.creditLimit, { decimals: 2 })}
                {' · '}
                {t('khata.detail.available', {
                  amount: formatCurrency(party.availableCredit ?? 0, { decimals: 2 }),
                })}
              </dd>
            </div>
          )}
          {party.address && (
            <div className="col-span-2">
              <dt className="text-ink-soft">{t('khata.detail.address')}</dt>
              <dd className="text-ink">{party.address}</dd>
            </div>
          )}
        </dl>

        <CreditMeter party={party} />
      </div>

      {owed && (
        <div className="grid grid-cols-2 gap-2">
          <Button onClick={() => onCreditSale(party)}>
            <Icon name="plus" className="size-4" />
            {t('khata.actions.creditSale')}
          </Button>
          <Button variant="secondary" onClick={() => onPayment(party)}>
            <Icon name="payments" className="size-4" />
            {t('khata.actions.payment')}
          </Button>
        </div>
      )}

      {!isOwner && <p className="text-xs text-ink-soft">{t('khata.detail.ownerNote')}</p>}

      <div>
        <h3 className="mb-3 text-sm font-semibold text-ink">{t('khata.ledger.title')}</h3>
        <LedgerTimeline entries={data.entries} language={language} />
      </div>
    </div>
  )
}

export function PartyDetailDrawer({
  partyId,
  onClose,
  onEdit,
  onCreditSale,
  onPayment,
}: {
  partyId: number | null
  onClose: () => void
  onEdit: (party: Party) => void
  onCreditSale: (party: Party) => void
  onPayment: (party: Party) => void
}) {
  const { t, language } = useLanguage()
  const [busy, setBusy] = useState<'statement' | 'reminder' | null>(null)

  const ledger = useQuery({
    queryKey: ['khata', 'ledger', partyId],
    queryFn: () => fetchLedger(partyId as number),
    enabled: partyId !== null,
  })

  const data = ledger.data
  const party = data?.party

  async function openStatement() {
    if (partyId === null) return
    setBusy('statement')
    // Open the tab before awaiting: browsers block a window created later.
    const tab = window.open('', '_blank')
    try {
      viewBlob(await fetchStatementPdf(partyId), tab, `khata-statement-${partyId}.pdf`)
    } catch (error) {
      tab?.close()
      toast.error(normalizeError(error).message || t('khata.statement.failed'))
    } finally {
      setBusy(null)
    }
  }

  async function sendReminder() {
    if (partyId === null) return
    setBusy('reminder')
    try {
      const reminder = await fetchReminder(partyId, language === 'hi' ? 'hi' : 'en')
      // The server builds the wa.me link with the message already encoded.
      window.open(reminder.url, '_blank', 'noopener,noreferrer')
    } catch (error) {
      toast.error(normalizeError(error).message || t('khata.reminder.failed'))
    } finally {
      setBusy(null)
    }
  }

  const footer: ReactNode = party ? (
    <>
      <Button variant="outline" size="sm" onClick={() => onEdit(party)} className="sm:flex-1">
        <Icon name="pencil" className="size-4" />
        {t('khata.actions.edit')}
      </Button>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => void openStatement()}
        disabled={busy === 'statement'}
        className="sm:flex-1"
      >
        <Icon
          name={busy === 'statement' ? 'loader' : 'file'}
          className={cn('size-4', busy === 'statement' && 'animate-spin')}
        />
        {t('khata.actions.statement')}
      </Button>
      {party.balance > 0 && (
        <Button
          variant="secondary"
          size="sm"
          onClick={() => void sendReminder()}
          disabled={busy === 'reminder'}
          className="sm:flex-1"
        >
          <Icon
            name={busy === 'reminder' ? 'loader' : 'khata'}
            className={cn('size-4', busy === 'reminder' && 'animate-spin')}
          />
          {t('khata.actions.reminder')}
        </Button>
      )}
      {party.balance > 0 && (
        <>
          <Button size="sm" onClick={() => onCreditSale(party)} className="sm:flex-1">
            <Icon name="plus" className="size-4" />
            {t('khata.actions.creditSale')}
          </Button>
          <Button size="sm" onClick={() => onPayment(party)} className="sm:flex-1">
            <Icon name="payments" className="size-4" />
            {t('khata.actions.payment')}
          </Button>
        </>
      )}
    </>
  ) : null

  return (
    <Sheet
      open={partyId !== null}
      onClose={onClose}
      title={party?.name ?? t('khata.detail.title')}
      subtitle={party ? t('khata.detail.subtitle', { name: party.name }) : undefined}
      footer={footer}
    >
      {ledger.isError ? (
        <ErrorState
          icon={<Icon name="alert-circle" className="size-7" />}
          title={t('khata.error.title')}
          message={t('khata.error.body')}
          retryLabel={t('common.retry')}
          onRetry={() => void ledger.refetch()}
        />
      ) : !data ? (
        <div className="space-y-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-40" />
        </div>
      ) : (
        <PartyPanel
          data={data}
          onCreditSale={onCreditSale}
          onPayment={onPayment}
        />
      )}
    </Sheet>
  )
}
