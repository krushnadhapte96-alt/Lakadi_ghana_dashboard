import { useEffect, useRef } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { createPortal } from 'react-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchSeedPurchase } from '@/api/beej'
import type { SeedType, Supplier } from '@/api/beej'
import { PurchaseForm } from '@/components/beej/PurchaseForm'
import { ErrorState, Skeleton } from '@/components/ui'
import { useIsDesktop } from '@/hooks/useIsDesktop'
import { useLanguage } from '@/i18n'
import { Icon } from '@/utils/icons'

export function PurchaseDrawer({
  open,
  purchaseId,
  seedTypes,
  suppliers,
  onClose,
}: {
  open: boolean
  purchaseId: number | null
  seedTypes: SeedType[]
  suppliers: Supplier[]
  onClose: () => void
}) {
  const { t } = useLanguage()
  const isDesktop = useIsDesktop()
  const panelRef = useRef<HTMLDivElement>(null)

  const detail = useQuery({
    queryKey: ['seed-purchases', 'detail', purchaseId],
    queryFn: () => fetchSeedPurchase(purchaseId as number),
    enabled: open && purchaseId !== null,
    staleTime: 0,
    gcTime: 0,
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

  const title = t(purchaseId === null ? 'beej.purchase.add' : 'beej.purchase.edit')
  const offscreen = isDesktop ? { x: '100%' } : { y: '100%' }
  const onscreen = isDesktop ? { x: 0 } : { y: 0 }

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
            className="absolute inset-x-0 bottom-0 flex max-h-[92vh] flex-col overflow-hidden rounded-t-card bg-surface-2 shadow-2xl outline-none sm:inset-y-0 sm:left-auto sm:right-0 sm:max-h-none sm:w-[30rem] sm:rounded-none"
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

            {purchaseId === null ? (
              <PurchaseForm key="new" purchase={null} seedTypes={seedTypes} suppliers={suppliers} onDone={onClose} />
            ) : detail.isError ? (
              <ErrorState
                title={t('beej.purchases.loadFailed')}
                retryLabel={t('common.retry')}
                onRetry={() => void detail.refetch()}
              />
            ) : detail.data ? (
              <PurchaseForm
                key={detail.data.id}
                purchase={detail.data}
                seedTypes={seedTypes}
                suppliers={suppliers}
                onDone={onClose}
              />
            ) : (
              <div className="space-y-4 p-5">
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
                <Skeleton className="h-32" />
              </div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  )
}

