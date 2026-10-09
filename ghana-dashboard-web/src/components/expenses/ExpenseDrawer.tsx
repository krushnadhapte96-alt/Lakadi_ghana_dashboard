import { useEffect, useRef } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { createPortal } from 'react-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchExpense } from '@/api/expenses'
import type { ExpenseCategory } from '@/api/expenses'
import { ExpenseForm } from '@/components/expenses/ExpenseForm'
import { ErrorState, Skeleton } from '@/components/ui'
import { useIsDesktop } from '@/hooks/useIsDesktop'
import { useLanguage } from '@/i18n'
import { Icon } from '@/utils/icons'

export function ExpenseDrawer({
  open,
  expenseId,
  categories,
  onClose,
}: {
  open: boolean
  expenseId: number | null
  categories: ExpenseCategory[]
  onClose: () => void
}) {
  const { t } = useLanguage()
  const isDesktop = useIsDesktop()
  const panelRef = useRef<HTMLDivElement>(null)

  const detail = useQuery({
    queryKey: ['expenses', 'detail', expenseId],
    queryFn: () => fetchExpense(expenseId as number),
    enabled: open && expenseId !== null,
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

  const title = t(expenseId === null ? 'expenses.add' : 'expenses.edit')
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

            {expenseId === null ? (
              <ExpenseForm key="new" expense={null} categories={categories} onDone={onClose} />
            ) : detail.isError ? (
              <ErrorState
                title={t('expenses.loadFailed')}
                message={t('expenses.error.body')}
                retryLabel={t('common.retry')}
                onRetry={() => void detail.refetch()}
              />
            ) : detail.data ? (
              <ExpenseForm key={detail.data.id} expense={detail.data} categories={categories} onDone={onClose} />
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

