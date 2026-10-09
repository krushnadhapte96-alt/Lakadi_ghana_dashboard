import type { ReactNode } from 'react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { fetchCustomer, fetchWhatsappRefill } from '@/api/customers'
import type { CustomerAddress, CustomerDetail } from '@/api/customers'
import { normalizeError } from '@/api/axios'
import { AddAddressDrawer } from '@/components/customers/AddAddressDrawer'
import { AddNoteDrawer } from '@/components/customers/AddNoteDrawer'
import { CustomerFormDrawer } from '@/components/customers/CustomerFormDrawer'
import { historyVariant, paidVariant, refillDays } from '@/components/customers/helpers'
import { Badge, Button, ErrorState, Sheet, Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { saleHrefForCustomer } from '@/utils/posPrefill'
import { cn } from '@/utils/cn'

interface CustomerDetailDrawerProps {
  customerId: number | null
  onClose: () => void
}

function telHref(phone: string | null): string | null {
  if (!phone) return null
  const digits = phone.replace(/\D/g, '')
  return digits ? `tel:+${digits}` : null
}

function addressLines(address: CustomerAddress): string[] {
  return [
    address.addressLine1,
    address.addressLine2,
    [address.city, address.state, address.pincode].filter(Boolean).join(' '),
    address.landmark,
  ].filter((line): line is string => Boolean(line && line.trim()))
}

export function CustomerDetailDrawer({ customerId, onClose }: CustomerDetailDrawerProps) {
  const { t } = useLanguage()
  const navigate = useNavigate()
  const [editOpen, setEditOpen] = useState(false)
  const [noteOpen, setNoteOpen] = useState(false)
  const [addressOpen, setAddressOpen] = useState(false)

  const query = useQuery({
    queryKey: ['customer', customerId],
    queryFn: () => fetchCustomer(customerId as number),
    enabled: customerId !== null,
  })

  const reminder = useMutation({
    mutationFn: () => fetchWhatsappRefill(customerId as number),
    onSuccess: (result) => {
      window.open(result.url, '_blank', 'noopener,noreferrer')
    },
    onError: (error) =>
      toast.error(normalizeError(error).message || t('customers.detail.whatsappFailed')),
  })

  const customer = query.data
  const startSale = () => {
    if (!customer) return
    onClose()
    navigate(saleHrefForCustomer(customer.id))
  }

  return (
    <>
      <Sheet
        open={customerId !== null}
        onClose={onClose}
        title={customer?.name ?? t('customers.detail.title')}
        subtitle={customer?.phone ?? undefined}
        className="sm:w-[38rem]"
        footer={
          customer ? (
            <>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setEditOpen(true)}
              >
                <Icon name="pencil" className="size-4" />
                {t('common.edit')}
              </Button>
              <Button variant="secondary" size="sm" onClick={() => setAddressOpen(true)}>
                <Icon name="map-pin" className="size-4" />
                {t('customers.actions.addAddress')}
              </Button>
              <Button size="sm" onClick={() => setNoteOpen(true)}>
                <Icon name="note" className="size-4" />
                {t('customers.actions.addNote')}
              </Button>
            </>
          ) : undefined
        }
      >
        {query.isPending && (
          <div className="flex flex-col gap-4 p-5">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-28 w-full" />
            <Skeleton className="h-48 w-full" />
          </div>
        )}

        {query.isError && (
          <ErrorState
            title={t('customers.error.title')}
            message={normalizeError(query.error).message || t('customers.error.body')}
            onRetry={() => query.refetch()}
            retryLabel={t('common.retry')}
          />
        )}

        {customer && (
          <>
            {/* Pinned above the scrolling body so the three contact actions stay reachable. */}
            <div className="sticky top-0 z-10 grid grid-cols-3 gap-2 border-b border-line bg-surface-2 px-5 py-3">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => reminder.mutate()}
                disabled={reminder.isPending || !customer.phone}
              >
                <Icon name="message" className="size-4" />
                {t('customers.actions.whatsapp')}
              </Button>
              <CallButton phone={customer.phone} />
              <Button size="sm" onClick={startSale}>
                <Icon name="sale" className="size-4" />
                {t('customers.actions.newSale')}
              </Button>
            </div>

            <CustomerPanel customer={customer} />
          </>
        )}
      </Sheet>

      {customer && (
        <>
          <CustomerFormDrawer
            open={editOpen}
            onClose={() => setEditOpen(false)}
            customer={customer}
          />
          <AddNoteDrawer
            open={noteOpen}
            onClose={() => setNoteOpen(false)}
            customerId={customer.id}
            customerName={customer.name}
          />
          <AddAddressDrawer
            open={addressOpen}
            onClose={() => setAddressOpen(false)}
            customerId={customer.id}
            customerName={customer.name}
          />
        </>
      )}
    </>
  )
}

function CallButton({ phone }: { phone: string | null }) {
  const { t } = useLanguage()
  const href = telHref(phone)
  const className =
    'focus-ring inline-flex h-8 items-center justify-center gap-2 rounded-card border border-line bg-surface-2 px-3 text-xs font-semibold text-ink shadow-soft transition-colors hover:bg-surface-3'
  const label = (
    <>
      <Icon name="phone" className="size-4" />
      {t('customers.actions.call')}
    </>
  )
  if (!href) {
    return (
      <Button variant="secondary" size="sm" disabled>
        {label}
      </Button>
    )
  }
  return (
    <a href={href} className={className}>
      {label}
    </a>
  )
}

function StatTile({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-card border border-line bg-surface p-3">
      <p className="text-[11px] uppercase tracking-wide text-ink-soft">{label}</p>
      <p className="mt-1 text-sm font-bold text-ink">{value}</p>
      {hint && <p className="mt-0.5 text-[11px] text-ink-soft">{hint}</p>}
    </div>
  )
}

function Section({
  title,
  count,
  children,
}: {
  title: string
  count?: number
  children: ReactNode
}) {
  return (
    <section className="border-t border-line px-5 py-4">
      <div className="mb-3 flex items-center gap-2">
        <h3 className="text-sm font-semibold text-ink">{title}</h3>
        {count != null && count > 0 && (
          <span className="rounded-full bg-surface-3 px-2 py-0.5 text-[11px] font-semibold text-ink-soft">
            {count}
          </span>
        )}
      </div>
      {children}
    </section>
  )
}

function CustomerPanel({ customer }: { customer: CustomerDetail }) {
  const { t, language } = useLanguage()
  const days = refillDays(customer.predictedRefillDate)
  const active = customer.addresses.filter((address) => address.active)

  return (
    <>
      <ContactFacts customer={customer} />

      <div className="grid grid-cols-2 gap-3 px-5 py-4">
        <StatTile label={t('customers.detail.totalSpent')} value={formatCurrency(customer.totalSpent)} />
        <StatTile label={t('customers.detail.totalOrders')} value={String(customer.totalOrders)} />
        <StatTile
          label={t('customers.detail.lastPurchase')}
          value={
            customer.lastPurchaseAt
              ? formatDate(customer.lastPurchaseAt, language)
              : t('customers.detail.never')
          }
          hint={
            customer.daysSinceLastPurchase === null
              ? undefined
              : t('customers.detail.daysAgo', { days: customer.daysSinceLastPurchase })
          }
        />
        <StatTile
          label={t('customers.detail.avgGap')}
          value={
            customer.avgGapDays === null
              ? t('customers.detail.needTwo')
              : t('customers.detail.days', { days: Math.round(customer.avgGapDays) })
          }
          hint={
            customer.predictedRefillDate
              ? t('customers.detail.nextRefill', {
                  date: formatDate(customer.predictedRefillDate, language),
                })
              : undefined
          }
        />
      </div>

      <div className="flex flex-wrap gap-2 px-5 pb-4">
        {customer.refillDue && (
          <Badge variant="clay" dot>
            {t('customers.detail.refillDue')}
          </Badge>
        )}
        {days !== null && !customer.refillDue && days >= 0 && days <= 7 && (
          <Badge variant="warning" dot>
            {t('customers.detail.refillSoon', { days })}
          </Badge>
        )}
        {customer.favouriteProductName && (
          <Badge variant="primary">
            {t('customers.detail.favourite', { product: customer.favouriteProductName })}
          </Badge>
        )}
        {customer.khataBalance !== null && customer.khataBalance > 0 && (
          <Badge variant="danger">
            {t('customers.detail.khataDue', { amount: formatCurrency(customer.khataBalance) })}
          </Badge>
        )}
        {customer.preferredLanguage === 'hi' && (
          <Badge variant="info">{t('customers.detail.prefersHindi')}</Badge>
        )}
      </div>

      {customer.tags.length > 0 && (
        <div className="flex flex-wrap gap-1.5 px-5 pb-4">
          {customer.tags.map((tag) => (
            <span
              key={tag}
              className="rounded-pill bg-surface-3 px-2.5 py-0.5 text-[11px] font-medium text-ink-soft"
            >
              {tag}
            </span>
          ))}
        </div>
      )}

      <Section title={t('customers.detail.addresses')} count={active.length}>
        {active.length === 0 ? (
          <p className="text-sm text-ink-soft">{t('customers.detail.noAddresses')}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {active.map((address) => (
              <li
                key={address.id}
                className={cn(
                  'rounded-card border px-3 py-2.5',
                  address.isDefault ? 'border-brand-200 bg-brand-50' : 'border-line bg-surface',
                )}
              >
                <div className="flex items-center gap-2">
                  <Icon name="map-pin" className="size-3.5 text-ink-soft" />
                  <span className="text-xs font-semibold uppercase tracking-wide text-ink-soft">
                    {t(`customers.address.type.${address.addressType}`)}
                  </span>
                  {address.isDefault && (
                    <Badge variant="primary" size="sm">
                      {t('customers.address.defaultBadge')}
                    </Badge>
                  )}
                </div>
                <p className="mt-1 text-sm text-ink">
                  {addressLines(address).map((line, index) => (
                    <span key={line} className={cn('block', index > 0 && 'text-ink-soft')}>
                      {line}
                    </span>
                  ))}
                </p>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section title={t('customers.detail.notes')} count={customer.notes.length}>
        {customer.notes.length === 0 ? (
          <p className="text-sm text-ink-soft">{t('customers.detail.noNotes')}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {customer.notes.map((note) => (
              <li key={note.id} className="rounded-card border border-line bg-surface px-3 py-2.5">
                <p className="text-sm text-ink">{note.note}</p>
                <p className="mt-1 text-[11px] text-ink-soft">
                  {note.createdByName} · {formatDate(note.createdAt, language)}
                </p>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section title={t('customers.detail.history')} count={customer.history.length}>
        {customer.history.length === 0 ? (
          <p className="text-sm text-ink-soft">{t('customers.detail.noHistory')}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {customer.history.map((item) => (
              <li
                key={`${item.type}-${item.id}`}
                className="rounded-card border border-line bg-surface px-3 py-2.5"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 text-sm font-medium text-ink">
                      <Icon
                        name={item.type === 'SALE' ? 'sale' : 'sales'}
                        className="size-3.5 text-ink-soft"
                      />
                      <span className="truncate">{item.billNo}</span>
                    </p>
                    <p className="mt-0.5 text-[11px] text-ink-soft">
                      {formatDate(item.date, language)}
                      {item.itemsSummary ? ` · ${item.itemsSummary}` : ''}
                    </p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="text-sm font-semibold text-ink">{formatCurrency(item.amount)}</p>
                    <div className="mt-1 flex justify-end gap-1">
                      <Badge variant={historyVariant(item)} size="sm">
                        {item.status}
                      </Badge>
                      {item.paymentStatus && (
                        <Badge variant={paidVariant(item.paymentStatus)} size="sm">
                          {item.paymentStatus}
                        </Badge>
                      )}
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Section>
    </>
  )
}

function ContactFacts({ customer }: { customer: CustomerDetail }) {
  const { t } = useLanguage()
  return (
    <div className="flex flex-col gap-1 border-b border-line px-5 py-4 text-sm">
      {customer.phone ? (
        <p className="flex items-center gap-2 text-ink">
          <Icon name="phone" className="size-3.5 text-ink-soft" />
          {customer.phone}
        </p>
      ) : (
        <p className="text-ink-soft">{t('customers.detail.noPhone')}</p>
      )}
      {customer.email && (
        <p className="flex items-center gap-2 text-ink-soft">
          <Icon name="message" className="size-3.5" />
          {customer.email}
        </p>
      )}
      {customer.gstNo && (
        <p className="text-ink-soft">{t('customers.detail.gst', { gst: customer.gstNo })}</p>
      )}
    </div>
  )
}
