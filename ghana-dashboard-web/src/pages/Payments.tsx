import { useMemo, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { saveBlob } from '@/api/download'
import {
  PAYMENT_MODES,
  PAYMENT_SOURCES,
  createPayment,
  exportPaymentsCsv,
  fetchPayments,
  type PaymentDirection,
  type PaymentFilters,
  type PaymentInput,
  type PaymentMode,
  type PaymentSource,
} from '@/api/payments'
import { Badge, Button, Card, EmptyState, ErrorState, Input, Modal, PageHeader, Select, Skeleton } from '@/components/ui'
import { cn } from '@/utils/cn'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { useLanguage } from '@/i18n'

const SOURCE_LABELS: Record<PaymentSource, string> = {
  SALE: 'Sale',
  ORDER: 'Order',
  KHATA: 'Khata',
  KHALI_SALE: 'Khali sale',
  SUPPLIER: 'Supplier',
  EXPENSE: 'Expense',
  REFUND: 'Refund',
  OTHER: 'Other',
}

function isoDate(offsetDays = 0): string {
  const d = new Date()
  d.setDate(d.getDate() - offsetDays)
  return d.toISOString().slice(0, 10)
}

export function PaymentsPage() {
  const { language } = useLanguage()
  const queryClient = useQueryClient()

  const [filters, setFilters] = useState<PaymentFilters>({ dateFrom: isoDate(30), dateTo: isoDate(0) })
  const [showRecord, setShowRecord] = useState(false)
  const [exporting, setExporting] = useState(false)

  const payments = useQuery({
    queryKey: ['payments', filters.dateFrom, filters.dateTo, filters.direction, filters.sourceType],
    queryFn: () => fetchPayments(filters),
    placeholderData: keepPreviousData,
  })

  const invalidateRelated = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['payments'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      queryClient.invalidateQueries({ queryKey: ['sales'] }),
      queryClient.invalidateQueries({ queryKey: ['khata'] }),
      queryClient.invalidateQueries({ queryKey: ['expenses'] }),
      queryClient.invalidateQueries({ queryKey: ['pnl'] }),
    ])
  }

  const createMutation = useMutation({
    mutationFn: createPayment,
    onSuccess: async () => {
      toast.success('Payment recorded')
      setShowRecord(false)
      await invalidateRelated()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to record payment'),
  })

  const onExport = async () => {
    try {
      setExporting(true)
      const blob = await exportPaymentsCsv(filters)
      saveBlob(blob, `payments-${filters.dateFrom}_${filters.dateTo}.csv`)
    } catch (error) {
      toast.error(normalizeError(error).message || 'Export failed')
    } finally {
      setExporting(false)
    }
  }

  const totals = useMemo(() => {
    const rows = payments.data ?? []
    const totalIn = rows.filter((p) => p.direction === 'IN').reduce((sum, p) => sum + p.amount, 0)
    const totalOut = rows.filter((p) => p.direction === 'OUT').reduce((sum, p) => sum + p.amount, 0)
    return { count: rows.length, totalIn, totalOut, net: totalIn - totalOut }
  }, [payments.data])

  return (
    <div>
      <PageHeader
        title="Payments"
        subtitle="Money in and out — sales, khata settlements, expenses and more"
        actions={
          <>
            <Button variant="secondary" onClick={onExport} disabled={exporting}>
              <Icon name="download" className="size-4" />
              {exporting ? 'Exporting…' : 'Export CSV'}
            </Button>
            <Button variant="primary" onClick={() => setShowRecord(true)}>
              <Icon name="plus" className="size-4" />
              Record Payment
            </Button>
          </>
        }
      />

      <Card className="mb-6">
        <div className="grid grid-cols-2 gap-3 p-4 sm:grid-cols-3 lg:grid-cols-5">
          <Input type="date" label="From" value={filters.dateFrom} onChange={(e) => setFilters({ ...filters, dateFrom: e.target.value })} />
          <Input type="date" label="To" value={filters.dateTo} onChange={(e) => setFilters({ ...filters, dateTo: e.target.value })} />
          <Select
            label="Direction"
            value={filters.direction ?? ''}
            onChange={(v) => setFilters({ ...filters, direction: (v || '') as PaymentDirection | '' })}
            options={[
              { value: '', label: 'All directions' },
              { value: 'IN', label: 'Money in' },
              { value: 'OUT', label: 'Money out' },
            ]}
          />
          <Select
            label="Source"
            value={filters.sourceType ?? ''}
            onChange={(v) => setFilters({ ...filters, sourceType: (v || '') as PaymentSource | '' })}
            options={[
              { value: '', label: 'All sources' },
              ...PAYMENT_SOURCES.map((s) => ({ value: s, label: SOURCE_LABELS[s] })),
            ]}
          />
          <Button variant="ghost" className="self-end" onClick={() => setFilters({ dateFrom: isoDate(30), dateTo: isoDate(0), direction: '', sourceType: '' })}>
            Reset
          </Button>
        </div>
        <div className="grid grid-cols-2 gap-4 border-t border-line px-4 py-4 lg:grid-cols-4">
          <Total label="Money in" value={totals.totalIn} tone="in" />
          <Total label="Money out" value={totals.totalOut} tone="out" />
          <Total label="Net" value={totals.net} tone={totals.net >= 0 ? 'in' : 'out'} />
          <Total label="Transactions" value={totals.count} tone="neutral" />
        </div>
      </Card>

      {payments.isError ? (
        <ErrorState icon={<Icon name="alert-circle" className="size-7" />} title="Could not load payments" message="The payments feed is unavailable." retryLabel="Retry" onRetry={() => void payments.refetch()} />
      ) : payments.isPending ? (
        <div className="space-y-3">{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-14" />)}</div>
      ) : (payments.data ?? []).length === 0 ? (
        <EmptyState icon={<Icon name="payments" className="size-8" />} title="No payments in this period" description="Try a wider date range, or record your first payment." />
      ) : (
        <Card className="overflow-hidden">
          <ul className="divide-y divide-line">
            {[...(payments.data ?? [])]
              .sort((a, b) => b.paymentDate.localeCompare(a.paymentDate))
              .map((payment) => (
                <li key={payment.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-ink">
                      {SOURCE_LABELS[payment.sourceType]} {payment.referenceNo ? `· ${payment.referenceNo}` : ''}
                    </p>
                    <p className="mt-0.5 text-xs text-ink-soft">
                      {formatDate(payment.paymentDate, language)}
                      {payment.createdByName ? ` · by ${payment.createdByName}` : ''}
                      {payment.note ? ` · ${payment.note}` : ''}
                    </p>
                  </div>
                  <Badge size="sm" variant={payment.direction === 'IN' ? 'success' : 'danger'}>
                    {payment.direction}
                  </Badge>
                  <Badge size="sm">{payment.mode}</Badge>
                  <p className={cn('shrink-0 text-sm font-bold tabular-nums', payment.direction === 'IN' ? 'text-gold-700' : 'text-crimson-700')}>
                    {payment.direction === 'IN' ? '+' : '-'}
                    {formatCurrency(payment.amount)}
                  </p>
                </li>
              ))}
          </ul>
        </Card>
      )}

      <RecordPaymentModal
        open={showRecord}
        onClose={() => setShowRecord(false)}
        onSubmit={createMutation.mutate}
        pending={createMutation.isPending}
        defaultDate={filters.dateTo}
      />
    </div>
  )
}

function Total({ label, value, tone }: { label: string; value: number; tone: 'in' | 'out' | 'neutral' }) {
  const color =
    tone === 'in' ? 'text-gold-700' : tone === 'out' ? 'text-crimson-700' : 'text-ink'
  return (
    <div>
      <p className="text-xs text-ink-soft">{label}</p>
      <p className={cn('mt-0.5 text-lg font-bold tabular-nums', color)}>{formatCurrency(value)}</p>
    </div>
  )
}

function RecordPaymentModal({
  open,
  onClose,
  onSubmit,
  pending,
  defaultDate,
}: {
  open: boolean
  onClose: () => void
  onSubmit: (input: PaymentInput) => void
  pending: boolean
  defaultDate: string
}) {
  const [direction, setDirection] = useState<PaymentDirection>('IN')
  const [mode, setMode] = useState<PaymentMode>('CASH')
  const [amount, setAmount] = useState('')
  const [sourceType, setSourceType] = useState<PaymentSource>('OTHER')
  const [referenceNo, setReferenceNo] = useState('')
  const [paymentDate, setPaymentDate] = useState(defaultDate)
  const [note, setNote] = useState('')

  return (
    <Modal isOpen={open} onClose={onClose} title="Record Payment">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (!amount || Number(amount) <= 0) return
          onSubmit({
            direction,
            mode,
            amount: Number(amount),
            sourceType,
            sourceId: null,
            referenceNo: referenceNo.trim(),
            paymentDate,
            note: note.trim(),
          })
        }}
      >
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Select
              label="Direction"
              value={direction}
              onChange={(v) => setDirection(v as PaymentDirection)}
              options={[
                { value: 'IN', label: 'Money in' },
                { value: 'OUT', label: 'Money out' },
              ]}
            />
            <Select
              label="Mode"
              value={mode}
              onChange={(v) => setMode(v as PaymentMode)}
              options={PAYMENT_MODES.map((m) => ({ value: m, label: m }))}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Amount" type="number" min="0" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00" />
            <Select
              label="Source"
              value={sourceType}
              onChange={(v) => setSourceType(v as PaymentSource)}
              options={PAYMENT_SOURCES.map((s) => ({ value: s, label: SOURCE_LABELS[s] }))}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Payment date" type="date" value={paymentDate} onChange={(e) => setPaymentDate(e.target.value)} />
            <Input label="Reference no." value={referenceNo} onChange={(e) => setReferenceNo(e.target.value)} placeholder="Optional" />
          </div>
          <Input label="Note" value={note} onChange={(e) => setNote(e.target.value)} placeholder="Optional" />
          <div className="flex gap-2 border-t border-line pt-4">
            <Button type="submit" variant="primary" disabled={pending}>
              Save Payment
            </Button>
            <Button type="button" variant="ghost" onClick={onClose}>
              Cancel
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  )
}