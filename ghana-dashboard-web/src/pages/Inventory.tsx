import { useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import {
  adjustPackagingStock,
  adjustStock,
  addOpeningStock,
  createPackagingItem,
  createProduct,
  fetchAggregatedInventory,
  fetchOilTypes,
  fetchPackagingItems,
  fetchStockMovements,
  recordPackagingPurchase,
  updateProductPrice,
  updateProductThreshold,
  type InventoryItem,
  type PackagingItem,
  type PackagingItemInput,
  type ProductInput,
  type StockMovement,
} from '@/api/inventory'
import { Badge, Button, Card, EmptyState, ErrorState, Input, Modal, PageHeader, Select, Skeleton } from '@/components/ui'
import { cn } from '@/utils/cn'
import { formatCurrency, formatDate, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { useLanguage } from '@/i18n'

type InventoryTab = 'oil-products' | 'khali' | 'packaging' | 'movements'

const INVENTORY_TABS: { id: InventoryTab; label: string }[] = [
  { id: 'oil-products', label: 'Oil Products' },
  { id: 'khali', label: 'Khali' },
  { id: 'packaging', label: 'Packaging' },
  { id: 'movements', label: 'Stock Movements' },
]

const MOVEMENT_COLOUR: Record<string, string> = {
  OPENING: 'bg-brand-100 text-brand-700',
  BATCH_IN: 'bg-brand-100 text-brand-700',
  ADJUST_IN: 'bg-emerald-100 text-emerald-700',
  RETURN_IN: 'bg-emerald-100 text-emerald-700',
  SALE_OUT: 'bg-clay-100 text-clay-700',
  ADJUST_OUT: 'bg-clay-100 text-clay-700',
  ORDER_RESERVE: 'bg-violet-100 text-violet-700',
  ORDER_RELEASE: 'bg-brand-100 text-brand-700',
  BATCH_OUTPUT: 'bg-gold-100 text-gold-700',
  BATCH_BOTTLE: 'bg-gold-100 text-gold-700',
  BATCH_RETURN_SEED: 'bg-violet-100 text-violet-700',
  WRITE_OFF: 'bg-crimson-100 text-crimson-700',
  KHATA_OUT: 'bg-clay-100 text-clay-700',
}

const EMPTY_PRODUCT: ProductInput = {
  oilTypeId: null,
  name: '',
  packSize: 1,
  unit: 'L',
  container: 'plastic',
  sellingPrice: 0,
  mrp: 0,
  costPrice: 0,
  lowStockThreshold: 0,
  isKhali: false,
}

const EMPTY_PACKAGING: PackagingItemInput = {
  name: '',
  type: 'bottle',
  size: '',
  stockQty: 0,
  costPerUnit: 0,
  lowThreshold: 0,
}

export function InventoryPage() {
  const { language } = useLanguage()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<InventoryTab>('oil-products')

  const [showAddProduct, setShowAddProduct] = useState(false)
  const [productForm, setProductForm] = useState<ProductInput>(EMPTY_PRODUCT)
  const [showAddPackaging, setShowAddPackaging] = useState(false)
  const [packagingForm, setPackagingForm] = useState<PackagingItemInput>(EMPTY_PACKAGING)
  const [importBusy, setImportBusy] = useState(false)

  const [adjustTarget, setAdjustTarget] = useState<InventoryItem | null>(null)
  const [adjustQty, setAdjustQty] = useState('')
  const [adjustReason, setAdjustReason] = useState('manual')
  const [adjustNote, setAdjustNote] = useState('')

  const [openingTarget, setOpeningTarget] = useState<InventoryItem | null>(null)
  const [openingForm, setOpeningForm] = useState({ qty: '', costPrice: '', receivedDate: '', expiryDate: '' })

  const [packTarget, setPackTarget] = useState<PackagingItem | null>(null)
  const [packMode, setPackMode] = useState<'purchase' | 'adjust'>('purchase')
  const [packForm, setPackForm] = useState({ qty: '', costPerUnit: '', purchaseDate: '', note: '' })

  const [priceTarget, setPriceTarget] = useState<InventoryItem | null>(null)
  const [priceForm, setPriceForm] = useState({ sellingPrice: '', mrp: '' })

  const fileRef = useRef<HTMLInputElement>(null)

  const inventory = useQuery({ queryKey: ['inventory', 'aggregate'], queryFn: fetchAggregatedInventory })
  const packaging = useQuery({ queryKey: ['packaging-items'], queryFn: fetchPackagingItems })
  const oilTypes = useQuery({ queryKey: ['oil-types'], queryFn: fetchOilTypes, staleTime: 300_000 })
  const movements = useQuery({
    queryKey: ['inventory', 'movements'],
    queryFn: () => fetchStockMovements(0, 50),
  })

  const invalidateAll = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['inventory'] }),
      queryClient.invalidateQueries({ queryKey: ['packaging-items'] }),
      queryClient.invalidateQueries({ queryKey: ['products'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
  }

  const createProductMutation = useMutation({
    mutationFn: createProduct,
    onSuccess: async () => {
      toast.success('Product added')
      setShowAddProduct(false)
      setProductForm(EMPTY_PRODUCT)
      await invalidateAll()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to add product'),
  })

  const createPackagingMutation = useMutation({
    mutationFn: createPackagingItem,
    onSuccess: async () => {
      toast.success('Packaging item added')
      setShowAddPackaging(false)
      setPackagingForm(EMPTY_PACKAGING)
      await invalidateAll()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to add packaging item'),
  })

  const adjustMutation = useMutation({
    mutationFn: async () => {
      if (!adjustTarget) return
      await adjustStock(adjustTarget.productId, { qty: Number(adjustQty), reason: adjustReason, note: adjustNote })
    },
    onSuccess: async () => {
      toast.success('Stock adjusted')
      setAdjustTarget(null)
      setAdjustQty('')
      setAdjustNote('')
      await invalidateAll()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Adjustment failed'),
  })

  const openingMutation = useMutation({
    mutationFn: async () => {
      if (!openingTarget) return
      await addOpeningStock(openingTarget.productId, {
        qty: Number(openingForm.qty),
        costPrice: Number(openingForm.costPrice),
        receivedDate: openingForm.receivedDate,
        expiryDate: openingForm.expiryDate || null,
      })
    },
    onSuccess: async () => {
      toast.success('Opening stock added')
      setOpeningTarget(null)
      setOpeningForm({ qty: '', costPrice: '', receivedDate: '', expiryDate: '' })
      await invalidateAll()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to add opening stock'),
  })

  const packMutation = useMutation({
    mutationFn: async () => {
      if (!packTarget) return
      if (packMode === 'purchase') {
        await recordPackagingPurchase(packTarget.id, {
          qty: Number(packForm.qty),
          unitCost: Number(packForm.costPerUnit),
          purchaseDate: packForm.purchaseDate,
          note: packForm.note,
        })
      } else {
        await adjustPackagingStock(packTarget.id, { qty: Number(packForm.qty), reason: 'manual', note: packForm.note })
      }
    },
    onSuccess: async () => {
      toast.success(packMode === 'purchase' ? 'Purchase recorded' : 'Packaging stock adjusted')
      setPackTarget(null)
      setPackForm({ qty: '', costPerUnit: '', purchaseDate: '', note: '' })
      await invalidateAll()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Operation failed'),
  })

  const priceMutation = useMutation({
    mutationFn: async () => {
      if (!priceTarget) return
      await updateProductPrice(priceTarget.productId, Number(priceForm.sellingPrice), Number(priceForm.mrp))
    },
    onSuccess: async () => {
      toast.success('Price updated')
      setPriceTarget(null)
      await invalidateAll()
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to update price'),
  })

  const thresholdMutation = useMutation({
    mutationFn: async (item: InventoryItem) => {
      const next = window.prompt(`Low stock threshold for ${item.name}`, String(item.lowStockThreshold))
      if (next === null) return
      const value = Number(next)
      if (!Number.isFinite(value) || value < 0) throw new Error('Enter a valid number')
      await updateProductThreshold(item.productId, value)
      await invalidateAll()
      toast.success('Threshold updated')
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to update threshold'),
  })

  const importMutation = useMutation<{ ok: number; failed: number; errors: string[] }, Error, string[][]>({
    mutationFn: async (rows) => {
      let ok = 0
      let failed = 0
      const errors: string[] = []
      for (const row of rows) {
        const input: ProductInput = {
          name: row[0]?.trim() ?? '',
          packSize: Number(row[1] ?? 1),
          unit: (row[2]?.trim().toUpperCase() as ProductInput['unit']) || 'L',
          container: (row[3]?.trim().toLowerCase() as ProductInput['container']) || 'plastic',
          sellingPrice: Number(row[4] ?? 0),
          mrp: Number(row[5] ?? row[4] ?? 0),
          costPrice: Number(row[6] ?? 0),
          lowStockThreshold: Number(row[7] ?? 0),
          oilTypeId: null,
          isKhali: (row[8]?.trim().toLowerCase() ?? '') === 'true' || (row[8]?.trim() ?? '') === '1',
        }
        try {
          await createProduct(input)
          ok += 1
        } catch (err) {
          failed += 1
          errors.push(`${input.name}: ${normalizeError(err).message}`)
        }
      }
      return { ok, failed, errors }
    },
    onSuccess: async ({ ok, failed, errors }) => {
      setImportBusy(false)
      if (fileRef.current) fileRef.current.value = ''
      if (ok > 0) toast.success(`Imported ${ok} product${ok === 1 ? '' : 's'}`)
      if (failed > 0) toast.error(`${failed} failed — ${errors[0] ?? ''}`)
      await invalidateAll()
    },
    onError: (error) => {
      setImportBusy(false)
      toast.error(normalizeError(error).message || 'Import failed')
    },
  })

  async function handleImportFile(file: File) {
    setImportBusy(true)
    const text = await file.text()
    const rows = text
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .slice(1)
      .map((line) => line.split(',').map((cell) => cell.replace(/^"|"$/g, '').trim()))
      .filter((row) => row.length >= 1 && row[0])
    if (rows.length === 0) {
      toast.error('No rows found in file')
      setImportBusy(false)
      if (fileRef.current) fileRef.current.value = ''
      return
    }
    importMutation.mutate(rows)
  }

  const totals = useMemo(() => {
    const agg = inventory.data
    if (!agg) return { products: 0, lowStock: 0, packaging: 0, packagingLow: 0 }
    const lowStock = [...agg.oilProducts, ...agg.khaliProducts].filter((p) => p.lowStock).length
    const packagingLow = agg.packagingItems.filter((p) => p.lowStock).length
    return {
      products: agg.oilProducts.length + agg.khaliProducts.length,
      lowStock,
      packaging: agg.packagingItems.length,
      packagingLow,
    }
  }, [inventory.data])

  const oilOptions = (oilTypes.data ?? []).map((ot) => ({ value: String(ot.id), label: ot.nameEn }))

  return (
    <div>
      <PageHeader
        title="Inventory"
        subtitle="Track oil product stock, khali, packaging and movement history"
        actions={
          <>
            <input
              ref={fileRef}
              type="file"
              accept=".csv,text/csv"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) void handleImportFile(file)
              }}
            />
            <Button variant="secondary" size="sm" onClick={() => fileRef.current?.click()} disabled={importBusy}>
              <Icon name="upload" className="size-4" />
              {importBusy ? 'Importing…' : 'Import'}
            </Button>
            <Button variant="primary" size="sm" onClick={() => setShowAddProduct(true)}>
              <Icon name="plus" className="size-4" />
              Add Product
            </Button>
          </>
        }
      />

      <div className="mb-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <SummaryCard label="Oil products" value={totals.products} icon="stock" />
        <SummaryCard label="Low stock" value={totals.lowStock} icon="alert-circle" emphasise={totals.lowStock > 0} />
        <SummaryCard label="Packaging items" value={totals.packaging} icon="grid" />
        <SummaryCard label="Packaging low" value={totals.packagingLow} icon="filter" emphasise={totals.packagingLow > 0} />
      </div>

      <div className="border-b border-line">
        <nav className="flex gap-1 overflow-x-auto">
          {INVENTORY_TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={cn(
                'whitespace-nowrap border-b-2 px-4 py-3 text-sm font-medium transition-colors',
                activeTab === tab.id ? 'border-brand-500 text-brand-600' : 'border-transparent text-ink-soft hover:text-ink',
              )}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      <div className="mt-6">
        {activeTab === 'oil-products' || activeTab === 'khali' ? (
          <OilProductList
            items={activeTab === 'khali' ? inventory.data?.khaliProducts ?? [] : inventory.data?.oilProducts ?? []}
            isError={inventory.isError}
            isPending={!inventory.data}
            emptyTitle={activeTab === 'khali' ? 'No khali products yet' : 'No oil products yet'}
            emptyAction={
              <Button size="sm" onClick={() => setShowAddProduct(true)}>
                <Icon name="plus" className="size-4" />
                Add Product
              </Button>
            }
            onAdjust={(item) => {
              setAdjustTarget(item)
              setAdjustQty('')
              setAdjustNote('')
              setAdjustReason('manual')
            }}
            onOpening={(item) => {
              setOpeningTarget(item)
              setOpeningForm({ qty: '', costPrice: '', receivedDate: '', expiryDate: '' })
            }}
            onPrice={(item) => {
              setPriceTarget(item)
              setPriceForm({ sellingPrice: String(item.sellingPrice), mrp: String(item.sellingPrice) })
            }}
            onThreshold={(item) => thresholdMutation.mutate(item)}
            addPackaging={() => setShowAddPackaging(true)}
          />
        ) : activeTab === 'packaging' ? (
          <PackagingList
            items={packaging.data ?? []}
            isError={packaging.isError}
            isPending={!packaging.data}
            onAdd={() => setShowAddPackaging(true)}
            onPurchase={(item) => {
              setPackTarget(item)
              setPackMode('purchase')
              setPackForm({ qty: '', costPerUnit: String(item.costPerUnit), purchaseDate: '', note: '' })
            }}
            onAdjust={(item) => {
              setPackTarget(item)
              setPackMode('adjust')
              setPackForm({ qty: '', costPerUnit: '', purchaseDate: '', note: '' })
            }}
          />
        ) : (
          <MovementsList
            movements={movements.data?.content ?? []}
            isError={movements.isError}
            isPending={!movements.data}
            language={language}
          />
        )}
      </div>

      <Modal isOpen={showAddProduct} onClose={() => setShowAddProduct(false)} title="Add Product">
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (productForm.name.trim() && productForm.packSize > 0) createProductMutation.mutate(productForm)
          }}
        >
          <div className="space-y-4">
            <Input
              label="Product name"
              value={productForm.name}
              onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
              required
            />
            <Select
              label="Oil type"
              options={oilOptions}
              value={productForm.oilTypeId ? String(productForm.oilTypeId) : ''}
              onChange={(v) => setProductForm({ ...productForm, oilTypeId: Number(v) })}
              placeholder="No oil type"
            />
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Pack size"
                type="number"
                min="0.001"
                step="0.001"
                value={String(productForm.packSize)}
                onChange={(e) => setProductForm({ ...productForm, packSize: Number(e.target.value) })}
                required
              />
              <Select
                label="Unit"
                options={[
                  { value: 'L', label: 'Litre (L)' },
                  { value: 'ML', label: 'Millilitre (ML)' },
                  { value: 'KG', label: 'Kilogram (KG)' },
                ]}
                value={productForm.unit}
                onChange={(v) => setProductForm({ ...productForm, unit: v as ProductInput['unit'] })}
              />
            </div>
            <Select
              label="Container"
              options={[
                { value: 'glass', label: 'Glass' },
                { value: 'plastic', label: 'Plastic' },
                { value: 'tin', label: 'Tin' },
                { value: 'bag', label: 'Bag' },
              ]}
              value={productForm.container}
              onChange={(v) => setProductForm({ ...productForm, container: v as ProductInput['container'] })}
            />
            <div className="grid grid-cols-3 gap-3">
              <Input
                label="Selling price"
                type="number"
                min="0"
                step="0.01"
                value={String(productForm.sellingPrice)}
                onChange={(e) => setProductForm({ ...productForm, sellingPrice: Number(e.target.value) })}
                required
              />
              <Input
                label="MRP"
                type="number"
                min="0"
                step="0.01"
                value={String(productForm.mrp)}
                onChange={(e) => setProductForm({ ...productForm, mrp: Number(e.target.value) })}
              />
              <Input
                label="Cost price"
                type="number"
                min="0"
                step="0.01"
                value={String(productForm.costPrice)}
                onChange={(e) => setProductForm({ ...productForm, costPrice: Number(e.target.value) })}
              />
            </div>
            <Input
              label="Low stock threshold"
              type="number"
              min="0"
              value={String(productForm.lowStockThreshold)}
              onChange={(e) => setProductForm({ ...productForm, lowStockThreshold: Number(e.target.value) })}
            />
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={createProductMutation.isPending}>
              Add Product
            </Button>
            <Button type="button" variant="ghost" onClick={() => setShowAddProduct(false)}>
              Cancel
            </Button>
          </div>
        </form>
      </Modal>

      <Modal isOpen={showAddPackaging} onClose={() => setShowAddPackaging(false)} title="Add Packaging Item">
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (packagingForm.name.trim()) createPackagingMutation.mutate(packagingForm)
          }}
        >
          <div className="space-y-4">
            <Input
              label="Name"
              value={packagingForm.name}
              onChange={(e) => setPackagingForm({ ...packagingForm, name: e.target.value })}
              required
            />
            <Select
              label="Type"
              options={[
                { value: 'bottle', label: 'Bottle' },
                { value: 'cap', label: 'Cap' },
                { value: 'label', label: 'Label' },
                { value: 'tin', label: 'Tin' },
                { value: 'bag', label: 'Bag' },
                { value: 'carton', label: 'Carton' },
              ]}
              value={packagingForm.type}
              onChange={(v) => setPackagingForm({ ...packagingForm, type: v as PackagingItemInput['type'] })}
            />
            <Input
              label="Size (e.g. 28mm, 1L)"
              value={packagingForm.size}
              onChange={(e) => setPackagingForm({ ...packagingForm, size: e.target.value })}
            />
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Opening stock"
                type="number"
                min="0"
                step="0.001"
                value={String(packagingForm.stockQty)}
                onChange={(e) => setPackagingForm({ ...packagingForm, stockQty: Number(e.target.value) })}
              />
              <Input
                label="Cost per unit"
                type="number"
                min="0"
                step="0.01"
                value={String(packagingForm.costPerUnit)}
                onChange={(e) => setPackagingForm({ ...packagingForm, costPerUnit: Number(e.target.value) })}
              />
            </div>
            <Input
              label="Low threshold"
              type="number"
              min="0"
              value={String(packagingForm.lowThreshold)}
              onChange={(e) => setPackagingForm({ ...packagingForm, lowThreshold: Number(e.target.value) })}
            />
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={createPackagingMutation.isPending}>
              Add Item
            </Button>
            <Button type="button" variant="ghost" onClick={() => setShowAddPackaging(false)}>
              Cancel
            </Button>
          </div>
        </form>
      </Modal>

      <Modal isOpen={adjustTarget !== null} onClose={() => setAdjustTarget(null)} title={`Adjust Stock — ${adjustTarget?.name ?? ''}`}>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            const qty = Number(adjustQty)
            if (adjustTarget && Number.isFinite(qty) && qty !== 0) adjustMutation.mutate()
          }}
        >
          <div className="space-y-4">
            <Input
              label="Quantity (negative to reduce)"
              type="number"
              step="0.001"
              value={adjustQty}
              onChange={(e) => setAdjustQty(e.target.value)}
              required
            />
            <Input
              label="Reason"
              value={adjustReason}
              onChange={(e) => setAdjustReason(e.target.value)}
              required
            />
            <Input label="Note" value={adjustNote} onChange={(e) => setAdjustNote(e.target.value)} />
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={adjustMutation.isPending || !adjustTarget}>
              Adjust
            </Button>
            <Button type="button" variant="ghost" onClick={() => setAdjustTarget(null)}>
              Cancel
            </Button>
          </div>
        </form>
      </Modal>

      <Modal isOpen={openingTarget !== null} onClose={() => setOpeningTarget(null)} title={`Opening Stock — ${openingTarget?.name ?? ''}`}>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (openingTarget && Number(openingForm.qty) > 0) openingMutation.mutate()
          }}
        >
          <div className="space-y-4">
            <Input
              label="Quantity"
              type="number"
              min="0.001"
              step="0.001"
              value={openingForm.qty}
              onChange={(e) => setOpeningForm({ ...openingForm, qty: e.target.value })}
              required
            />
            <Input
              label="Cost price per unit"
              type="number"
              min="0"
              step="0.01"
              value={openingForm.costPrice}
              onChange={(e) => setOpeningForm({ ...openingForm, costPrice: e.target.value })}
              required
            />
            <Input
              label="Received date"
              type="date"
              value={openingForm.receivedDate}
              onChange={(e) => setOpeningForm({ ...openingForm, receivedDate: e.target.value })}
            />
            <Input
              label="Expiry date (optional)"
              type="date"
              value={openingForm.expiryDate}
              onChange={(e) => setOpeningForm({ ...openingForm, expiryDate: e.target.value })}
            />
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={openingMutation.isPending || !openingTarget}>
              Add Stock
            </Button>
            <Button type="button" variant="ghost" onClick={() => setOpeningTarget(null)}>
              Cancel
            </Button>
          </div>
        </form>
      </Modal>

      <Modal
        isOpen={packTarget !== null}
        onClose={() => setPackTarget(null)}
        title={`${packMode === 'purchase' ? 'Record Purchase' : 'Adjust Stock'} — ${packTarget?.name ?? ''}`}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (packTarget && Number(packForm.qty) !== 0) packMutation.mutate()
          }}
        >
          <div className="space-y-4">
            <Input
              label="Quantity"
              type="number"
              step="0.001"
              value={packForm.qty}
              onChange={(e) => setPackForm({ ...packForm, qty: e.target.value })}
              required
            />
            {packMode === 'purchase' && (
              <>
                <Input
                  label="Cost per unit"
                  type="number"
                  min="0"
                  step="0.01"
                  value={packForm.costPerUnit}
                  onChange={(e) => setPackForm({ ...packForm, costPerUnit: e.target.value })}
                  required
                />
                <Input
                  label="Purchase date"
                  type="date"
                  value={packForm.purchaseDate}
                  onChange={(e) => setPackForm({ ...packForm, purchaseDate: e.target.value })}
                />
              </>
            )}
            <Input label="Note" value={packForm.note} onChange={(e) => setPackForm({ ...packForm, note: e.target.value })} />
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={packMutation.isPending || !packTarget}>
              Save
            </Button>
            <Button type="button" variant="ghost" onClick={() => setPackTarget(null)}>
              Cancel
            </Button>
          </div>
        </form>
      </Modal>

      <Modal isOpen={priceTarget !== null} onClose={() => setPriceTarget(null)} title={`Update Price — ${priceTarget?.name ?? ''}`}>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (priceTarget && Number(priceForm.sellingPrice) >= 0) priceMutation.mutate()
          }}
        >
          <div className="space-y-4">
            <Input
              label="Selling price"
              type="number"
              min="0"
              step="0.01"
              value={priceForm.sellingPrice}
              onChange={(e) => setPriceForm({ ...priceForm, sellingPrice: e.target.value })}
              required
            />
            <Input
              label="MRP"
              type="number"
              min="0"
              step="0.01"
              value={priceForm.mrp}
              onChange={(e) => setPriceForm({ ...priceForm, mrp: e.target.value })}
            />
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={priceMutation.isPending}>Save</Button>
            <Button type="button" variant="ghost" onClick={() => setPriceTarget(null)}>Cancel</Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}

function SummaryCard({ label, value, icon, emphasise }: { label: string; value: number; icon: string; emphasise?: boolean }) {
  return (
    <Card className="p-4">
      <div className="flex items-center gap-3">
        <span
          className={cn(
            'flex size-10 items-center justify-center rounded-card',
            emphasise ? 'bg-crimson-50 text-crimson-600' : 'bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300',
          )}
        >
          <Icon name={icon as 'stock'} className="size-5" />
        </span>
        <div className="min-w-0">
          <p className="text-xs text-ink-soft">{label}</p>
          <p className={cn('text-2xl font-bold tabular-nums', emphasise ? 'text-crimson-600' : 'text-ink')}>{value}</p>
        </div>
      </div>
    </Card>
  )
}

function OilProductList({
  items,
  isError,
  isPending,
  emptyTitle,
  emptyAction,
  onAdjust,
  onOpening,
  onPrice,
  onThreshold,
  addPackaging,
}: {
  items: InventoryItem[]
  isError: boolean
  isPending: boolean
  emptyTitle: string
  emptyAction: React.ReactNode
  onAdjust: (item: InventoryItem) => void
  onOpening: (item: InventoryItem) => void
  onPrice: (item: InventoryItem) => void
  onThreshold: (item: InventoryItem) => void
  addPackaging: () => void
}) {
  if (isError) {
    return (
      <ErrorState icon={<Icon name="alert-circle" className="size-7" />} title="Could not load inventory" message="The inventory feed is unavailable." retryLabel="Retry" />
    )
  }
  if (isPending) {
    return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-40" />)}
      </div>
    )
  }
  if (items.length === 0) {
    return (
      <EmptyState
        icon={<Icon name="stock" className="size-8" />}
        title={emptyTitle}
        description="Add your first product to start tracking stock."
        action={emptyAction}
      />
    )
  }
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {items.map((item) => (
        <Card key={item.productId} className="p-4">
          <div className="mb-3 flex items-start justify-between gap-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-ink">{item.name}</p>
              <p className="mt-0.5 text-xs text-ink-soft">{item.sku}</p>
            </div>
            <Badge size="sm" variant={item.lowStock ? 'danger' : 'success'}>
              {item.lowStock ? 'LOW' : 'OK'}
            </Badge>
          </div>
          <p className="mb-3 text-2xl font-bold tabular-nums text-ink">
            {formatQty(item.qtyAvailable)} <span className="text-sm font-medium text-ink-soft">{item.unit}</span>
          </p>
          <div className="mb-3 grid grid-cols-2 gap-2 text-xs text-ink-soft">
            <span>Selling: <span className="font-semibold text-ink">{formatCurrency(item.sellingPrice)}</span></span>
            <span>Cost: <span className="font-semibold text-ink">{formatCurrency(item.costPrice)}</span></span>
          </div>
          <div className="flex flex-wrap gap-1">
            <ActionChip onClick={() => onAdjust(item)}>Adjust</ActionChip>
            <ActionChip onClick={() => onOpening(item)}>Opening</ActionChip>
            <ActionChip onClick={() => onPrice(item)}>Price</ActionChip>
            <ActionChip onClick={() => onThreshold(item)}>Threshold</ActionChip>
            <ActionChip onClick={addPackaging}>Add Packaging</ActionChip>
          </div>
        </Card>
      ))}
    </div>
  )
}

function PackagingList({
  items,
  isError,
  isPending,
  onAdd,
  onPurchase,
  onAdjust,
}: {
  items: PackagingItem[]
  isError: boolean
  isPending: boolean
  onAdd: () => void
  onPurchase: (item: PackagingItem) => void
  onAdjust: (item: PackagingItem) => void
}) {
  if (isError) {
    return (
      <ErrorState icon={<Icon name="alert-circle" className="size-7" />} title="Could not load packaging" message="The packaging feed is unavailable." retryLabel="Retry" />
    )
  }
  if (isPending) {
    return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-32" />)}
      </div>
    )
  }
  if (items.length === 0) {
    return (
      <EmptyState
        icon={<Icon name="grid" className="size-8" />}
        title="No packaging items yet"
        description="Add bottles, caps, labels and more so you can track them."
        action={<Button size="sm" onClick={onAdd}><Icon name="plus" className="size-4" />Add Packaging Item</Button>}
      />
    )
  }
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {items.map((item) => (
        <Card key={item.id} className="p-4">
          <div className="mb-3 flex items-start justify-between gap-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-ink">{item.name}</p>
              <p className="mt-0.5 text-xs text-ink-soft">{item.type}{item.size ? ` · ${item.size}` : ''}</p>
            </div>
            <Badge size="sm" variant={item.lowStock ? 'danger' : 'success'}>
              {item.lowStock ? 'LOW' : 'OK'}
            </Badge>
          </div>
          <p className="mb-3 text-2xl font-bold tabular-nums text-ink">{formatQty(item.stockQty)} <span className="text-sm font-medium text-ink-soft">units</span></p>
          <div className="flex flex-wrap gap-1">
            <ActionChip onClick={() => onPurchase(item)}>Purchase</ActionChip>
            <ActionChip onClick={() => onAdjust(item)}>Adjust</ActionChip>
          </div>
        </Card>
      ))}
    </div>
  )
}

function MovementsList({
  movements,
  isError,
  isPending,
  language,
}: {
  movements: StockMovement[]
  isError: boolean
  isPending: boolean
  language: 'en' | 'hi' | 'mr'
}) {
  if (isError) {
    return <ErrorState icon={<Icon name="alert-circle" className="size-7" />} title="Could not load movements" message="The movement feed is unavailable." retryLabel="Retry" />
  }
  if (isPending) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-12" />)}
      </div>
    )
  }
  if (movements.length === 0) {
    return (
      <EmptyState icon={<Icon name="list" className="size-8" />} title="No movements yet" description="Product stock movements will appear here." />
    )
  }
  return (
    <Card className="overflow-hidden">
      <ul className="divide-y divide-line">
        {movements.map((m) => (
          <li key={m.id} className="flex items-center gap-3 px-4 py-3">
            <span className={cn('shrink-0 rounded-pill px-2 py-0.5 text-[10px] font-semibold', MOVEMENT_COLOUR[m.type] ?? 'bg-surface-3 text-ink-soft')}>
              {m.type}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-ink">{m.productName}</p>
              <p className="truncate text-xs text-ink-soft">{m.reason || m.note || ''}{m.batchId ? ` · Batch ${m.batchId}` : ''}</p>
            </div>
            <p className={cn('shrink-0 text-sm font-bold tabular-nums', m.changeQty > 0 ? 'text-emerald-600' : 'text-clay-700')}>
              {m.changeQty > 0 ? '+' : ''}{formatQty(m.changeQty)}
            </p>
            <p className="hidden shrink-0 text-xs text-ink-soft sm:block">{formatDate(m.createdAt, language)}</p>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function ActionChip({ onClick, children }: { onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="focus-ring rounded-pill border border-line bg-surface-2 px-2.5 py-1 text-[11px] font-medium text-ink-soft transition-colors hover:bg-surface-3 hover:text-ink"
    >
      {children}
    </button>
  )
}