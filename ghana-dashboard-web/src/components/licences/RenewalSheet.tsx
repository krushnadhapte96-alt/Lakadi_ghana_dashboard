import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import type { Licence } from '@/api/licences'
import { useLicenceMutations } from '@/components/licences/useLicenceMutations'
import { validateLicenceFile } from '@/components/licences/helpers'
import { Button, Input, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { formatBytes, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'

interface RenewalSheetProps {
  open: boolean
  onClose: () => void
  licence: Licence | null
}

function addYears(iso: string, years: number): string {
  const date = new Date(iso)
  date.setFullYear(date.getFullYear() + years)
  return date.toISOString().slice(0, 10)
}

/**
 * Renewing appends to the licence's history and moves the expiry forward.
 * Attaching a file is the "Upload renewal" path; submitting without one is
 * "Mark renewed" for licences renewed offline.
 */
export function RenewalSheet({ open, onClose, licence }: RenewalSheetProps) {
  const { t, language } = useLanguage()
  const inputRef = useRef<HTMLInputElement>(null)
  const { renew } = useLicenceMutations()

  const [issueDate, setIssueDate] = useState('')
  const [expiryDate, setExpiryDate] = useState('')
  const [newNumber, setNewNumber] = useState('')
  const [notes, setNotes] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const [touched, setTouched] = useState(false)

  // Default the new window to the previous length, which is what most renewals are.
  const [seed, setSeed] = useState({ open, id: licence?.id ?? null })
  if (open !== seed.open || (licence?.id ?? null) !== seed.id) {
    setSeed({ open, id: licence?.id ?? null })
    if (open && licence) {
      const today = new Date().toISOString().slice(0, 10)
      const previousYears =
        new Date(licence.expiryDate).getFullYear() - new Date(licence.issueDate).getFullYear()
      const years = previousYears > 0 ? previousYears : 1
      setIssueDate(today)
      setExpiryDate(addYears(today, years))
      setNewNumber('')
      setNotes('')
      setFile(null)
      setFileError(null)
      setTouched(false)
    }
  }

  const pick = (picked: File | null) => {
    if (!picked) return
    const problem = validateLicenceFile(picked)
    if (problem === 'type') {
      setFile(null)
      setFileError(t('licences.form.typeError'))
      return
    }
    if (problem === 'size') {
      setFile(null)
      setFileError(t('licences.form.sizeError', { limit: 10 }))
      return
    }
    setFile(picked)
    setFileError(null)
  }

  const invalidRange = Boolean(issueDate && expiryDate && expiryDate < issueDate)
  const canSubmit = Boolean(issueDate && expiryDate && !invalidRange)

  const submit = () => {
    if (!licence) return
    setTouched(true)
    if (!canSubmit) return
    renew
      .mutateAsync({
        id: licence.id,
        input: {
          newNumber: newNumber.trim() || null,
          issueDate,
          expiryDate,
          notes: notes.trim() || null,
        },
        file,
      })
      .then(() => {
        toast.success(t('licences.renew.done'))
        onClose()
      })
      .catch((error) =>
        toast.error(normalizeError(error).message || t('licences.renew.failed')),
      )
  }

  const busy = renew.isPending

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={t('licences.renew.title')}
      subtitle={licence?.licenceNumber}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={busy || !licence}>
            {busy ? t('licences.renew.working') : t('licences.renew.submit')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 p-5">
        {licence && (
          <div className="rounded-card border border-line bg-surface px-3 py-2.5 text-sm">
            <p className="text-ink-soft">{t('licences.renew.currentExpiry')}</p>
            <p className="font-medium tabular-nums text-ink">
              {formatDate(licence.expiryDate, language)}
            </p>
            {licence.renewalCount > 0 && (
              <p className="mt-1 text-xs text-ink-soft">
                {t('licences.renew.previousCount', { count: licence.renewalCount })}
              </p>
            )}
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          <Input
            type="date"
            label={t('licences.form.issueDate')}
            value={issueDate}
            onChange={(event) => setIssueDate(event.target.value)}
            error={touched && !issueDate ? t('licences.errors.issueDate') : undefined}
          />
          <Input
            type="date"
            label={t('licences.form.expiryDate')}
            value={expiryDate}
            onChange={(event) => setExpiryDate(event.target.value)}
            error={
              (touched && !expiryDate) || invalidRange
                ? invalidRange
                  ? t('licences.errors.expiryOrder')
                  : t('licences.errors.expiryDate')
                : undefined
            }
          />
        </div>

        <Input
          label={t('licences.renew.newNumber')}
          value={newNumber}
          onChange={(event) => setNewNumber(event.target.value)}
          maxLength={50}
          hint={t('licences.renew.newNumberHint')}
          placeholder={licence?.licenceNumber ?? ''}
        />

        <input
          ref={inputRef}
          type="file"
          accept=".jpg,.jpeg,.png,.webp,.pdf"
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
              aria-label={t('licences.form.removeFile')}
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
              'focus-ring flex flex-col items-center gap-2 rounded-card border-2 border-dashed px-4 py-6 text-center transition-colors',
              dragging ? 'border-brand-500 bg-brand-50' : 'border-line-strong hover:bg-surface-3',
            )}
          >
            <Icon name="upload" className="size-6 text-ink-soft" />
            <span className="text-sm font-medium text-ink">{t('licences.renew.drop')}</span>
            <span className="text-xs text-ink-soft">{t('licences.renew.dropHint')}</span>
          </button>
        )}

        {fileError && (
          <p role="alert" className="text-xs text-crimson-600">
            {fileError}
          </p>
        )}

        <Textarea
          label={t('licences.form.notes')}
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
          rows={3}
          maxLength={500}
          placeholder={t('licences.renew.notesPlaceholder')}
        />
      </div>
    </Sheet>
  )
}
