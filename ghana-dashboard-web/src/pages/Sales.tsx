import { useMemo, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { fetchProducts } from '@/api/inventory'
import type { Product } from '@/api/inventory'
import { fetchCustomer, fetchCustomers } from '@/api/customers'
import type { CustomerListItem } from '@/api/customers'
import {
  ORDER_STATUSES,
  SALE_PAYMENT_STATUSES,
  cancelOrder,
  createOrder,
  createSale,
  fetchOrderStatusCounts,
  fetchOrders,
  fetchSale,
  fetchSales,
  updateOrderStatus,
  updateSalePaymentStatus,
  voidSale,
  type CreateOrderInput,
  type CreateSaleInput,
  type OrderStatus,
  type OrderSummary,
  type PaymentMode,
  type SaleItemInput,
  type SaleSummary,
} from '@/api/sales'
import { Badge, Button, Card, EmptyState, ErrorState, Input, Modal, PageHeader, Select, Skeleton } from '@/components/ui'
import { cn } from '@/utils/cn'
import { formatCurrency, formatDate, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { useLanguage } from '@/i18n'

type PageTab = 'sales' | 'orders'

const SALE_STATUS_VARIANT: Record<string, 'primary' | 'warning' | 'success' | 'danger'> = {
  PAID: 'success',
  PARTIAL: 'warning',
  UNPAID: 'danger',
}

const ORDER_STATUS_VARIANT: Record<OrderStatus, 'primary' | 'warning' | 'success' | 'info' | 'danger' | 'default'> = {
  NEW: 'info',
  CONFIRMED: 'primary',
  PACKED: 'warning',
  OUT_FOR_DELIVERY: 'primary',
  DELIVERED: 'success',
  CANCELLED: 'danger',
}

const ORDER_STATUS_TABS: { id: OrderStatus | 'all'; label: string }[] = [
  { id: 'all', label: 'All' },
  ...ORDER_STATUSES.map((s) => ({ id: s as OrderStatus | 'all', label: s.replaceAll('_', ' ') })),
]

const EMPTY_CART = { items: [] as SaleItemInput[], discount: 0, discountType: 'AMOUNT' as 'PERCENT' | 'AMOUNT' }

export function SalesPage() {
  const { language } = useLanguage()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<PageTab>('sales')

  const [saleStatus, setSaleStatus] = useState('')
  const [salePage, setSalePage] = useState(0)
  const [orderStatus, setOrderStatus] = useState<OrderStatus | 'all'>('all')
  const [orderPage, setOrderPage] = useState(0)

  const [showNewSale, setShowNewSale] = useState(false)
  const [showNewOrder, setShowNewOrder] = useState(false)
  const [viewSaleId, setViewSaleId] = useState<number | null>(null)
  const [statusSale, setStatusSale] = useState<SaleSummary | null>(null)
  const [voidSaleTarget, setVoidSaleTarget] = useState<SaleSummary | null>(null)

  const products = useQuery({ queryKey: ['products'], queryFn: fetchProducts, staleTime: 120_000 })

  const salesQuery = useQuery({
    queryKey: ['sales', 'list', salePage, saleStatus],
    queryFn: () => fetchSales({ page: salePage, size: 10, paymentStatus: saleStatus as never }),
    placeholderData: keepPreviousData,
  })
  const ordersQuery = useQuery({
    queryKey: ['orders', 'list', orderPage, orderStatus],
    queryFn: () => fetchOrders({ page: orderPage, size: 10, status: orderStatus === 'all' ? '' : orderStatus }),
    placeholderData: keepPreviousData,
  })
  const statusCounts = useQuery({ queryKey: ['orders', 'status-counts'], queryFn: fetchOrderStatusCounts })
  const viewSale = useQuery({
    queryKey: ['sales', 'detail', viewSaleId],
    queryFn: () => fetchSale(viewSaleId as number),
    enabled: viewSaleId !== null,
  })

  const invalidateRelated = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['sales'] }),
      queryClient.invalidateQueries({ queryKey: ['orders'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      queryClient.invalidateQueries({ queryKey: ['inventory'] }),
      queryClient.invalidateQueries({ queryKey: ['payments'] }),
      queryClient.invalidateQueries({ queryKey: ['customers'] }),
      queryClient.invalidateQueries({ queryKey: ['returns'] }),
    ])
  }

  const createSaleMutation = useMutation({
    mutationFn: createSale,
    onSuccess: async (sale) => {
      toast.success(`Sale ${sale.billNo} created`)
      setShowNewSale(false)
      await invalidateRelated()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to create sale'),
  })

  const createOrderMutation = useMutation({
    mutationFn: createOrder,
    onSuccess: async (order) => {
      toast.success(`Order ${order.billNo} created`)
      setShowNewOrder(false)
      await invalidateRelated()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to create order'),
  })

  const statusMutation = useMutation({
    mutationFn: (input: { id: number; paymentStatus: string }) =>
      updateSalePaymentStatus(input.id, { paymentStatus: input.paymentStatus as never }),
    onSuccess: async () => {
      toast.success('Payment status updated')
      setStatusSale(null)
      await invalidateRelated()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to update status'),
  })

  const voidMutation = useMutation({
    mutationFn: (input: { id: number; reason: string }) => voidSale(input.id, input.reason),
    onSuccess: async () => {
      toast.success('Sale voided')
      setVoidSaleTarget(null)
      await invalidateRelated()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to void sale'),
  })

  const orderStatusMutation = useMutation({
    mutationFn: (input: { id: number; status: OrderStatus }) => updateOrderStatus(input.id, input.status),
    onSuccess: () => {
      void invalidateRelated()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to update order'),
  })

  const orderCancelMutation = useMutation({
    mutationFn: (input: { id: number }) => cancelOrder(input.id, 'Cancelled by user'),
    onSuccess: async () => {
      toast.success('Order cancelled')
      await invalidateRelated()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to cancel order'),
  })

  const counts = statusCounts.data

  return (
    <div>
      <PageHeader
        title="Sales & Orders"
        subtitle="Record counter sales and manage delivery orders"
        actions={
          <>
            <Button variant="secondary" onClick={() => { setShowNewOrder(true) }}>
              <Icon name="truck" className="size-4" />
              New Order
            </Button>
            <Button variant="primary" onClick={() => setShowNewSale(true)}>
              <Icon name="plus" className="size-4" />
              New Sale
            </Button>
          </>
        }
      />

      <div className="border-b border-line">
        <nav className="flex gap-1 overflow-x-auto">
          {(
            [
              { id: 'sales', label: `Sales · ${salesQuery.data?.totalElements ?? '–'}` },
              { id: 'orders', label: `Orders · ${ordersQuery.data?.totalElements ?? '–'}` },
            ] as { id: PageTab; label: string }[]
          ).map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={cn(
                'whitespace-nowrap border-b-2 px-4 py-3 text-sm font-medium transition-colors',
                tab === t.id ? 'border-brand-500 text-brand-600' : 'border-transparent text-ink-soft hover:text-ink',
              )}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </div>

      <div className="mt-6">
        {tab === 'sales' ? (
          <SalesList
            data={salesQuery.data?.content ?? []}
            totalPages={salesQuery.data?.totalPages ?? 1}
            page={salePage}
            onPage={setSalePage}
            statusFilter={saleStatus}
            onStatusFilter={setSaleStatus}
            isError={salesQuery.isError}
            isPending={!salesQuery.data}
            language={language}
            onView={setViewSaleId}
            onStatus={(sale) => setStatusSale(sale)}
            onVoid={(sale) => setVoidSaleTarget(sale)}
          />
        ) : (
          <OrdersList
            data={ordersQuery.data?.content ?? []}
            totalPages={ordersQuery.data?.totalPages ?? 1}
            page={orderPage}
            onPage={setOrderPage}
            statusTabs={ORDER_STATUS_TABS}
            activeStatus={orderStatus}
            onStatusTab={setOrderStatus}
            counts={counts}
            isError={ordersQuery.isError}
            isPending={!ordersQuery.data}
            language={language}
            onAdvance={(order) => {
              const next = advanceStatus(order.status)
              if (next) orderStatusMutation.mutate({ id: order.id, status: next })
            }}
            onCancel={(order) => orderCancelMutation.mutate({ id: order.id })}
          />
        )}
      </div>

      <NewSaleModal
        open={showNewSale}
        onClose={() => setShowNewSale(false)}
        products={products.data ?? []}
        onSubmit={createSaleMutation.mutate}
        pending={createSaleMutation.isPending}
      />

      <NewOrderModal
        open={showNewOrder}
        onClose={() => setShowNewOrder(false)}
        products={products.data ?? []}
        onSubmit={createOrderMutation.mutate}
        pending={createOrderMutation.isPending}
      />

      <Modal isOpen={viewSaleId !== null} onClose={() => setViewSaleId(null)} title={viewSale.data?.billNo ?? 'Sale'} size="lg">
        {viewSale.isPending ? (
          <div className="space-y-3">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-10" />)}</div>
        ) : viewSale.isError || !viewSale.data ? (
          <ErrorState title="Could not load sale" message="The sale could not be fetched." retryLabel="Retry" onRetry={() => void viewSale.refetch()} className="py-6" />
        ) : (
          <SaleDetailView sale={viewSale.data} language={language} />
        )}
      </Modal>

      <Modal isOpen={statusSale !== null} onClose={() => setStatusSale(null)} title={`Update Payment — ${statusSale?.billNo ?? ''}`}>
        <div className="space-y-4">
          <Select
            label="Payment status"
            options={SALE_PAYMENT_STATUSES.map((s) => ({ value: s, label: s }))}
            value={statusSale?.paymentStatus ?? 'PAID'}
            onChange={(v) => statusSale && statusMutation.mutate({ id: statusSale.id, paymentStatus: v })}
          />
          <Button type="button" variant="ghost" onClick={() => setStatusSale(null)} className="w-full">
            Cancel
          </Button>
        </div>
      </Modal>

      <Modal isOpen={voidSaleTarget !== null} onClose={() => setVoidSaleTarget(null)} title={`Void Sale — ${voidSaleTarget?.billNo ?? ''}`}>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            const form = e.currentTarget.elements.namedItem('reason') as HTMLInputElement
            if (voidSaleTarget && form.value.trim()) voidMutation.mutate({ id: voidSaleTarget.id, reason: form.value.trim() })
          }}
        >
          <Input label="Reason for voiding" name="reason" placeholder="Duplicate bill, wrong amount…" required />
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="danger" disabled={voidMutation.isPending}>Void Sale</Button>
            <Button type="button" variant="ghost" onClick={() => setVoidSaleTarget(null)}>Cancel</Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}

// ==================== Sales list ====================

function SalesList({
  data,
  totalPages,
  page,
  onPage,
  statusFilter,
  onStatusFilter,
  isError,
  isPending,
  language,
  onView,
  onStatus,
  onVoid,
}: {
  data: SaleSummary[]
  totalPages: number
  page: number
  onPage: (p: number) => void
  statusFilter: string
  onStatusFilter: (s: string) => void
  isError: boolean
  isPending: boolean
  language: 'en' | 'hi' | 'mr'
  onView: (id: number) => void
  onStatus: (sale: SaleSummary) => void
  onVoid: (sale: SaleSummary) => void
}) {
  if (isError) return <ErrorState icon={<Icon name="alert-circle" className="size-7" />} title="Could not load sales" message="The sales feed is unavailable." retryLabel="Retry" />
  if (isPending) return <div className="space-y-3">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-14" />)}</div>
  if (data.length === 0) {
    return (
      <EmptyState
        icon={<Icon name="sales" className="size-8" />}
        title="No sales yet"
        description="Record your first sale with the New Sale button."
      />
    )
  }
  return (
    <Card className="overflow-hidden">
      <div className="flex items-center justify-between gap-3 border-b border-line px-4 py-3">
        <p className="text-sm font-semibold text-ink">{page + 1} of {Math.max(totalPages, 1)}</p>
        <div className="w-40">
          <Select
            options={[{ value: '', label: 'All payment statuses' }, ...SALE_PAYMENT_STATUSES.map((s) => ({ value: s, label: s }))]}
            value={statusFilter}
            onChange={onStatusFilter}
          />
        </div>
      </div>
      <ul className="divide-y divide-line">
        {data.map((sale) => (
          <li key={sale.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
            <button type="button" onClick={() => onView(sale.id)} className="min-w-0 flex-1 text-left focus-ring">
              <p className="truncate text-sm font-semibold text-ink">{sale.billNo}</p>
              <p className="mt-0.5 truncate text-xs text-ink-soft">
                {sale.customerName || 'Walk-in'} · {formatDate(sale.createdAt, language)}
              </p>
            </button>
            <Badge size="sm" variant={SALE_STATUS_VARIANT[sale.paymentStatus]}>{sale.paymentStatus}</Badge>
            <Badge size="sm">{sale.paymentMode}</Badge>
            {sale.isVoid && <Badge size="sm" variant="danger">VOID</Badge>}
            <p className="shrink-0 text-sm font-bold tabular-nums text-ink">{formatCurrency(sale.totalAmount)}</p>
            <div className="flex shrink-0 items-center gap-1">
              <IconButton title="Payment status" onClick={() => onStatus(sale)} disabled={sale.isVoid} icon="wallet" />
              <IconButton title="Void" onClick={() => onVoid(sale)} disabled={sale.isVoid} icon="x-circle" danger />
            </div>
          </li>
        ))}
      </ul>
      <Pagination page={page} totalPages={totalPages} onPage={onPage} />
    </Card>
  )
}

// ==================== Orders list ====================

function OrdersList({
  data,
  totalPages,
  page,
  onPage,
  statusTabs,
  activeStatus,
  onStatusTab,
  counts,
  isError,
  isPending,
  language,
  onAdvance,
  onCancel,
}: {
  data: OrderSummary[]
  totalPages: number
  page: number
  onPage: (p: number) => void
  statusTabs: { id: OrderStatus | 'all'; label: string }[]
  activeStatus: OrderStatus | 'all'
  onStatusTab: (s: OrderStatus | 'all') => void
  counts?: import('@/api/sales').OrderStatusCounts
  isError: boolean
  isPending: boolean
  language: 'en' | 'hi' | 'mr'
  onAdvance: (order: OrderSummary) => void
  onCancel: (order: OrderSummary) => void
}) {
  const countOf = (id: OrderStatus | 'all') => {
    if (!counts) return 0
    if (id === 'all') return Object.values(counts).reduce((a, b) => a + b, 0)
    return counts[id as OrderStatus]
  }

  if (isError) return <ErrorState icon={<Icon name="alert-circle" className="size-7" />} title="Could not load orders" message="The orders feed is unavailable." retryLabel="Retry" />
  if (isPending) return <div className="space-y-3">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-14" />)}</div>

  return (
    <Card className="overflow-hidden">
      <div className="flex gap-1 overflow-x-auto border-b border-line px-2 pt-2">
        {statusTabs.map((stab) => (
          <button
            key={stab.id}
            type="button"
            onClick={() => onStatusTab(stab.id)}
            className={cn(
              'whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              activeStatus === stab.id ? 'border-brand-500 text-brand-600' : 'border-transparent text-ink-soft hover:text-ink',
            )}
          >
            {stab.label}
            <span className="ml-1.5 text-xs text-ink-soft">{countOf(stab.id)}</span>
          </button>
        ))}
      </div>
      {data.length === 0 ? (
        <EmptyState icon={<Icon name="truck" className="size-8" />} title="No orders here" description="Orders in this status will appear here." className="py-8" />
      ) : (
        <ul className="divide-y divide-line">
          {data.map((order) => {
            const next = advanceStatus(order.status)
            return (
              <li key={order.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-ink">{order.billNo}</p>
                  <p className="mt-0.5 truncate text-xs text-ink-soft">
                    {order.customerName || 'Walk-in'} · {formatDate(order.createdAt, language)}
                    {order.expectedDeliveryDate ? ` · due ${formatDate(order.expectedDeliveryDate, language)}` : ''}
                  </p>
                </div>
                <Badge size="sm" variant={ORDER_STATUS_VARIANT[order.status]}>{order.status.replaceAll('_', ' ')}</Badge>
                <Badge size="sm">{order.paymentStatus}</Badge>
                <p className="shrink-0 text-sm font-bold tabular-nums text-ink">{formatCurrency(order.totalAmount)}</p>
                <div className="flex shrink-0 items-center gap-1">
                  {next && <IconButton title={`Advance to ${next.replaceAll('_', ' ')}`} onClick={() => onAdvance(order)} icon="chevron-right" />}
                  {order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && (
                    <IconButton title="Cancel order" onClick={() => onCancel(order)} icon="x-circle" danger />
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
      <Pagination page={page} totalPages={totalPages} onPage={onPage} />
    </Card>
  )
}

function advanceStatus(status: OrderStatus): OrderStatus | null {
  const order: Record<OrderStatus, OrderStatus | null> = {
    NEW: 'CONFIRMED',
    CONFIRMED: 'PACKED',
    PACKED: 'OUT_FOR_DELIVERY',
    OUT_FOR_DELIVERY: 'DELIVERED',
    DELIVERED: null,
    CANCELLED: null,
  }
  return order[status]
}

function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  return (
    <div className="flex items-center justify-end gap-2 border-t border-line px-4 py-2">
      <Button size="sm" variant="ghost" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        <Icon name="chevron-left" className="size-4" />
      </Button>
      <span className="text-xs text-ink-soft">{page + 1} / {Math.max(totalPages, 1)}</span>
      <Button size="sm" variant="ghost" disabled={page + 1 >= totalPages || totalPages === 0} onClick={() => onPage(page + 1)}>
        <Icon name="chevron-right" className="size-4" />
      </Button>
    </div>
  )
}

function IconButton({ title, onClick, icon, danger, disabled }: { title: string; onClick: () => void; icon: 'wallet' | 'x-circle' | 'chevron-right'; danger?: boolean; disabled?: boolean }) {
  return (
    <button
      type="button"
      title={title}
      aria-label={title}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        'focus-ring rounded-pill p-2 text-ink-soft transition-colors hover:bg-surface-3',
        danger && 'hover:text-crimson-600',
        disabled && 'cursor-not-allowed opacity-40',
      )}
    >
      <Icon name={icon} className="size-4" />
    </button>
  )
}

// ==================== Sale detail ====================

function SaleDetailView({ sale, language }: { sale: import('@/api/sales').SaleResponse; language: 'en' | 'hi' | 'mr' }) {
  const items = sale.items ?? []
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Detail label="Customer" value={sale.customerName || 'Walk-in'} />
        <Detail label="Mode" value={sale.paymentMode} />
        <Detail label="Status" value={`${sale.paymentStatus}${sale.isVoid ? ' · VOID' : ''}`} />
        <Detail label="Date" value={formatDate(sale.createdAt, language)} />
      </div>
      <ul className="divide-y divide-line rounded-card border border-line">
        {items.map((item) => (
          <li key={item.id} className="flex items-center gap-3 px-3 py-2 text-sm">
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-ink">{item.productName}</p>
              <p className="text-xs text-ink-soft">{formatQty(item.qty)} {item.unit} × {formatCurrency(item.rate)}</p>
            </div>
            <p className="font-semibold tabular-nums text-ink">{formatCurrency(item.amount)}</p>
          </li>
        ))}
      </ul>
      <div className="flex items-center justify-between border-t border-line pt-3">
        <p className="text-sm text-ink-soft">Total (discount {sale.discount > 0 ? `${sale.discount} ${sale.discountType}` : 'none'})</p>
        <p className="text-lg font-bold tabular-nums text-ink">{formatCurrency(sale.totalAmount)}</p>
      </div>
    </div>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-ink-soft">{label}</p>
      <p className="mt-0.5 truncate text-sm font-semibold text-ink">{value}</p>
    </div>
  )
}

// ==================== New Sale / New Order forms ====================

function productOptions(products: Product[]) {
  return products.filter((p) => p.active && !p.isKhali).map((p) => ({ value: String(p.id), label: `${p.name} (${p.sku}) · ${formatCurrency(p.sellingPrice)}` }))
}

function LineItemsEditor({
  items,
  products,
  onChange,
}: {
  items: SaleItemInput[]
  products: Product[]
  onChange: (items: SaleItemInput[]) => void
}) {
  const options = productOptions(products)
  const productOf = (id: number) => products.find((p) => p.id === id)

  const update = (index: number, patch: Partial<SaleItemInput>) => {
    onChange(items.map((it, i) => (i === index ? { ...it, ...patch } : it)))
  }

  return (
    <div className="space-y-2">
      {items.map((item, index) => {
        const product = productOf(item.productId)
        return (
          <div key={index} className="rounded-card border border-line bg-surface-2 p-3">
            <div className="flex items-start gap-2">
              <div className="min-w-0 flex-1">
                <Select
                  label="Product"
                  options={options}
                  value={String(item.productId)}
                  onChange={(v) => update(index, { productId: Number(v), rate: productOf(Number(v))?.sellingPrice ?? item.rate })}
                  placeholder="Select product"
                />
              </div>
              <button
                type="button"
                aria-label="Remove line"
                onClick={() => onChange(items.filter((_, i) => i !== index))}
                className="focus-ring mt-7 rounded-pill p-2 text-ink-soft hover:text-crimson-600"
                title={product?.name ?? ''}
              >
                <Icon name="trash" className="size-4" />
              </button>
            </div>
            <div className="mt-2 grid grid-cols-2 gap-2">
              <Input
                label="Qty"
                type="number"
                min="0.001"
                step="0.001"
                value={String(item.qty)}
                onChange={(e) => update(index, { qty: Number(e.target.value) })}
              />
              <Input
                label="Rate"
                type="number"
                min="0"
                step="0.01"
                value={String(item.rate)}
                onChange={(e) => update(index, { rate: Number(e.target.value) })}
              />
            </div>
          </div>
        )
      })}
      <Button
        size="sm"
        variant="ghost"
        type="button"
        onClick={() => onChange([...items, { productId: 0, qty: 1, rate: 0 }])}
      >
        <Icon name="plus" className="size-4" />
        Add line
      </Button>
    </div>
  )
}

function NewSaleModal({
  open,
  onClose,
  products,
  onSubmit,
  pending,
}: {
  open: boolean
  onClose: () => void
  products: Product[]
  onSubmit: (input: CreateSaleInput) => void
  pending: boolean
}) {
  const [form, setForm] = useState(EMPTY_CART)
  const [customerPhone, setCustomerPhone] = useState('')
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH')
  const [paidAmount, setPaidAmount] = useState('')

  const subtotal = useMemo(() => form.items.reduce((sum, it) => sum + (it.qty * it.rate || 0), 0), [form.items])
  const discount = useMemo(() => {
    if (form.discountType === 'PERCENT') return (subtotal * form.discount) / 100
    return form.discount
  }, [subtotal, form.discount, form.discountType])
  const total = Math.max(subtotal - discount, 0)

  return (
    <Modal isOpen={open} onClose={onClose} title="New Sale" size="xl">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          const validItems = form.items.filter((it) => it.productId && it.qty > 0 && it.rate >= 0)
          if (validItems.length === 0) return
          onSubmit({
            customerId: null,
            customerPhone: customerPhone.trim(),
            items: validItems,
            discount: Number(form.discount),
            discountType: form.discountType,
            paymentMode,
            paidAmount: paidAmount === '' ? total : Number(paidAmount),
            paymentReference: '',
            notes: '',
          })
        }}
      >
        <div className="space-y-4">
          <Input label="Customer phone (optional)" value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} placeholder="Used to create/link a khata bill" />
          <LineItemsEditor items={form.items} products={products} onChange={(items) => setForm({ ...form, items })} />
          <div className="flex flex-wrap items-end gap-3">
            <Input
              label="Discount"
              type="number"
              min="0"
              step="0.01"
              className="w-32"
              value={String(form.discount)}
              onChange={(e) => setForm({ ...form, discount: Number(e.target.value) })}
            />
            <Select
              label="Discount type"
              className="w-36"
              options={[
                { value: 'AMOUNT', label: 'Rs' },
                { value: 'PERCENT', label: '%' },
              ]}
              value={form.discountType}
              onChange={(v) => setForm({ ...form, discountType: v as 'PERCENT' | 'AMOUNT' })}
            />
            <Select
              label="Payment mode"
              className="w-36"
              options={[
                { value: 'CASH', label: 'Cash' },
                { value: 'UPI', label: 'UPI' },
                { value: 'CARD', label: 'Card' },
                { value: 'UDHAAR', label: 'Udḣaar / credit' },
              ]}
              value={paymentMode}
              onChange={(v) => setPaymentMode(v as PaymentMode)}
            />
            <Input label="Paid amount (blank = full)" type="number" min="0" step="0.01" className="w-40" value={paidAmount} onChange={(e) => setPaidAmount(e.target.value)} />
            <div className="rounded-card bg-surface px-3 py-2 text-sm">
              <span className="text-ink-soft">Total: </span>
              <span className="font-bold tabular-nums text-ink">{formatCurrency(total)}</span>
            </div>
          </div>
          <div className="flex gap-2 border-t border-line pt-4">
            <Button type="submit" variant="primary" disabled={pending || form.items.length === 0}>
              Save Sale
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

function NewOrderModal({
  open,
  onClose,
  products,
  onSubmit,
  pending,
}: {
  open: boolean
  onClose: () => void
  products: Product[]
  onSubmit: (input: CreateOrderInput) => void
  pending: boolean
}) {
  const [form, setForm] = useState(EMPTY_CART)
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH')
  const [deliveryCharge, setDeliveryCharge] = useState('')
  const [customerId, setCustomerId] = useState('')
  const [addressId, setAddressId] = useState('')

  const customersQuery = useQuery({
    queryKey: ['customers', 'order-picker'],
    queryFn: () => fetchCustomers({ tab: 'all', q: '' }),
  })
  const addressQuery = useQuery({
    queryKey: ['customers', 'detail', customerId],
    queryFn: () => fetchCustomer(Number(customerId)),
    enabled: customerId !== '',
  })

  const customers: CustomerListItem[] = customersQuery.data ?? []
  const addresses = (addressQuery.data?.addresses ?? []).filter((a) => a.active)

  const onPickCustomer = (id: string) => {
    setCustomerId(id)
    setAddressId('')
  }

  const subtotal = useMemo(() => form.items.reduce((sum, it) => sum + (it.qty * it.rate || 0), 0), [form.items])
  const delivery = Number(deliveryCharge) || 0
  const discount = form.discountType === 'PERCENT' ? (subtotal * form.discount) / 100 : form.discount
  const total = Math.max(subtotal - discount + delivery, 0)

  const ready = customerId !== '' && addressId !== '' && form.items.some((it) => it.productId && it.qty > 0 && it.rate >= 0)

  return (
    <Modal isOpen={open} onClose={onClose} title={`New Order${customerId ? ` · ${customers.find((c) => String(c.id) === customerId)?.name ?? ''}` : ''}`} size="xl">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (!ready) return
          const validItems = form.items.filter((it) => it.productId && it.qty > 0 && it.rate >= 0)
          onSubmit({
            customerId: Number(customerId),
            addressId: Number(addressId),
            items: validItems,
            deliveryCharge: delivery,
            discount: Number(form.discount),
            discountType: form.discountType,
            paymentMode,
            paidAmount: 0,
            paymentReference: '',
            expectedDeliveryDate: null,
            notes: '',
          })
        }}
      >
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Select
              label="Customer *"
              value={customerId}
              onChange={onPickCustomer}
              options={[
                ...(customersQuery.isPending ? [] : [{ value: '', label: 'Select customer' }]),
                ...customers.map((c) => ({ value: String(c.id), label: `${c.name}${c.phone ? ` · ${c.phone}` : ''}` })),
              ]}
              placeholder={customersQuery.isPending ? 'Loading customers…' : 'Select customer'}
            />
            <Select
              label="Delivery address *"
              value={addressId}
              onChange={setAddressId}
              options={[
                { value: '', label: customerId === '' ? 'Select a customer first' : 'Select address' },
                ...addresses.map((a) => ({
                  value: String(a.id),
                  label: `${a.addressType.toLowerCase()} · ${a.addressLine1}${a.landmark ? `, ${a.landmark}` : ''}${a.city ? `, ${a.city}` : ''}${a.isDefault ? ' (default)' : ''}`,
                })),
              ]}
              disabled={customerId === '' || addresses.length === 0}
            />
          </div>
          <LineItemsEditor items={form.items} products={products} onChange={(items) => setForm({ ...form, items })} />
          <div className="flex flex-wrap items-end gap-3">
            <Input label="Delivery charge" type="number" min="0" step="0.01" className="w-32" value={deliveryCharge} onChange={(e) => setDeliveryCharge(e.target.value)} />
            <Input label="Discount" type="number" min="0" step="0.01" className="w-32" value={String(form.discount)} onChange={(e) => setForm({ ...form, discount: Number(e.target.value) })} />
            <Select
              label="Discount type"
              className="w-36"
              options={[
                { value: 'AMOUNT', label: 'Rs' },
                { value: 'PERCENT', label: '%' },
              ]}
              value={form.discountType}
              onChange={(v) => setForm({ ...form, discountType: v as 'PERCENT' | 'AMOUNT' })}
            />
            <Select
              label="Payment mode"
              className="w-36"
              options={[
                { value: 'CASH', label: 'Cash' },
                { value: 'UPI', label: 'UPI' },
                { value: 'CARD', label: 'Card' },
                { value: 'UDHAAR', label: 'Udḣaar / credit' },
              ]}
              value={paymentMode}
              onChange={(v) => setPaymentMode(v as PaymentMode)}
            />
            <div className="rounded-card bg-surface px-3 py-2 text-sm">
              <span className="text-ink-soft">Total: </span>
              <span className="font-bold tabular-nums text-ink">{formatCurrency(total)}</span>
            </div>
          </div>
          <div className="flex gap-2 border-t border-line pt-4">
            <Button type="submit" variant="primary" disabled={pending || !ready}>
              Save Order
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