import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import {
  createBatch,
  deleteBatch,
  fetchBatches,
  fetchBatchLabel,
  recordBatchOutput,
  markBatchReady,
  bottleBatch,
  fetchSeedTypes,
  fetchOilTypes,
  type BatchStatus,
  type CreateBatchRequest,
  type RecordOutputRequest,
  type MarkReadyRequest,
  type BottleBatchRequest,
  type LabelData,
} from '@/api/production'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { BatchPanel } from '@/components/documents/BatchPanel'
import type { BatchSummary } from '@/api/production'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Modal } from '@/components/ui/Modal'
import { PageHeader } from '@/components/ui/PageHeader'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { Icon } from '@/utils/icons'
import { formatQty } from '@/utils/format'
import { cn } from '@/utils/cn'
import { initialRange } from '@/utils/dateRange'
import type { DateRangeValue } from '@/utils/dateRange'
import { readEntryAction } from '@/utils/entryPoint'

type StatusTab = 'all' | BatchStatus

const STATUS_TABS: { id: StatusTab; label: string; color: string }[] = [
  { id: 'all', label: 'All', color: 'brand' },
  { id: 'PRESSING', label: 'Pressing', color: 'warning' },
  { id: 'SETTLING', label: 'Settling', color: 'info' },
  { id: 'READY', label: 'Ready', color: 'success' },
  { id: 'BOTTLED', label: 'Bottled', color: 'primary' },
  { id: 'SOLD_OUT', label: 'Sold Out', color: 'danger' },
]

const STATUS_VARIANT: Record<BatchStatus, 'warning' | 'info' | 'success' | 'primary' | 'danger'> = {
  PRESSING: 'warning',
  SETTLING: 'info',
  READY: 'success',
  BOTTLED: 'primary',
  SOLD_OUT: 'danger',
}

export function ProductionPage() {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [activeTab, setActiveTab] = useState<StatusTab>('all')
  const [panelBatch, setPanelBatch] = useState<BatchSummary | null>(null)
  const [showNewBatch, setShowNewBatch] = useState(() => readEntryAction(searchParams) === 'batch')
  const [showOutput, setShowOutput] = useState(false)
  const [showReady, setShowReady] = useState(false)
  const [showBottle, setShowBottle] = useState(false)
  const [showLabel, setShowLabel] = useState(false)
  const [showConfirmDelete, setShowConfirmDelete] = useState(false)
  const [batchToDelete, setBatchToDelete] = useState<number | null>(null)
  const [dateRange] = useState<DateRangeValue>(initialRange)

  const [newBatchForm, setNewBatchForm] = useState<CreateBatchRequest>({
    oilTypeId: 0,
    seedTypeId: 0,
    seedKg: 0,
    pressStart: new Date().toISOString(),
  })
  const [outputForm, setOutputForm] = useState<RecordOutputRequest>({ oilLitres: 0, khaliKg: 0 })
  const [readyForm, setReadyForm] = useState<MarkReadyRequest>({ readyDate: '' })
  const [bottleForm, setBottleForm] = useState<BottleBatchRequest>({ items: [], totalLitresUsed: 0 })
  const [labelData, setLabelData] = useState<LabelData | null>(null)

  const [activeItemId, setActiveItemId] = useState<number | null>(null)

  const batchTab = activeTab === 'all' ? undefined : activeTab

  const batchesQuery = useQuery({
    queryKey: ['batches', 'list', activeTab, dateRange.range.from, dateRange.range.to],
    queryFn: () => fetchBatches({ status: batchTab, dateFrom: dateRange.range.from, dateTo: dateRange.range.to }),
    placeholderData: keepPreviousData,
  })

  const seedTypes = useQuery({ queryKey: ['seed-types'], queryFn: fetchSeedTypes, staleTime: 300_000 })
  const oilTypes = useQuery({ queryKey: ['oil-types'], queryFn: fetchOilTypes, staleTime: 300_000 })

  const createMutation = useMutation({
    mutationFn: (input: CreateBatchRequest) => createBatch(input),
    onSuccess: async () => {
      toast.success('Batch created successfully')
      setShowNewBatch(false)
      setNewBatchForm({ oilTypeId: 0, seedTypeId: 0, seedKg: 0, pressStart: new Date().toISOString() })
      await queryClient.invalidateQueries({ queryKey: ['batches'] })
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to create batch'),
  })

  const outputMutation = useMutation({
    mutationFn: (id: number) => recordBatchOutput(id, outputForm),
    onSuccess: async () => {
      toast.success('Output recorded')
      setShowOutput(false)
      setOutputForm({ oilLitres: 0, khaliKg: 0 })
      await queryClient.invalidateQueries({ queryKey: ['batches'] })
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to record output'),
  })

  const readyMutation = useMutation({
    mutationFn: (id: number) => markBatchReady(id, readyForm),
    onSuccess: async () => {
      toast.success('Batch marked as ready')
      setShowReady(false)
      setReadyForm({ readyDate: '' })
      await queryClient.invalidateQueries({ queryKey: ['batches'] })
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to mark ready'),
  })

  const bottleMutation = useMutation({
    mutationFn: (id: number) => bottleBatch(id, bottleForm),
    onSuccess: async () => {
      toast.success('Batch bottled')
      setShowBottle(false)
      setBottleForm({ items: [], totalLitresUsed: 0 })
      await queryClient.invalidateQueries({ queryKey: ['batches'] })
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to bottle batch'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteBatch(id),
    onSuccess: async () => {
      toast.success('Batch deleted, seed restored')
      setShowConfirmDelete(false)
      setBatchToDelete(null)
      await queryClient.invalidateQueries({ queryKey: ['batches'] })
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to delete batch'),
  })

  const labelMutation = useMutation({
    mutationFn: (id: number) => fetchBatchLabel(id),
    onSuccess: (data) => {
      setLabelData(data)
      setShowLabel(true)
    },
    onError: (error) => toast.error(normalizeError(error).message || 'Failed to fetch label'),
  })

  const handleDeleteClick = (id: number) => {
    setBatchToDelete(id)
    setShowConfirmDelete(true)
  }

  const handlePrintLabel = (id: number) => {
    labelMutation.mutate(id)
  }

  return (
    <div>
      <PageHeader
        title="Production"
        subtitle="Manage batch production from seed pressing to bottling"
        actions={
          <Button onClick={() => setShowNewBatch(true)}>
            <Icon name="plus" className="size-4" />
            New Batch
          </Button>
        }
      />

      <div className="border-b border-line">
        <nav className="flex gap-1 overflow-x-auto">
          {STATUS_TABS.map((tab) => {
            const isActive = activeTab === tab.id
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={cn(
                  'whitespace-nowrap px-4 py-3 text-sm font-medium transition-colors border-b-2',
                  isActive
                    ? 'border-brand-500 text-brand-600'
                    : 'border-transparent text-ink-soft hover:text-ink'
                )}
              >
                {tab.label}
              </button>
            )
          })}
        </nav>
      </div>

      <div className="mt-6">
        {batchesQuery.isError ? (
          <EmptyState icon={<Icon name="production" className="size-7" />} title="Error" description="Failed to load batches" />
        ) : !batchesQuery.data ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-44" />
            ))}
          </div>
        ) : batchesQuery.data.length === 0 ? (
          <EmptyState
            icon={<Icon name="production" className="size-7" />}
            title="No batches yet"
            description="Create your first batch to start production"
            action={<Button onClick={() => setShowNewBatch(true)}>Create Batch</Button>}
          />
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {batchesQuery.data.map((batch) => (
              <Card key={batch.id} className="p-4 hover:border-brand-300 transition-colors cursor-pointer"
                    onClick={() => setPanelBatch(batch)}>
                <div className="mb-3 flex items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-ink">{batch.code}</p>
                    <p className="text-xs text-ink-soft mt-0.5">{batch.oilTypeName}</p>
                  </div>
                  <Badge variant={STATUS_VARIANT[batch.status]} size="sm">{batch.status}</Badge>
                </div>

                <div className="mb-3 grid grid-cols-2 gap-3">
                  <div>
                    <p className="text-[10px] text-ink-soft uppercase tracking-wider">Seed</p>
                    <p className="text-sm font-semibold tabular-nums text-ink">{formatQty(batch.seedKg)} kg</p>
                  </div>
                  <div>
                    <p className="text-[10px] text-ink-soft uppercase tracking-wider">Oil</p>
                    <p className="text-sm font-semibold tabular-nums text-ink">{formatQty(batch.oilLitres)} L</p>
                  </div>
                  <div>
                    <p className="text-[10px] text-ink-soft uppercase tracking-wider">Yield</p>
                    <p className="text-sm font-semibold tabular-nums text-ink">{batch.yieldPct.toFixed(1)}%</p>
                  </div>
                  <div>
                    <p className="text-[10px] text-ink-soft uppercase tracking-wider">Settling</p>
                    <p className="text-sm font-semibold tabular-nums text-ink">{batch.daysSettling}d</p>
                  </div>
                </div>

                <div className="flex items-center justify-between">
                  <p className="text-[10px] text-ink-soft">
                    Press: {new Date(batch.pressStart).toLocaleDateString('en-IN')}
                  </p>
                  <div className="flex gap-1">
                    <button type="button" onClick={(e) => { e.stopPropagation(); setActiveItemId(batch.id); setShowOutput(true); setOutputForm({ oilLitres: 0, khaliKg: 0 }) }}
                            className="focus-ring rounded-pill px-2 py-1 text-[10px] font-medium text-ink-soft transition-colors hover:bg-surface-3"
                            disabled={batch.status !== 'PRESSING'}>
                      Output
                    </button>
                    <button type="button" onClick={(e) => { e.stopPropagation(); setActiveItemId(batch.id); setShowReady(true); setReadyForm({ readyDate: '' }) }}
                            className="focus-ring rounded-pill px-2 py-1 text-[10px] font-medium text-ink-soft transition-colors hover:bg-surface-3"
                            disabled={batch.status !== 'PRESSING'}>
                      Ready
                    </button>
                    <button type="button" onClick={(e) => { e.stopPropagation(); setActiveItemId(batch.id); setShowBottle(true); setBottleForm({ items: [], totalLitresUsed: 0 }) }}
                            className="focus-ring rounded-pill px-2 py-1 text-[10px] font-medium text-ink-soft transition-colors hover:bg-surface-3"
                            disabled={batch.status !== 'SETTLING'}>
                      Bottle
                    </button>
                    <button type="button" onClick={(e) => { e.stopPropagation(); handlePrintLabel(batch.id) }}
                            className="focus-ring rounded-pill px-2 py-1 text-[10px] font-medium text-ink-soft transition-colors hover:bg-surface-3"
                            title="Print Label">
                      <Icon name="file" className="size-3" />
                    </button>
                    <button type="button" onClick={(e) => { e.stopPropagation(); handleDeleteClick(batch.id) }}
                            className="focus-ring rounded-pill px-2 py-1 text-[10px] font-medium text-ink-soft transition-colors hover:bg-crimson-50 hover:text-crimson-600"
                            disabled={batch.status !== 'PRESSING'}
                            title="Delete">
                      <Icon name="trash" className="size-3" />
                    </button>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* New Batch Modal */}
      <Modal isOpen={showNewBatch} onClose={() => setShowNewBatch(false)} title="New Batch">
        <form onSubmit={(e) => { e.preventDefault(); if (newBatchForm.oilTypeId && newBatchForm.seedTypeId && newBatchForm.seedKg > 0) createMutation.mutate(newBatchForm) }}>
          <div className="space-y-4">
            <select className="w-full rounded-lg border border-line px-3 py-2 text-sm text-ink bg-surface"
                    value={newBatchForm.oilTypeId} onChange={(e) => setNewBatchForm({ ...newBatchForm, oilTypeId: Number(e.target.value) })}>
              <option value={0}>Select Oil Type</option>
              {(oilTypes.data ?? []).map((ot) => (
                <option key={ot.id} value={ot.id}>{ot.nameEn}</option>
              ))}
            </select>
            <select className="w-full rounded-lg border border-line px-3 py-2 text-sm text-ink bg-surface"
                    value={newBatchForm.seedTypeId} onChange={(e) => setNewBatchForm({ ...newBatchForm, seedTypeId: Number(e.target.value) })}>
              <option value={0}>Select Seed Type</option>
              {(seedTypes.data ?? []).map((st) => (
                <option key={st.id} value={st.id}>{st.nameEn}</option>
              ))}
            </select>
            <input type="number" className="w-full rounded-lg border border-line px-3 py-2 text-sm text-ink bg-surface"
                   placeholder="Seed (kg)" value={newBatchForm.seedKg}
                   onChange={(e) => setNewBatchForm({ ...newBatchForm, seedKg: Number(e.target.value) })}
                   min="0.001" step="0.001" required />
            <input type="datetime-local" className="w-full rounded-lg border border-line px-3 py-2 text-sm text-ink bg-surface"
                   value={newBatchForm.pressStart}
                   onChange={(e) => setNewBatchForm({ ...newBatchForm, pressStart: e.target.value })} required />
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={createMutation.isPending}>Create</Button>
            <Button type="button" variant="ghost" onClick={() => setShowNewBatch(false)}>Cancel</Button>
          </div>
        </form>
      </Modal>

      {/* Output Modal */}
      <Modal isOpen={showOutput} onClose={() => setShowOutput(false)} title="Record Output">
        <form onSubmit={(e) => { e.preventDefault(); if (activeItemId) outputMutation.mutate(activeItemId) }}>
          <div className="space-y-4">
            <input type="number" className="w-full rounded-lg border border-line px-3 py-2 text-sm text-ink bg-surface"
                   placeholder="Oil (L)" value={outputForm.oilLitres || ''}
                   onChange={(e) => setOutputForm({ ...outputForm, oilLitres: Number(e.target.value) })}
                   min="0.001" step="0.001" required />
            <input type="number" className="w-full rounded-lg border border-line px-3 py-2 text-sm text-ink bg-surface"
                   placeholder="Khali (kg)" value={outputForm.khaliKg || ''}
                   onChange={(e) => setOutputForm({ ...outputForm, khaliKg: Number(e.target.value) })}
                   min="0.001" step="0.001" required />
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={outputMutation.isPending}>Record</Button>
            <Button type="button" variant="ghost" onClick={() => setShowOutput(false)}>Cancel</Button>
          </div>
        </form>
      </Modal>

      {/* Ready Modal */}
      <Modal isOpen={showReady} onClose={() => setShowReady(false)} title="Mark Ready">
        <form onSubmit={(e) => { e.preventDefault(); if (activeItemId) readyMutation.mutate(activeItemId) }}>
          <div className="space-y-4">
            <input type="date" className="w-full rounded-lg border border-line px-3 py-2 text-sm text-ink bg-surface"
                   value={readyForm.readyDate} onChange={(e) => setReadyForm({ readyDate: e.target.value })} required />
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={readyMutation.isPending}>Mark Ready</Button>
            <Button type="button" variant="ghost" onClick={() => setShowReady(false)}>Cancel</Button>
          </div>
        </form>
      </Modal>

      {/* Bottle Modal */}
      <Modal isOpen={showBottle} onClose={() => setShowBottle(false)} title="Bottle Batch">
        <form onSubmit={(e) => { e.preventDefault(); if (activeItemId) bottleMutation.mutate(activeItemId) }}>
          <div className="space-y-4">
            <input type="number" className="w-full rounded-lg border border-line px-3 py-2 text-sm text-ink bg-surface"
                   placeholder="Total litres used" value={bottleForm.totalLitresUsed || ''}
                   onChange={(e) => setBottleForm({ ...bottleForm, totalLitresUsed: Number(e.target.value) })}
                   min="0.001" step="0.001" required />
            <p className="text-xs text-ink-soft">Enter items and packaging details in production interface</p>
          </div>
          <div className="mt-4 flex gap-2">
            <Button type="submit" variant="primary" disabled={bottleMutation.isPending}>Bottle</Button>
            <Button type="button" variant="ghost" onClick={() => setShowBottle(false)}>Cancel</Button>
          </div>
        </form>
      </Modal>

      {/* Label Modal */}
      <Modal isOpen={showLabel} onClose={() => setShowLabel(false)} title="Batch Label">
        {labelData && (
          <div className="space-y-3">
            <div className="rounded-lg border-2 border-dashed border-brand-300 p-6 text-center bg-surface">
              <p className="text-2xl font-bold text-ink">{labelData.code}</p>
              <p className="text-sm text-ink-soft mt-1">{labelData.oilTypeName}</p>
              <div className="mt-4 grid grid-cols-2 gap-4 text-sm">
                <div>
                  <p className="text-ink-soft">MFG</p>
                  <p className="font-semibold text-ink">{new Date(labelData.mfgDate).toLocaleDateString('en-IN')}</p>
                </div>
                <div>
                  <p className="text-ink-soft">Best Before</p>
                  <p className="font-semibold text-ink">{new Date(labelData.bestBeforeDate).toLocaleDateString('en-IN')}</p>
                </div>
                <div>
                  <p className="text-ink-soft">Oil</p>
                  <p className="font-semibold text-ink">{formatQty(labelData.oilLitres)} L</p>
                </div>
              </div>
            </div>
            <Button onClick={() => window.print()} variant="primary">Print Label</Button>
          </div>
        )}
      </Modal>

      {/* Confirm Delete */}
      <ConfirmDialog
        isOpen={showConfirmDelete}
        onClose={() => setShowConfirmDelete(false)}
        onConfirm={() => { if (batchToDelete) deleteMutation.mutate(batchToDelete) }}
        title="Delete Batch"
        message="This will delete the batch and restore the seed stock. Only allowed for PRESSING batches."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        confirmVariant="danger"
        loading={deleteMutation.isPending}
      />

      {/* Quality notes and attached files for the selected batch */}
      <BatchPanel
        batchId={panelBatch?.id ?? null}
        batchCode={panelBatch?.code ?? null}
        onClose={() => setPanelBatch(null)}
      />
    </div>
  )
}

