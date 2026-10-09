import { useRef, useState } from 'react'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { Icon } from '@/utils/icons'
import { BILL_ACCEPT } from '@/components/expenses/helpers'
import { formatBytes } from '@/utils/format'

export interface ExistingBill {
  name: string | null
  size: number | null
}

export function BillUpload({
  file,
  existing,
  error,
  onFile,
  onRemoveExisting,
}: {
  file: File | null
  existing: ExistingBill | null
  error?: string
  onFile: (file: File | null) => void
  onRemoveExisting: () => void
}) {
  const { t } = useLanguage()
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  function pick(files: FileList | null) {
    const picked = files?.[0]
    if (picked) onFile(picked)
  }

  const shown = file ? { name: file.name, size: file.size } : existing ? { name: existing.name, size: existing.size } : null

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-sm font-medium text-ink">{t('expenses.form.bill')}</span>
      <input
        ref={inputRef}
        type="file"
        accept={BILL_ACCEPT}
        className="sr-only"
        tabIndex={-1}
        onChange={(e) => {
          pick(e.target.files)
          e.target.value = ''
        }}
      />

      {shown ? (
        <div className="flex items-center gap-3 rounded-card border border-line bg-surface px-3.5 py-3">
          <Icon name={file?.type.startsWith('image/') ? 'image' : 'file'} className="size-5 shrink-0 text-ink-soft" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-ink">{shown.name}</p>
            <p className="text-xs text-ink-soft">
              {file ? '' : `${t('expenses.form.billCurrent')} · `}
              {shown.size != null ? formatBytes(shown.size) : ''}
            </p>
          </div>
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className="focus-ring rounded-pill px-2.5 py-1 text-xs font-semibold text-brand-600 hover:bg-surface-3 dark:text-brand-300"
          >
            {t('expenses.form.billReplace')}
          </button>
          <button
            type="button"
            aria-label={t('expenses.form.billRemove')}
            onClick={() => (file ? onFile(null) : onRemoveExisting())}
            className="focus-ring rounded-pill p-1.5 text-ink-soft hover:bg-surface-3 hover:text-crimson-600"
          >
            <Icon name="trash" className="size-4" />
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault()
            setDragging(false)
            pick(e.dataTransfer.files)
          }}
          className={cn(
            'focus-ring flex flex-col items-center gap-1.5 rounded-card border-2 border-dashed px-4 py-6 text-center transition-colors',
            dragging ? 'border-brand-500 bg-brand-50 dark:bg-brand-500/10' : 'border-line-strong hover:bg-surface-3',
          )}
        >
          <Icon name="upload" className="size-6 text-ink-soft" />
          <span className="text-sm font-medium text-ink">{t('expenses.form.billDrop')}</span>
          <span className="text-xs text-ink-soft">{t('expenses.form.billHint')}</span>
        </button>
      )}
      {error && (
        <p role="alert" className="text-xs text-crimson-600">
          {error}
        </p>
      )}
    </div>
  )
}

