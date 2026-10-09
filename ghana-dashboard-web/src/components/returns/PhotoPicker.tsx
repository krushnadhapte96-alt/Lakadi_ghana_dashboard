import { useEffect, useRef, useState } from 'react'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { Icon } from '@/utils/icons'
import { MAX_PHOTOS } from '@/api/returns'
import { PHOTO_ACCEPT, validPhotoFiles } from '@/components/returns/helpers'
import { formatBytes } from '@/utils/format'

/**
 * One staged file owns its own preview URL, so the URL is created exactly once
 * per mounted thumbnail and revoked when it is removed or replaced.
 */
function PreviewThumb({ file, onRemove }: { file: File; onRemove: () => void }) {
  const { t } = useLanguage()
  const [url] = useState(() => URL.createObjectURL(file))
  useEffect(() => () => URL.revokeObjectURL(url), [url])

  return (
    <li className="relative">
      <img src={url} alt={file.name} className="size-16 rounded-card border border-line object-cover" />
      <button
        type="button"
        onClick={onRemove}
        aria-label={t('returns.form.photoRemove', { name: file.name })}
        className="focus-ring absolute -right-1.5 -top-1.5 flex size-6 items-center justify-center rounded-full bg-crimson-600 text-white shadow-soft transition-colors hover:bg-crimson-700"
      >
        <Icon name="close" className="size-3.5" />
      </button>
    </li>
  )
}

/** Staged locally; the API only accepts photos after the return exists. */
export function PhotoPicker({
  files,
  onChange,
  error,
}: {
  files: File[]
  onChange: (files: File[]) => void
  error?: string
}) {
  const { t } = useLanguage()
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [rejected, setRejected] = useState(0)
  const full = files.length >= MAX_PHOTOS

  function add(picked: FileList | File[] | null) {
    if (!picked) return
    const incoming = [...picked]
    const { accepted, rejected: dropped } = validPhotoFiles(incoming)
    setRejected(dropped)
    onChange([...files, ...accepted].slice(0, MAX_PHOTOS))
  }

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-sm font-medium text-ink">{t('returns.form.photos')}</span>
      <input
        ref={inputRef}
        type="file"
        accept={PHOTO_ACCEPT}
        multiple
        className="sr-only"
        tabIndex={-1}
        onChange={(event) => {
          add(event.target.files)
          event.target.value = ''
        }}
      />

      {files.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {files.map((file, position) => (
            <PreviewThumb
              key={`${file.name}-${file.size}-${position}`}
              file={file}
              onRemove={() => onChange(files.filter((_, index) => index !== position))}
            />
          ))}
        </ul>
      )}

      <button
        type="button"
        disabled={full}
        onClick={() => inputRef.current?.click()}
        onDragOver={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDragging(false)
          add(event.dataTransfer.files)
        }}
        className={cn(
          'focus-ring flex flex-col items-center gap-1.5 rounded-card border-2 border-dashed px-4 py-5 text-center transition-colors',
          'disabled:cursor-not-allowed disabled:opacity-60',
          dragging
            ? 'border-brand-500 bg-brand-50 dark:bg-brand-500/10'
            : 'border-line-strong hover:bg-surface-3',
        )}
      >
        <Icon name="image" className="size-6 text-ink-soft" />
        <span className="text-sm font-medium text-ink">
          {full ? t('returns.form.photosFull') : t('returns.form.photosDrop')}
        </span>
        <span className="text-xs text-ink-soft">
          {t('returns.form.photosHint', { max: MAX_PHOTOS })}
          {files.length > 0 && (
            <>
              {' · '}
              {t('returns.form.photosSelected', { count: files.length, max: MAX_PHOTOS })}
            </>
          )}
        </span>
      </button>

      {files.length > 0 && (
        <p className="text-xs text-ink-soft">
          {t('returns.form.photoSize', { size: formatBytes(files.reduce((sum, f) => sum + f.size, 0)) })}
        </p>
      )}
      {rejected > 0 && (
        <p role="alert" className="text-xs text-crimson-600">
          {t('returns.form.photosRejected', { count: rejected })}
        </p>
      )}
      {error && (
        <p role="alert" className="text-xs text-crimson-600">
          {error}
        </p>
      )}
    </div>
  )
}

