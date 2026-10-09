import { useCallback, useEffect } from 'react'
import { createPortal } from 'react-dom'
import type { DocumentItem } from '@/api/documents'
import { KindBadge } from '@/components/documents/DocumentThumb'
import { useFileUrl } from '@/components/documents/useObjectUrl'
import { useLanguage } from '@/i18n'
import { formatBytes, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'

interface DocumentLightboxProps {
  documents: DocumentItem[]
  index: number
  onClose: () => void
  onIndexChange: (index: number) => void
}

/**
 * One viewer for all three kinds: images and video stream from the resolved blob,
 * PDFs go to an <iframe> so the browser's own reader handles printing and zoom.
 */
export function DocumentLightbox({
  documents,
  index,
  onClose,
  onIndexChange,
}: DocumentLightboxProps) {
  const { t, language } = useLanguage()
  const item = documents[index]
  const url = useFileUrl(item.id)

  const step = useCallback(
    (delta: number) => {
      const next = index + delta
      if (next < 0 || next >= documents.length) return
      onIndexChange(next)
    },
    [index, documents.length, onIndexChange],
  )

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      if (event.key === 'ArrowRight') step(1)
      if (event.key === 'ArrowLeft') step(-1)
    }
    window.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }, [onClose, step])

  if (typeof document === 'undefined') return null

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={item.title}
      className="fixed inset-0 z-50 flex flex-col bg-black/90"
      onClick={onClose}
    >
      <div
        className="flex items-center justify-between gap-3 px-4 py-3 text-white"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{item.title}</p>
          <p className="truncate text-[11px] text-white/70">
            {item.fileName} · {formatBytes(item.fileSize)} · {formatDate(item.createdAt, language)}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          <span className="text-xs tabular-nums text-white/70">
            {index + 1} / {documents.length}
          </span>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('common.close')}
            className="focus-ring rounded-pill p-2 text-white transition-colors hover:bg-white/10"
          >
            <Icon name="close" className="size-5" />
          </button>
        </div>
      </div>

      <div
        className="relative flex flex-1 items-center justify-center px-2 pb-4 sm:px-14"
        onClick={(event) => event.stopPropagation()}
      >
        {documents.length > 1 && (
          <NavButton side="left" onClick={() => step(-1)} label={t('common.previous')} />
        )}

        <div className="flex size-full items-center justify-center">
          {!url ? (
            <KindBadge kind={item.kind} label={t('documents.loading')} />
          ) : item.kind === 'IMAGE' ? (
            <img src={url} alt={item.title} className="max-h-full max-w-full rounded-card object-contain" />
          ) : item.kind === 'VIDEO' ? (
            <video src={url} controls autoPlay className="max-h-full max-w-full rounded-card" />
          ) : (
            <iframe
              src={url}
              title={item.title}
              className="h-full w-full max-w-4xl rounded-card border border-line bg-surface"
            />
          )}
        </div>

        {documents.length > 1 && (
          <NavButton side="right" onClick={() => step(1)} label={t('common.next')} />
        )}
      </div>
    </div>,
    document.body,
  )
}

function NavButton({
  side,
  onClick,
  label,
}: {
  side: 'left' | 'right'
  onClick: () => void
  label: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={cn(
        'focus-ring absolute z-10 flex size-10 items-center justify-center rounded-full bg-black/60 text-white transition-colors hover:bg-black/80',
        side === 'left' ? 'left-2' : 'right-2',
      )}
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-5">
        {side === 'left' ? <path d="m15 18-6-6 6-6" /> : <path d="m9 18 6-6-6-6" />}
      </svg>
    </button>
  )
}


