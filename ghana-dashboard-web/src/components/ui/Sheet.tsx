import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { createPortal } from 'react-dom'
import { Icon } from '@/utils/icons'
import { useIsDesktop } from '@/hooks/useIsDesktop'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'

export interface SheetProps {
  open: boolean
  onClose: () => void
  title: string
  subtitle?: string
  children: ReactNode
  footer?: ReactNode
  className?: string
}

/**
 * Side drawer on desktop, bottom sheet on phones - the shape the whole app
 * uses for create/edit flows.
 */
export function Sheet({
  open,
  onClose,
  title,
  subtitle,
  children,
  footer,
  className,
}: SheetProps) {
  const { t } = useLanguage()
  const isDesktop = useIsDesktop()
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    panelRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }, [open, onClose])

  const offscreen = isDesktop ? { x: '100%' } : { y: '100%' }
  const onscreen = isDesktop ? { x: 0 } : { y: 0 }

  if (typeof document === 'undefined') return null

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
            className={cn(
              'absolute inset-x-0 bottom-0 flex max-h-[92vh] flex-col overflow-hidden rounded-t-card bg-surface-2 shadow-2xl outline-none',
              'sm:inset-y-0 sm:left-auto sm:right-0 sm:max-h-none sm:w-[32rem] sm:rounded-none',
              className,
            )}
          >
            <div className="flex items-start justify-between gap-3 border-b border-line px-5 py-4">
              <div className="min-w-0">
                <h2 className="truncate text-lg font-semibold text-ink">{title}</h2>
                {subtitle && <p className="mt-0.5 truncate text-xs text-ink-soft">{subtitle}</p>}
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label={t('common.close')}
                className="focus-ring shrink-0 rounded-pill p-2 text-ink-soft hover:bg-surface-3"
              >
                <Icon name="close" className="size-5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto">{children}</div>

            {footer && (
              <div className="flex items-center justify-end gap-3 border-t border-line bg-surface-2 px-5 py-4">
                {footer}
              </div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  )
}

