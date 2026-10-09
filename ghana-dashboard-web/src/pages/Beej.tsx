import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { viewBlob } from '@/api/download'
import {
  deleteSeedPurchase,
  deleteSupplier,
  fetchSeedPurchaseBill,
  fetchSeedPurchases,
  fetchSeedStock,
  fetchSeedTypes,
  fetchSuppliers,
} from '@/api/beej'
import type { PaymentStatus, SeedPurchaseSummary, Supplier } from '@/api/beej'
import { PurchaseDrawer } from '@/components/beej/PurchaseDrawer'
import { RateHistoryModal } from '@/components/beej/RateHistoryModal'
import { StockAdjustModal } from '@/components/beej/StockAdjustModal'
import { StockCard } from '@/components/beej/StockCard'
import { SupplierLedgerModal } from '@/components/beej/SupplierLedgerModal'
import { SupplierModal } from '@/components/beej/SupplierModal'
import { SupplierPaymentModal } from '@/components/beej/SupplierPaymentModal'
import { gradeKey, statusKey, statusVariant } from '@/components/beej/helpers'
import { DateRangeControl } from '@/components/DateRangeControl'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  IconButton,
  PageHeader,
  Select,
  Skeleton,
  Tabs,
} from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { initialRange, isValidRange } from '@/utils/dateRange'
import type { DateRangeValue } from '@/utils/dateRange'
import { readEntryAction } from '@/utils/entryPoint'
import { formatCurrency, formatDate, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'

const ICON_BUTTON = 'focus-ring rounded-pill p-2 text-ink-soft transition-colors hover:bg-surface-3'

type Tab = 'stock' | 'purchases' | 'suppliers'

function ListSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }, (_, i) => (
        <Skeleton key={i} className="h-16" />
      ))}
    </div>
  )
}

export function BeejPage() {
  const { t, language } = useLanguage()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [activeTab, setActiveTab] = useState<Tab>('stock')

  const [purchaseDrawer, setPurchaseDrawer] = useState<{ open: boolean; id: number | null }>(() => ({
    // Home dashboard quick action: /beej?action=purchase opens the blank purchase drawer.
    open: readEntryAction(searchParams) === 'purchase',
    id: null,
  }))
  const [pendingDeletePurchase, setPendingDeletePurchase] = useState<SeedPurchaseSummary | null>(null)

  const [supplierModal, setSupplierModal] = useState<{ open: boolean; supplier: Supplier | null }>({
    open: false,
    supplier: null,
  })
  const [pendingDeleteSupplier, setPendingDeleteSupplier] = useState<Supplier | null>(null)
  const [paymentModal, setPaymentModal] = useState<{ open: boolean; supplier: Supplier | null }>({
    open: false,
    supplier: null,
  })
  const [ledgerModal, setLedgerModal] = useState<{ open: boolean; supplier: Supplier | null }>({
    open: false,
    supplier: null,
  })

  const [adjustModal, setAdjustModal] = useState<{ open: boolean; seedTypeId: number | null; name: string }>({
    open: false,
    seedTypeId: null,
    name: '',
  })
  const [rateHistoryModal, setRateHistoryModal] = useState<{ open: boolean; seedTypeId: number | null; name: string }>({
    open: false,
    seedTypeId: null,
    name: '',
  })

  const [dateRange, setDateRange] = useState<DateRangeValue>(initialRange)
  const [supplierFilter, setSupplierFilter] = useState<number | null>(null)
  const [seedTypeFilter, setSeedTypeFilter] = useState<number | null>(null)
  const [statusFilter, setStatusFilter] = useState<PaymentStatus | ''>('')
  const { range } = dateRange
  const validRange = isValidRange(range)

  const seedTypes = useQuery({ queryKey: ['seed-types'], queryFn: fetchSeedTypes, staleTime: 300_000 })
  const suppliers = useQuery({ queryKey: ['suppliers'], queryFn: fetchSuppliers })
  const seedStock = useQuery({ queryKey: ['seed-stock'], queryFn: fetchSeedStock, enabled: activeTab === 'stock' })
  const purchases = useQuery({
    queryKey: ['seed-purchases', 'list', range.from, range.to, supplierFilter, seedTypeFilter, statusFilter],
    queryFn: () =>
      fetchSeedPurchases({
        dateFrom: range.from,
        dateTo: range.to,
        supplierId: supplierFilter,
        seedTypeId: seedTypeFilter,
        paymentStatus: statusFilter,
      }),
    enabled: activeTab === 'purchases' && validRange,
    placeholderData: keepPreviousData,
  })

  const deletePurchase = useMutation({
    mutationFn: (id: number) => deleteSeedPurchase(id),
    onSuccess: async () => {
      toast.success(t('beej.purchases.deleted'))
      setPendingDeletePurchase(null)
      await Promise.all(
        [['seed-purchases'], ['seed-stock'], ['suppliers'], ['payments'], ['cash-book']].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      )
    },
    onError: (error) => toast.error(normalizeError(error).message || t('beej.purchases.deleteFailed')),
  })

  const removeSupplier = useMutation({
    mutationFn: (id: number) => deleteSupplier(id),
    onSuccess: async () => {
      toast.success(t('beej.suppliers.deleted'))
      setPendingDeleteSupplier(null)
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] })
    },
    onError: (error) => toast.error(normalizeError(error).message || t('beej.suppliers.deleteFailed')),
  })

  async function openBill(id: number) {
    const tab = window.open('', '_blank')
    try {
      viewBlob(await fetchSeedPurchaseBill(id), tab, `bill-${id}`)
    } catch {
      tab?.close()
      toast.error(t('beej.purchases.bill.failed'))
    }
  }

  const supplierOptions = [
    { value: '', label: t('beej.purchases.allSuppliers') },
    ...(suppliers.data ?? []).map((s) => ({ value: String(s.id), label: s.name })),
  ]
  const seedTypeOptions = [
    { value: '', label: t('beej.purchases.allSeedTypes') },
    ...(seedTypes.data ?? []).map((s) => ({ value: String(s.id), label: s.nameEn })),
  ]
  const statusOptions = [
    { value: '', label: t('beej.purchases.allStatus') },
    { value: 'PAID', label: t('beej.purchases.status.PAID') },
    { value: 'PARTIAL', label: t('beej.purchases.status.PARTIAL') },
    { value: 'UNPAID', label: t('beej.purchases.status.UNPAID') },
  ]

  const tabs = [
    { id: 'stock', label: t('beej.tabs.stock') },
    { id: 'purchases', label: t('beej.tabs.purchases') },
    { id: 'suppliers', label: t('beej.tabs.suppliers') },
  ]

  return (
    <div>
      <PageHeader
        title={t('beej.title')}
        subtitle={t('beej.subtitle')}
        actions={
          activeTab === 'suppliers' ? (
            <Button onClick={() => setSupplierModal({ open: true, supplier: null })}>
              <Icon name="plus" className="size-4" />
              {t('beej.suppliers.add')}
            </Button>
          ) : (
            <Button onClick={() => setPurchaseDrawer({ open: true, id: null })}>
              <Icon name="plus" className="size-4" />
              {t('beej.stock.addPurchase')}
            </Button>
          )
        }
      />

      <Tabs tabs={tabs} activeTab={activeTab} onTabChange={(id) => setActiveTab(id as Tab)} />

      <div className="mt-6">
        {activeTab === 'stock' &&
          (seedStock.isError ? (
            <ErrorState
              title={t('beej.stock.error.title')}
              message={t('beej.stock.error.body')}
              retryLabel={t('common.retry')}
              onRetry={() => void seedStock.refetch()}
            />
          ) : !seedStock.data ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {Array.from({ length: 4 }, (_, i) => (
                <Skeleton key={i} className="h-40" />
              ))}
            </div>
          ) : seedStock.data.length === 0 ? (
            <EmptyState
              icon={<Icon name="beej" className="size-7" />}
              title={t('beej.stock.empty.title')}
              description={t('beej.stock.empty.body')}
            />
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {seedStock.data.map((stock) => (
                <StockCard
                  key={stock.seedTypeId}
                  stock={stock}
                  onAdjust={() =>
                    setAdjustModal({ open: true, seedTypeId: stock.seedTypeId, name: stock.seedTypeName })
                  }
                  onRateHistory={() =>
                    setRateHistoryModal({ open: true, seedTypeId: stock.seedTypeId, name: stock.seedTypeName })
                  }
                />
              ))}
            </div>
          ))}

        {activeTab === 'purchases' && (
          <Card className="p-4">
            <div className="mb-4">
              <DateRangeControl value={dateRange} onChange={setDateRange} />
            </div>
            <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
              <Select
                options={supplierOptions}
                value={supplierFilter !== null ? String(supplierFilter) : ''}
                onChange={(v) => setSupplierFilter(v ? Number(v) : null)}
              />
              <Select
                options={seedTypeOptions}
                value={seedTypeFilter !== null ? String(seedTypeFilter) : ''}
                onChange={(v) => setSeedTypeFilter(v ? Number(v) : null)}
              />
              <Select
                options={statusOptions}
                value={statusFilter}
                onChange={(v) => setStatusFilter(v as PaymentStatus | '')}
              />
            </div>

            {!validRange ? null : purchases.isError ? (
              <ErrorState
                title={t('beej.purchases.error.title')}
                message={t('beej.purchases.error.body')}
                retryLabel={t('common.retry')}
                onRetry={() => void purchases.refetch()}
              />
            ) : !purchases.data ? (
              <ListSkeleton />
            ) : purchases.data.length === 0 ? (
              <EmptyState
                icon={<Icon name="beej" className="size-7" />}
                title={t('beej.purchases.empty.title')}
                description={t('beej.purchases.empty.body')}
                action={
                  <Button size="sm" onClick={() => setPurchaseDrawer({ open: true, id: null })}>
                    <Icon name="plus" className="size-4" />
                    {t('beej.purchase.add')}
                  </Button>
                }
              />
            ) : (
              <ul aria-label={t('beej.purchases.list')} className="divide-y divide-line">
                {purchases.data.map((purchase) => (
                  <li key={purchase.id} className="flex items-center gap-3 py-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-ink">
                        {purchase.seedTypeName} · {formatQty(purchase.qtyKg)} kg
                      </p>
                      <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-soft">
                        <span>{purchase.supplierName}</span>
                        <span aria-hidden="true">·</span>
                        <span>{formatDate(purchase.purchaseDate, language)}</span>
                        <Badge size="sm">{t(gradeKey(purchase.grade))}</Badge>
                        <Badge size="sm" variant={statusVariant(purchase.paymentStatus)}>
                          {t(statusKey(purchase.paymentStatus))}
                        </Badge>
                      </div>
                    </div>
                    <p className="shrink-0 text-sm font-bold tabular-nums text-ink">
                      {formatCurrency(purchase.totalAmount, { decimals: 2 })}
                    </p>
                    <div className="flex shrink-0 items-center">
                      {purchase.hasBill ? (
                        <button
                          type="button"
                          aria-label={t('beej.purchases.bill.view')}
                          title={t('beej.purchases.bill.view')}
                          onClick={() => void openBill(purchase.id)}
                          className={cn(ICON_BUTTON, 'text-brand-600 dark:text-brand-300')}
                        >
                          <Icon name="file" className="size-4" />
                        </button>
                      ) : (
                        <span className="p-2 text-ink-soft/40" title={t('beej.purchases.bill.none')}>
                          <Icon name="file" className="size-4" />
                        </span>
                      )}
                      <button
                        type="button"
                        aria-label={t('common.edit')}
                        title={t('common.edit')}
                        onClick={() => setPurchaseDrawer({ open: true, id: purchase.id })}
                        className={ICON_BUTTON}
                      >
                        <Icon name="pencil" className="size-4" />
                      </button>
                      <button
                        type="button"
                        aria-label={t('common.delete')}
                        title={t('common.delete')}
                        onClick={() => setPendingDeletePurchase(purchase)}
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
        )}

        {activeTab === 'suppliers' && (
          <Card className="p-4">
            {suppliers.isError ? (
              <ErrorState
                title={t('beej.suppliers.error.title')}
                message={t('beej.suppliers.error.body')}
                retryLabel={t('common.retry')}
                onRetry={() => void suppliers.refetch()}
              />
            ) : !suppliers.data ? (
              <ListSkeleton />
            ) : suppliers.data.length === 0 ? (
              <EmptyState
                icon={<Icon name="truck" className="size-7" />}
                title={t('beej.suppliers.empty.title')}
                description={t('beej.suppliers.empty.body')}
                action={
                  <Button size="sm" onClick={() => setSupplierModal({ open: true, supplier: null })}>
                    <Icon name="plus" className="size-4" />
                    {t('beej.suppliers.add')}
                  </Button>
                }
              />
            ) : (
              <ul aria-label={t('beej.suppliers.list')} className="divide-y divide-line">
                {suppliers.data.map((supplier) => (
                  <li key={supplier.id} className="flex items-center gap-3 py-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-ink">{supplier.name}</p>
                      <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-soft">
                        {supplier.phone && <span>{supplier.phone}</span>}
                        <span aria-hidden="true">·</span>
                        <span>
                          {t('beej.suppliers.balance')}: {formatCurrency(supplier.balance, { decimals: 2 })}
                        </span>
                      </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                      <IconButton
                        variant="ghost"
                        size="sm"
                        aria-label={t('beej.suppliers.recordPayment')}
                        title={t('beej.suppliers.recordPayment')}
                        onClick={() => setPaymentModal({ open: true, supplier })}
                      >
                        <Icon name="wallet" className="size-4" />
                      </IconButton>
                      <IconButton
                        variant="ghost"
                        size="sm"
                        aria-label={t('beej.suppliers.ledger')}
                        title={t('beej.suppliers.ledger')}
                        onClick={() => setLedgerModal({ open: true, supplier })}
                      >
                        <Icon name="list" className="size-4" />
                      </IconButton>
                      <IconButton
                        variant="ghost"
                        size="sm"
                        aria-label={t('common.edit')}
                        title={t('common.edit')}
                        onClick={() => setSupplierModal({ open: true, supplier })}
                      >
                        <Icon name="pencil" className="size-4" />
                      </IconButton>
                      <IconButton
                        variant="ghost"
                        size="sm"
                        aria-label={t('common.delete')}
                        title={t('common.delete')}
                        onClick={() => setPendingDeleteSupplier(supplier)}
                        className="hover:text-crimson-600"
                      >
                        <Icon name="trash" className="size-4" />
                      </IconButton>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        )}
      </div>

      <PurchaseDrawer
        open={purchaseDrawer.open}
        purchaseId={purchaseDrawer.id}
        seedTypes={seedTypes.data ?? []}
        suppliers={suppliers.data ?? []}
        onClose={() => setPurchaseDrawer((d) => ({ ...d, open: false }))}
      />

      <SupplierModal
        open={supplierModal.open}
        supplier={supplierModal.supplier}
        onClose={() => setSupplierModal((s) => ({ ...s, open: false }))}
      />
      <SupplierPaymentModal
        open={paymentModal.open}
        supplier={paymentModal.supplier}
        onClose={() => setPaymentModal((s) => ({ ...s, open: false }))}
      />
      <SupplierLedgerModal
        open={ledgerModal.open}
        supplierId={ledgerModal.supplier?.id ?? null}
        supplierName={ledgerModal.supplier?.name ?? ''}
        onClose={() => setLedgerModal((s) => ({ ...s, open: false }))}
      />

      <StockAdjustModal
        open={adjustModal.open}
        seedTypeId={adjustModal.seedTypeId}
        seedTypeName={adjustModal.name}
        onClose={() => setAdjustModal((s) => ({ ...s, open: false }))}
      />
      <RateHistoryModal
        open={rateHistoryModal.open}
        seedTypeId={rateHistoryModal.seedTypeId}
        seedTypeName={rateHistoryModal.name}
        onClose={() => setRateHistoryModal((s) => ({ ...s, open: false }))}
      />

      <ConfirmDialog
        isOpen={pendingDeletePurchase !== null}
        onClose={() => setPendingDeletePurchase(null)}
        onConfirm={() => pendingDeletePurchase && deletePurchase.mutate(pendingDeletePurchase.id)}
        title={t('beej.purchases.delete.title')}
        message={t('beej.purchases.delete.message')}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        confirmVariant="danger"
        loading={deletePurchase.isPending}
      />
      <ConfirmDialog
        isOpen={pendingDeleteSupplier !== null}
        onClose={() => setPendingDeleteSupplier(null)}
        onConfirm={() => pendingDeleteSupplier && removeSupplier.mutate(pendingDeleteSupplier.id)}
        title={t('beej.suppliers.delete.title')}
        message={t('beej.suppliers.delete.message', { name: pendingDeleteSupplier?.name ?? '' })}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        confirmVariant="danger"
        loading={removeSupplier.isPending}
      />
    </div>
  )
}

