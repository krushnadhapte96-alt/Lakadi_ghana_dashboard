import { useCallback, useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { createPortal } from 'react-dom'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'
import { useLanguage } from '@/i18n'
import type { ReturnPhoto } from '@/api/returns'
import { useReturnPhoto } from '@/components/returns/useReturnPhoto'

const THUMB = 'size-14 shrink-0'

/** Only start downloading once a thumbnail is close to the viewport. */
function useNearViewport<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  // Where IntersectionObserver is unavailable there is no viewport to wait for,
  // so those environments load straight away.
  const [near, setNear] = useState(() => typeof IntersectionObserver === 'undefined')

  useEffect(() => {
    const node = ref.current
    if (!node || near) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setNear(true)
          observer.disconnect()
        }
      },
      { rootMargin: '200px' },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [near])

  return { ref, near }
}

function Thumb({
  returnId,
  photo,
  index,
  selected,
  onOpen,
}: {
  returnId: number
  photo: ReturnPhoto
  index: number
  selected: boolean
  onOpen: (index: number) => void
}) {
  const { t } = useLanguage()
  const { ref, near } = useNearViewport<HTMLButtonElement>()
  const { url, failed } = useReturnPhoto(returnId, photo.id)
  const enabled = near || selected

  return (
    <button
      ref={ref}
      type="button"
      onClick={() => onOpen(index)}
      aria-label={t('returns.photo.open', { name: photo.fileName })}
      className={cn(
        'focus-ring relative overflow-hidden rounded-card border-2 bg-surface-3 transition-colors',
        selected ? 'border-brand-600' : 'border-line hover:border-brand-400',
        url ? 'animate-oil-fill' : '',
      )}
    >
      {enabled && url ? (
        <img src={url} alt={photo.fileName} loading="lazy" className={cn(THUMB, 'object-cover')} />
      ) : (
        <span
          className={cn(THUMB, 'flex items-center justify-center text-ink-soft/50')}
          aria-hidden="true"
        >
          <Icon name={failed ? 'x-circle' : 'image'} className="size-5" />
        </span>
      )}
    </button>
  )
}

export function PhotoLightbox({
  returnId,
  photos,
  index,
  onClose,
  onIndexChange,
}: {
  returnId: number
  photos: ReturnPhoto[]
  index: number | null
  onClose: () => void
  onIndexChange: (index: number) => void
}) {
  const { t, language } = useLanguage()
  const open = index !== null && index >= 0 && index < photos.length
  const photo = open ? photos[index as number] : null
  const { url, failed } = useReturnPhoto(returnId, photo?.id ?? 0)
  const closeRef = useRef<HTMLButtonElement>(null)

  const step = useCallback(
    (delta: number) => {
      if (index === null) return
      onIndexChange((index + delta + photos.length) % photos.length)
    },
    [index, photos.length, onIndexChange],
  )

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      if (event.key === 'ArrowRight') step(1)
      if (event.key === 'ArrowLeft') step(-1)
    }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    closeRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }, [open, onClose, step])

  if (typeof document === 'undefined') return null

  return createPortal(
    <AnimatePresence>
      {open && photo && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex flex-col bg-black/85 p-4 backdrop-blur-sm"
          onClick={(event) => {
            if (event.target === event.currentTarget) onClose()
          }}
        >
          <div className="flex items-center justify-between gap-3 text-white">
            <p className="min-w-0 truncate text-sm">
              {photo.fileName}
              <span className="ml-2 text-white/60">
                {index! + 1} / {photos.length}
              </span>
            </p>
            <button
              ref={closeRef}
              type="button"
              onClick={onClose}
              aria-label={t('common.close')}
              className="focus-ring rounded-pill p-2 text-white transition-colors hover:bg-white/10"
            >
              <Icon name="close" className="size-5" />
            </button>
          </div>

          <div className="relative flex flex-1 items-center justify-center py-3">
            {photos.length > 1 && (
              <button
                type="button"
                onClick={() => step(-1)}
                aria-label={t('returns.photo.previous')}
                className="focus-ring absolute left-0 z-10 rounded-full bg-white/10 p-2.5 text-white transition-colors hover:bg-white/20"
              >
                <Icon name="chevron-left" className="size-5" />
              </button>
            )}

            {url ? (
              <img
                src={url}
                alt={photo.fileName}
                className="max-h-full max-w-full rounded-card object-contain shadow-2xl"
              />
            ) : (
              <div className="flex flex-col items-center gap-2 text-white/70">
                <Icon name={failed ? 'x-circle' : 'image'} className="size-10" />
                <p className="text-sm">
                  {failed ? t('returns.photo.failed') : t('returns.photo.loading')}
                </p>
              </div>
            )}

            {photos.length > 1 && (
              <button
                type="button"
                onClick={() => step(1)}
                aria-label={t('returns.photo.next')}
                className="focus-ring absolute right-0 z-10 rounded-full bg-white/10 p-2.5 text-white transition-colors hover:bg-white/20"
              >
                <Icon name="chevron-right" className="size-5" />
              </button>
            )}
          </div>

          <div className="flex justify-center gap-2">
            {photos.map((entry, position) => (
              <button
                key={entry.id}
                type="button"
                onClick={() => onIndexChange(position)}
                aria-label={t('returns.photo.goTo', { index: position + 1 })}
                aria-current={position === index}
                className={cn(
                  'focus-ring size-2.5 rounded-full transition-colors',
                  position === index ? 'bg-white' : 'bg-white/40 hover:bg-white/70',
                )}
              />
            ))}
          </div>

          <p className="sr-only" aria-live="polite">
            {photo.fileName} - {new Date().toLocaleDateString(language === 'hi' ? 'hi-IN' : 'en-IN')}
          </p>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  )
}

export function PhotoStrip({
  returnId,
  photos,
  max = 4,
}: {
  returnId: number
  photos: ReturnPhoto[]
  max?: number
}) {
  const { t } = useLanguage()
  const [index, setIndex] = useState<number | null>(null)

  if (photos.length === 0) {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs text-ink-soft">
        <Icon name="image" className="size-3.5" aria-hidden="true" />
        {t('returns.photo.none')}
      </span>
    )
  }

  const shown = photos.slice(0, max)
  const hidden = photos.length - shown.length

  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        {shown.map((photo, position) => (
          <Thumb
            key={photo.id}
            returnId={returnId}
            photo={photo}
            index={position}
            selected={index === position}
            onOpen={setIndex}
          />
        ))}
        {hidden > 0 && (
          <button
            type="button"
            onClick={() => setIndex(shown.length)}
            aria-label={t('returns.photo.more', { count: hidden })}
            className="focus-ring flex size-14 shrink-0 items-center justify-center rounded-card border border-line bg-surface-3 text-xs font-semibold text-ink-soft transition-colors hover:border-brand-400 hover:text-ink"
          >
            +{hidden}
          </button>
        )}
      </div>

      <PhotoLightbox
        returnId={returnId}
        photos={photos}
        index={index}
        onIndexChange={setIndex}
        onClose={() => setIndex(null)}
      />
    </>
  )
}

