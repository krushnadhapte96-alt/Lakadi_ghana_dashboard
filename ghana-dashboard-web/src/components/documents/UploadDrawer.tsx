import { useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import {
  ACCEPTED_EXTENSIONS,
  DOCUMENT_TYPES,
  MAX_IMAGE_MB,
  MAX_VIDEO_MB,
  uploadDocument,
} from '@/api/documents'
import type { DocumentItem, DocumentType } from '@/api/documents'
import { Button, Input, Select, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { formatBytes } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'

interface UploadDrawerProps {
  open: boolean
  onClose: () => void
  onUploaded: (document: DocumentItem) => void
  /** Preset when opened from a batch page, and disables the batch selector. */
  batchId?: number | null
  batchCode?: string | null
}

const VIDEO_EXTENSIONS = ['mp4', 'webm', 'mov']

function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

/** Mirrors DocumentService.store: extension allowlist plus per-kind size cap. */
function validateFile(file: File): string | null {
  const ext = extensionOf(file.name)
  if (!ACCEPTED_EXTENSIONS.includes(ext as (typeof ACCEPTED_EXTENSIONS)[number])) return 'type'
  const limitMb = VIDEO_EXTENSIONS.includes(ext) ? MAX_VIDEO_MB : MAX_IMAGE_MB
  return file.size > limitMb * 1024 * 1024 ? 'size' : null
}

export function UploadDrawer({
  open,
  onClose,
  onUploaded,
  batchId = null,
  batchCode = null,
}: UploadDrawerProps) {
  const { t } = useLanguage()
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [type, setType] = useState<DocumentType>('LAB_REPORT')
  const [title, setTitle] = useState('')
  const [notes, setNotes] = useState('')
  const [dragging, setDragging] = useState(false)
  const [touched, setTouched] = useState(false)

  // Clear on the open edge so a cancelled upload never leaks into the next one.
  const [lastOpen, setLastOpen] = useState(open)
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) {
      setFile(null)
      setFileError(null)
      setType(batchId !== null ? 'LAB_REPORT' : 'CERTIFICATE')
      setTitle('')
      setNotes('')
      setTouched(false)
    }
  }

  const pick = (picked: File | null) => {
    if (!picked) return
    const problem = validateFile(picked)
    if (problem === 'type') {
      setFile(null)
      setFileError(t('documents.upload.typeError'))
      return
    }
    if (problem === 'size') {
      setFile(null)
      const ext = extensionOf(picked.name)
      const limit = VIDEO_EXTENSIONS.includes(ext) ? MAX_VIDEO_MB : MAX_IMAGE_MB
      setFileError(t('documents.upload.sizeError', { limit }))
      return
    }
    setFile(picked)
    setFileError(null)
    // Title defaults to the file name without its extension, still editable.
    setTitle((current) => current || picked.name.replace(/\.[^.]+$/, '').slice(0, 200))
  }

  const mutation = useMutation({
    mutationFn: () =>
      uploadDocument(file as File, {
        type,
        title: title.trim(),
        batchId: batchId ?? null,
        notes: notes.trim() || null,
      }),
    onSuccess: (uploaded) => {
      toast.success(t('documents.upload.saved'))
      onUploaded(uploaded)
      onClose()
    },
    onError: (error) =>
      toast.error(normalizeError(error).message || t('documents.upload.failed')),
  })

  const submit = () => {
    setTouched(true)
    if (!file || !title.trim()) return
    mutation.mutate()
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={t('documents.upload.title')}
      subtitle={batchCode ? t('documents.upload.forBatch', { batch: batchCode }) : undefined}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={mutation.isPending}>
            {mutation.isPending ? t('documents.upload.saving') : t('documents.upload.submit')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 p-5">
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED_EXTENSIONS.map((ext) => `.${ext}`).join(',')}
          className="sr-only"
          tabIndex={-1}
          onChange={(event) => {
            pick(event.target.files?.[0] ?? null)
            event.target.value = ''
          }}
        />

        {file ? (
          <div className="flex items-center gap-3 rounded-card border border-line bg-surface px-3 py-2.5">
            <Icon name="file" className="size-5 shrink-0 text-ink-soft" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-ink">{file.name}</p>
              <p className="text-[11px] text-ink-soft">{formatBytes(file.size)}</p>
            </div>
            <button
              type="button"
              onClick={() => {
                setFile(null)
                setFileError(null)
              }}
              aria-label={t('documents.upload.removeFile')}
              className="focus-ring rounded-pill p-1.5 text-ink-soft transition-colors hover:bg-surface-3"
            >
              <Icon name="close" className="size-4" />
            </button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            onDragOver={(event) => {
              event.preventDefault()
              setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault()
              setDragging(false)
              pick(event.dataTransfer.files?.[0] ?? null)
            }}
            className={cn(
              'focus-ring flex flex-col items-center gap-2 rounded-card border-2 border-dashed px-4 py-8 text-center transition-colors',
              dragging ? 'border-brand-500 bg-brand-50' : 'border-line-strong hover:bg-surface-3',
            )}
          >
            <Icon name="upload" className="size-6 text-ink-soft" />
            <span className="text-sm font-medium text-ink">{t('documents.upload.drop')}</span>
            <span className="text-xs text-ink-soft">{t('documents.upload.hint')}</span>
          </button>
        )}

        {fileError && (
          <p role="alert" className="text-xs text-crimson-600">
            {fileError}
          </p>
        )}
        {touched && !file && (
          <p role="alert" className="text-xs text-crimson-600">
            {t('documents.upload.fileRequired')}
          </p>
        )}

        <Input
          label={t('documents.upload.titleLabel')}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          error={touched && !title.trim() ? t('documents.errors.title') : undefined}
          maxLength={200}
        />

        <Select
          label={t('documents.upload.type')}
          value={type}
          onChange={(value) => setType(value as DocumentType)}
          options={DOCUMENT_TYPES.map((option) => ({
            value: option,
            label: t(`documents.type.${option}`),
          }))}
        />

        <Textarea
          label={t('documents.upload.notes')}
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
          rows={3}
          maxLength={1000}
          placeholder={t('documents.upload.notesPlaceholder')}
        />
      </div>
    </Sheet>
  )
}
