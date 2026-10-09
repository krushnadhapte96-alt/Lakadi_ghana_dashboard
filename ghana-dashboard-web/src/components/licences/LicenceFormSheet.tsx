import { useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { LICENCE_TYPES } from '@/api/licences'
import type { Licence, LicenceInput, LicenceType } from '@/api/licences'
import { useLicenceMutations } from '@/components/licences/useLicenceMutations'
import { validateLicenceFile } from '@/components/licences/helpers'
import { Button, Input, Select, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { formatBytes } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'

interface LicenceFormSheetProps {
  open: boolean
  onClose: () => void
  /** Set for editing; a new licence starts from a year of validity. */
  licence: Licence | null
}

const ONE_YEAR_MS = 365 * 24 * 60 * 60 * 1000

function isoDate(offsetMs: number): string {
  return new Date(Date.now() + offsetMs).toISOString().slice(0, 10)
}

export function LicenceFormSheet({ open, onClose, licence }: LicenceFormSheetProps) {
  const { t } = useLanguage()
  const inputRef = useRef<HTMLInputElement>(null)
  const { create, update } = useLicenceMutations()

  const [licenceNumber, setLicenceNumber] = useState('')
  const [type, setType] = useState<LicenceType>('FSSAI')
  const [issuer, setIssuer] = useState('')
  const [issueDate, setIssueDate] = useState(isoDate(0))
  const [expiryDate, setExpiryDate] = useState(isoDate(ONE_YEAR_MS))
  const [fssaiGstin, setFssaiGstin] = useState('')
  const [notes, setNotes] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const [touched, setTouched] = useState(false)

  // Reseed on the open edge, and whenever the target licence changes underneath.
  const [seed, setSeed] = useState({ open, id: licence?.id ?? null })
  if (open !== seed.open || (licence?.id ?? null) !== seed.id) {
    setSeed({ open, id: licence?.id ?? null })
    if (open) {
      setLicenceNumber(licence?.licenceNumber ?? '')
      setType(licence?.type ?? 'FSSAI')
      setIssuer(licence?.issuer ?? '')
      setIssueDate(licence?.issueDate ?? isoDate(0))
      setExpiryDate(licence?.expiryDate ?? isoDate(ONE_YEAR_MS))
      setFssaiGstin(licence?.fssaiGstin ?? '')
      setNotes(licence?.notes ?? '')
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
    // The number is the one thing users always know, so default it to the filename.
    setLicenceNumber((current) => current || picked.name.replace(/\.[^.]+$/, '').slice(0, 50))
  }

  const input: LicenceInput = {
    licenceNumber: licenceNumber.trim(),
    type,
    issuer: issuer.trim() || null,
    issueDate,
    expiryDate,
    fssaiGstin: fssaiGstin.trim() || null,
    notes: notes.trim() || null,
  }

  const invalidRange = Boolean(issueDate && expiryDate && expiryDate < issueDate)
  const canSubmit = Boolean(licenceNumber.trim() && issueDate && expiryDate && !invalidRange)

  const save = useMutation({
    mutationFn: () => {
      // Editing changes metadata only; the stored scan is replaced via Upload.
      return licence
        ? update.mutateAsync({ id: licence.id, input })
        : create.mutateAsync({ input, file })
    },
    onSuccess: () => {
      toast.success(licence ? t('licences.form.updated') : t('licences.form.saved'))
      onClose()
    },
    onError: (error) => toast.error(normalizeError(error).message || t('licences.form.saveFailed')),
  })

  const submit = () => {
    setTouched(true)
    if (!canSubmit) return
    save.mutate()
  }

  const busy = save.isPending

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={licence ? t('licences.form.editTitle') : t('licences.form.addTitle')}
      subtitle={licence?.licenceNumber}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={busy}>
            {busy ? t('licences.form.saving') : t('licences.form.submit')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 p-5">
        <Input
          label={t('licences.form.number')}
          value={licenceNumber}
          onChange={(event) => setLicenceNumber(event.target.value)}
          error={touched && !licenceNumber.trim() ? t('licences.errors.number') : undefined}
          maxLength={50}
          placeholder={t('licences.form.numberPlaceholder')}
        />

        <Select
          label={t('licences.form.type')}
          value={type}
          onChange={(value) => setType(value as LicenceType)}
          options={LICENCE_TYPES.map((option) => ({
            value: option,
            label: t(`licences.type.${option}`),
          }))}
        />

        <Input
          label={t('licences.form.issuer')}
          value={issuer}
          onChange={(event) => setIssuer(event.target.value)}
          maxLength={120}
          placeholder={t('licences.form.issuerPlaceholder')}
        />

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
          label={t('licences.form.registrationNumber')}
          value={fssaiGstin}
          onChange={(event) => setFssaiGstin(event.target.value)}
          maxLength={100}
          hint={t('licences.form.registrationHint')}
          placeholder={t('licences.form.registrationPlaceholder')}
        />

        {!licence && (
          <>
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
                <span className="text-sm font-medium text-ink">{t('licences.form.drop')}</span>
                <span className="text-xs text-ink-soft">{t('licences.form.dropHint')}</span>
              </button>
            )}
            {fileError && (
              <p role="alert" className="text-xs text-crimson-600">
                {fileError}
              </p>
            )}
          </>
        )}

        <Textarea
          label={t('licences.form.notes')}
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
          rows={3}
          maxLength={500}
          placeholder={t('licences.form.notesPlaceholder')}
        />
      </div>
    </Sheet>
  )
}
