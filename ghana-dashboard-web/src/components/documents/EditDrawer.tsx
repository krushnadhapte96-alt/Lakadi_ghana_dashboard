import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { DOCUMENT_TYPES, updateDocument } from '@/api/documents'
import type { DocumentItem, DocumentType } from '@/api/documents'
import { Button, Input, Select, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'

interface EditDrawerProps {
  open: boolean
  onClose: () => void
  document: DocumentItem | null
  onSaved: (document: DocumentItem) => void
}

/** Renames/retypes an existing file. The binary itself is immutable server-side. */
export function EditDrawer({ open, onClose, document, onSaved }: EditDrawerProps) {
  const { t } = useLanguage()
  const [title, setTitle] = useState('')
  const [type, setType] = useState<DocumentType>('OTHER')
  const [notes, setNotes] = useState('')
  const [touched, setTouched] = useState(false)

  const [seed, setSeed] = useState<{ open: boolean; id: number | null }>({
    open,
    id: document?.id ?? null,
  })
  if (seed.open !== open || seed.id !== (document?.id ?? null)) {
    setSeed({ open, id: document?.id ?? null })
    if (open && document) {
      setTitle(document.title)
      setType(document.type)
      setNotes(document.notes ?? '')
      setTouched(false)
    }
  }

  const mutation = useMutation({
    mutationFn: () =>
      updateDocument(document!.id, {
        title: title.trim(),
        type,
        batchId: document!.batchId,
        notes: notes.trim() || null,
      }),
    onSuccess: (saved) => {
      toast.success(t('documents.updated'))
      onSaved(saved)
      onClose()
    },
    onError: (error) =>
      toast.error(normalizeError(error).message || t('documents.updateFailed')),
  })

  const submit = () => {
    setTouched(true)
    if (!title.trim() || !document) return
    mutation.mutate()
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={t('documents.edit.title')}
      subtitle={document?.fileName}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={mutation.isPending}>
            {mutation.isPending ? t('documents.edit.saving') : t('documents.edit.submit')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 p-5">
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
        />
        {document?.batchCode && (
          <p className="text-xs text-ink-soft">
            {t('documents.edit.batch', { batch: document.batchCode })}
          </p>
        )}
      </div>
    </Sheet>
  )
}
