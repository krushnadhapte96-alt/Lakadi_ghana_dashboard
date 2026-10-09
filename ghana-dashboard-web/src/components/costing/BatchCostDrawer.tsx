import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { createPortal } from 'react-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { fetchBatchCost, recalculateBatchCost } from '@/api/costing'
import { Badge, Button, ErrorState, Skeleton } from '@/components/ui'
import { StackedCostBar } from '@/components/costing/StackedCostBar'
import { useIsDesktop } from '@/hooks/useIsDesktop'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'

export function BatchCostDrawer({
  open,
  batchId,
  onClose,
}: {
  open: boolean
  batchId: number | null
  onClose: () => void
}) {
  const { t } = useLanguage()
  const isDesktop = useIsDesktop()
  const queryClient = useQueryClient()
  const panelRef = useRef<HTMLDivElement>(null)
  const [recalculating, setRecalculating] = useState(false)

  const cost = useQuery({
    queryKey: ['costing', 'batch-cost', batchId],
    queryFn: () => fetchBatchCost(batchId as number),
    enabled: open && batchId !== null,
  })

  const recalculate = useMutation({
    mutationFn: () => recalculateBatchCost(batchId as number),
    onMutate: () => setRecalculating(true),
    onSuccess: (data) => {
      queryClient.setQueryData(['costing', 'batch-cost', batchId], data)
      void queryClient.invalidateQueries({ queryKey: ['costing', 'products'] })
      toast.success(t('costing.drawer.recalculated'))
    },
    onError: () => toast.error(t('costing.drawer.recalculateFailed')),
    onSettled: () => setRecalculating(false),
  })

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    panelRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }, [open, onClose])

  const data = cost.data
  const title = data ? t('costing.drawer.title', { code: data.batchCode }) : t('costing.drawer.titleFallback')
  const offscreen = isDesktop ? { x: '100%' } : { y: '100%' }
  const onscreen = isDesktop ? { x: 0 } : { y: 0 }

  const segments = data
    ? [
        { key: 'seed', label: t('costing.drawer.seedCost'), value: data.seedCost, barClass: 'bg-brand-500', dotClass: 'bg-brand-500' },
        { key: 'electricity', label: t('costing.drawer.electricity'), value: data.electricityCost, barClass: 'bg-gold-500', dotClass: 'bg-gold-500' },
        { key: 'labour', label: t('costing.drawer.labour'), value: data.labourCost, barClass: 'bg-blue-500', dotClass: 'bg-blue-500' },
        { key: 'other', label: t('costing.drawer.other'), value: data.otherCost, barClass: 'bg-ink-soft/60', dotClass: 'bg-ink-soft' },
        { key: 'khali', label: t('costing.drawer.khaliCredit'), value: -data.khaliCredit, barClass: '', dotClass: 'bg-clay-500' },
      ]
    : []

  return createPortal(
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-50">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="absolute inset-0 bg-black/50"
          />
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-label={title}
            tabIndex={-1}
            initial={offscreen}
            animate={onscreen}
            exit={offscreen}
            transition={{ type: 'spring', damping: 32, stiffness: 320 }}
            className="absolute inset-x-0 bottom-0 flex max-h-[92vh] flex-col overflow-hidden rounded-t-card bg-surface-2 shadow-2xl outline-none sm:inset-y-0 sm:left-auto sm:right-0 sm:max-h-none sm:w-[26rem] sm:rounded-none"
          >
            <div className="flex items-center justify-between border-b border-line px-5 py-4">
              <h2 className="text-lg font-semibold text-ink">{title}</h2>
              <button
                type="button"
                onClick={onClose}
                aria-label={t('common.close')}
                className="focus-ring rounded-pill p-2 text-ink-soft hover:bg-surface-3"
              >
                <Icon name="close" className="size-5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-5">
              {cost.isError ? (
                <ErrorState
                  title={t('costing.error.title')}
                  message={t('costing.error.body')}
                  retryLabel={t('common.retry')}
                  onRetry={() => void cost.refetch()}
                />
              ) : !data ? (
                <div className="space-y-4">
                  <Skeleton className="h-3" />
                  <Skeleton className="h-32" />
                </div>
              ) : (
                <div className="space-y-5">
                  <div className="flex flex-wrap items-center gap-2 text-sm text-ink-soft">
                    <span>{t('costing.drawer.seedKg', { value: formatQty(data.seedKg) })}</span>
                    <span>·</span>
                    <span>{t('costing.drawer.oilLitres', { value: formatQty(data.oilLitres) })}</span>
                    <span>·</span>
                    <span>{t('costing.drawer.khaliKg', { value: formatQty(data.khaliKg) })}</span>
                  </div>

                  {data.manualOverride && (
                    <Badge variant="warning">{t('costing.drawer.manualNote')}</Badge>
                  )}

                  <StackedCostBar segments={segments} />

                  <div className="space-y-1 border-t border-line pt-4">
                    <div className="flex items-center justify-between text-sm font-semibold text-ink">
                      <span>{t('costing.drawer.total')}</span>
                      <span className="tabular-nums">{formatCurrency(data.totalCost)}</span>
                    </div>
                    <div className="flex items-center justify-between text-sm text-ink-soft">
                      <span>{t('costing.drawer.perLitre')}</span>
                      <span className="tabular-nums">{formatCurrency(data.costPerLitre, { decimals: 2 })}</span>
                    </div>
                  </div>

                  <Button
                    variant="secondary"
                    size="sm"
                    className="w-full"
                    disabled={recalculating}
                    onClick={() => recalculate.mutate()}
                  >
                    <Icon name="loader" className={cn('size-4', recalculating && 'animate-spin')} />
                    {recalculating ? t('costing.drawer.recalculating') : t('costing.drawer.recalculate')}
                  </Button>
                </div>
              )}
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  )
}

