import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { SEDIMENT_LEVELS, addQualityNote, updateQualityNote } from '@/api/documents'
import type { QualityNote, QualityNoteInput, SedimentLevel } from '@/api/documents'
import { Button, Input, Select, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'

interface QualityNoteDrawerProps {
  open: boolean
  onClose: () => void
  batchId: number
  /** Present when editing an existing observation. */
  note: QualityNote | null
  onSaved: (note: QualityNote) => void
}

interface FormState {
  colour: string
  colourScore: string
  aromaScore: string
  tasteScore: string
  clarity: string
  sedimentLevel: SedimentLevel | ''
  testedBy: string
  testedOn: string
  remarks: string
}

const EMPTY: FormState = {
  colour: '',
  colourScore: '',
  aromaScore: '',
  tasteScore: '',
  clarity: '',
  sedimentLevel: '',
  testedBy: '',
  testedOn: '',
  remarks: '',
}

function seed(note: QualityNote | null): FormState {
  if (!note) return EMPTY
  const score = (value: number | null) => (value === null ? '' : String(value))
  return {
    colour: note.colour ?? '',
    colourScore: score(note.colourScore),
    aromaScore: score(note.aromaScore),
    tasteScore: score(note.tasteScore),
    clarity: note.clarity ?? '',
    sedimentLevel: note.sedimentLevel ?? '',
    testedBy: note.testedBy ?? '',
    testedOn: note.testedOn ?? '',
    remarks: note.remarks ?? '',
  }
}

/** The backend requires at least one observation, so mirror that check. */
function hasObservation(form: FormState): boolean {
  return Boolean(
    form.colour.trim() ||
      form.colourScore ||
      form.aromaScore ||
      form.tasteScore ||
      form.clarity.trim() ||
      form.sedimentLevel ||
      form.remarks.trim(),
  )
}

const SCORE_KEYS = ['colourScore', 'aromaScore', 'tasteScore'] as const

export function QualityNoteDrawer({
  open,
  onClose,
  batchId,
  note,
  onSaved,
}: QualityNoteDrawerProps) {
  const { t } = useLanguage()
  const [form, setForm] = useState<FormState>(() => seed(note))
  const [touched, setTouched] = useState(false)

  const [seedState, setSeedState] = useState<{ open: boolean; id: number | null }>({
    open,
    id: note?.id ?? null,
  })
  if (seedState.open !== open || seedState.id !== (note?.id ?? null)) {
    setSeedState({ open, id: note?.id ?? null })
    if (open) {
      setForm(seed(note))
      setTouched(false)
    }
  }

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const scoreError = (key: (typeof SCORE_KEYS)[number]) => {
    if (!touched) return undefined
    const raw = form[key]
    if (raw === '') return undefined
    const value = Number(raw)
    return value >= 1 && value <= 5 ? undefined : t('documents.quality.scoreRange')
  }

  const needsObservation = touched && !hasObservation(form)

  const mutation = useMutation({
    mutationFn: (input: QualityNoteInput) =>
      note
        ? updateQualityNote(batchId, note.id, input)
        : addQualityNote(batchId, input),
    onSuccess: (saved) => {
      toast.success(t(note ? 'documents.quality.updated' : 'documents.quality.saved'))
      onSaved(saved)
      onClose()
    },
    onError: (error) =>
      toast.error(normalizeError(error).message || t('documents.quality.failed')),
  })

  const submit = () => {
    setTouched(true)
    if (!hasObservation(form)) return
    const toScore = (raw: string) => (raw === '' ? null : Number(raw))
    mutation.mutate({
      colour: form.colour.trim() || null,
      colourScore: toScore(form.colourScore),
      aromaScore: toScore(form.aromaScore),
      tasteScore: toScore(form.tasteScore),
      clarity: form.clarity.trim() || null,
      sedimentLevel: form.sedimentLevel || null,
      testedBy: form.testedBy.trim() || null,
      testedOn: form.testedOn || null,
      remarks: form.remarks.trim() || null,
    })
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={note ? t('documents.quality.editTitle') : t('documents.quality.title')}
      subtitle={t('documents.quality.subtitle')}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={mutation.isPending}>
            {mutation.isPending ? t('documents.quality.saving') : t('documents.quality.submit')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 p-5">
        <Input
          label={t('documents.quality.colour')}
          value={form.colour}
          onChange={(event) => set('colour', event.target.value)}
          maxLength={40}
          placeholder={t('documents.quality.colourPlaceholder')}
        />
        <Input
          label={t('documents.quality.clarity')}
          value={form.clarity}
          onChange={(event) => set('clarity', event.target.value)}
          maxLength={20}
        />

        <div className="grid grid-cols-3 gap-3">
          {SCORE_KEYS.map((key) => (
            <ScoreInput
              key={key}
              label={t(`documents.quality.${key}`)}
              value={form[key]}
              error={scoreError(key)}
              onChange={(value) => set(key, value)}
            />
          ))}
        </div>

        <Select
          label={t('documents.quality.sediment.label')}
          value={form.sedimentLevel}
          onChange={(value) => set('sedimentLevel', value as SedimentLevel | '')}
          placeholder={t('documents.quality.sediment.sedimentAny')}
          options={SEDIMENT_LEVELS.map((level) => ({
            value: level,
            label: t(`documents.quality.sediment.${level}`),
          }))}
        />

        <div className="grid grid-cols-2 gap-3">
          <Input
            label={t('documents.quality.testedBy')}
            value={form.testedBy}
            onChange={(event) => set('testedBy', event.target.value)}
            maxLength={100}
          />
          <Input
            label={t('documents.quality.testedOn')}
            type="date"
            value={form.testedOn}
            onChange={(event) => set('testedOn', event.target.value)}
          />
        </div>

        <Textarea
          label={t('documents.quality.remarks')}
          value={form.remarks}
          onChange={(event) => set('remarks', event.target.value)}
          rows={3}
          maxLength={1000}
        />

        {needsObservation && (
          <p role="alert" className="text-xs text-crimson-600">
            {t('documents.quality.needsOne')}
          </p>
        )}
      </div>
    </Sheet>
  )
}

function ScoreInput({
  label,
  value,
  error,
  onChange,
}: {
  label: string
  value: string
  error?: string
  onChange: (value: string) => void
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-sm font-medium text-ink">{label}</label>
      <input
        type="number"
        min={1}
        max={5}
        step={1}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className={cn(
          'focus-ring w-full rounded-card border bg-surface px-3.5 py-2.5 text-sm text-ink',
          'border-line hover:border-line-strong',
          error && 'border-crimson-500 focus:border-crimson-500',
        )}
        aria-invalid={error ? true : undefined}
      />
      {error && <p className="text-xs text-crimson-600">{error}</p>}
    </div>
  )
}
