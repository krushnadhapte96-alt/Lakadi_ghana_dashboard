import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import {
  deleteQualityNote,
  fetchBatchDocuments,
  fetchQualityNotes,
} from '@/api/documents'
import type { DocumentItem, QualityNote } from '@/api/documents'
import { DocumentThumb } from '@/components/documents/DocumentThumb'
import { DocumentLightbox } from '@/components/documents/DocumentLightbox'
import { useLightbox } from '@/components/documents/useLightbox'
import { downloadDocument } from '@/components/documents/downloadDocument'
import { QualityNoteDrawer } from '@/components/documents/QualityNoteDrawer'
import { UploadDrawer } from '@/components/documents/UploadDrawer'
import { Badge, Button, EmptyState, ErrorState, Sheet, Skeleton, Tabs } from '@/components/ui'
import { useLanguage } from '@/i18n'
import type { Language } from '@/i18n'
import { formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'

type BatchTab = 'quality' | 'documents'

interface BatchPanelProps {
  batchId: number | null
  batchCode: string | null
  onClose: () => void
}

/** The batch page's Quality/Documents tabs: observations plus attached files. */
export function BatchPanel({ batchId, batchCode, onClose }: BatchPanelProps) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<BatchTab>('quality')
  const [noteOpen, setNoteOpen] = useState(false)
  const [editing, setEditing] = useState<QualityNote | null>(null)
  const [uploadOpen, setUploadOpen] = useState(false)

  const notes = useQuery({
    queryKey: ['batch-quality', batchId],
    queryFn: () => fetchQualityNotes(batchId as number),
    enabled: batchId !== null,
  })

  const files = useQuery({
    queryKey: ['batch-documents', batchId],
    queryFn: () => fetchBatchDocuments(batchId as number),
    enabled: batchId !== null,
  })

  const items = files.data ?? []
  const { index, open, close, setIndex } = useLightbox(items)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['batch-quality', batchId] })
    queryClient.invalidateQueries({ queryKey: ['batch-documents', batchId] })
    queryClient.invalidateQueries({ queryKey: ['documents'] })
  }

  const deleteNote = useMutation({
    mutationFn: (noteId: number) => deleteQualityNote(batchId as number, noteId),
    onSuccess: () => {
      toast.success(t('documents.quality.deleted'))
      invalidate()
    },
    onError: (error) =>
      toast.error(normalizeError(error).message || t('documents.quality.deleteFailed')),
  })

  return (
    <>
      <Sheet
        open={batchId !== null}
        onClose={onClose}
        title={batchCode ?? t('documents.batch.title')}
        subtitle={t('documents.batch.subtitle')}
        className="sm:w-[36rem]"
        footer={
          <>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setUploadOpen(true)}
              disabled={batchId === null}
            >
              <Icon name="upload" className="size-4" />
              {t('documents.upload.title')}
            </Button>
            <Button
              size="sm"
              onClick={() => {
                setEditing(null)
                setNoteOpen(true)
              }}
              disabled={batchId === null}
            >
              <Icon name="plus" className="size-4" />
              {t('documents.quality.add')}
            </Button>
          </>
        }
      >
        <Tabs
          className="px-5"
          activeTab={tab}
          onTabChange={(next) => setTab(next as BatchTab)}
          tabs={[
            { id: 'quality', label: t('documents.tabs.quality'), count: notes.data?.length },
            { id: 'documents', label: t('documents.tabs.documents'), count: items.length },
          ]}
        />

        {tab === 'quality' ? (
          <QualityTab
            query={notes}
            onAdd={() => {
              setEditing(null)
              setNoteOpen(true)
            }}
            onEdit={(note) => {
              setEditing(note)
              setNoteOpen(true)
            }}
            onDelete={(noteId) => deleteNote.mutate(noteId)}
            deleting={deleteNote.isPending}
          />
        ) : (
          <BatchFilesTab
            query={files}
            onUpload={() => setUploadOpen(true)}
            onOpen={open}
          />
        )}
      </Sheet>

      {batchId !== null && (
        <>
          <QualityNoteDrawer
            open={noteOpen}
            onClose={() => setNoteOpen(false)}
            batchId={batchId}
            note={editing}
            onSaved={invalidate}
          />
          <UploadDrawer
            open={uploadOpen}
            onClose={() => setUploadOpen(false)}
            onUploaded={invalidate}
            batchId={batchId}
            batchCode={batchCode}
          />
        </>
      )}

      {index !== null && (
        <DocumentLightbox
          documents={items}
          index={index}
          onClose={close}
          onIndexChange={setIndex}
        />
      )}
    </>
  )
}

type Query<T> = {
  data?: T
  isPending: boolean
  isError: boolean
  error: unknown
  refetch: () => unknown
}

function QualityTab({
  query,
  onAdd,
  onEdit,
  onDelete,
  deleting,
}: {
  query: Query<QualityNote[]>
  onAdd: () => void
  onEdit: (note: QualityNote) => void
  onDelete: (noteId: number) => void
  deleting: boolean
}) {
  const { t, language } = useLanguage()

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-3 p-5">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    )
  }

  if (query.isError) {
    return (
      <ErrorState
        title={t('documents.quality.error')}
        message={normalizeError(query.error).message}
        onRetry={() => query.refetch()}
        retryLabel={t('common.retry')}
      />
    )
  }

  const notes = query.data ?? []

  if (notes.length === 0) {
    return (
      <EmptyState
        icon={<Icon name="check" className="size-7" />}
        title={t('documents.quality.empty')}
        description={t('documents.quality.emptyBody')}
        action={
          <Button variant="outline" size="sm" onClick={onAdd}>
            {t('documents.quality.add')}
          </Button>
        }
      />
    )
  }

  return (
    <ul className="flex flex-col gap-3 p-5">
      {notes.map((note) => (
        <QualityCard
          key={note.id}
          note={note}
          language={language}
          onEdit={() => onEdit(note)}
          onDelete={() => onDelete(note.id)}
          deleting={deleting}
        />
      ))}
    </ul>
  )
}

function BatchFilesTab({
  query,
  onUpload,
  onOpen,
}: {
  query: Query<DocumentItem[]>
  onUpload: () => void
  onOpen: (item: DocumentItem) => void
}) {
  const { t } = useLanguage()

  if (query.isPending) {
    return (
      <div className="grid grid-cols-2 gap-3 p-5 sm:grid-cols-3">
        {[0, 1, 2].map((n) => (
          <Skeleton key={n} className="aspect-4/3 w-full" />
        ))}
      </div>
    )
  }

  if (query.isError) {
    return (
      <ErrorState
        title={t('documents.error.title')}
        message={normalizeError(query.error).message}
        onRetry={() => query.refetch()}
        retryLabel={t('common.retry')}
      />
    )
  }

  const items = query.data ?? []

  if (items.length === 0) {
    return (
      <EmptyState
        icon={<Icon name="documents" className="size-7" />}
        title={t('documents.batch.empty')}
        description={t('documents.batch.emptyBody')}
        action={
          <Button variant="outline" size="sm" onClick={onUpload}>
            {t('documents.upload.title')}
          </Button>
        }
      />
    )
  }

  return (
    <ul className="grid grid-cols-2 gap-3 p-5 sm:grid-cols-3">
      {items.map((item) => (
        <li key={item.id}>
          <button
            type="button"
            onClick={() => onOpen(item)}
            className="focus-ring w-full rounded-card"
          >
            <DocumentThumb document={item} />
          </button>
          <p className="mt-1 truncate px-0.5 text-[11px] text-ink-soft">{item.title}</p>
          <button
            type="button"
            onClick={() => downloadDocument(item)}
            className="focus-ring mt-0.5 inline-flex items-center gap-1 rounded-pill px-1.5 py-0.5 text-[11px] text-brand-600 transition-colors hover:bg-brand-50"
          >
            <Icon name="download" className="size-3" />
            {t('documents.download')}
          </button>
        </li>
      ))}
    </ul>
  )
}

function QualityCard({
  note,
  language,
  onEdit,
  onDelete,
  deleting,
}: {
  note: QualityNote
  language: Language
  onEdit: () => void
  onDelete: () => void
  deleting: boolean
}) {
  const { t } = useLanguage()
  const scores = [
    { key: 'colourScore', value: note.colourScore },
    { key: 'aromaScore', value: note.aromaScore },
    { key: 'tasteScore', value: note.tasteScore },
  ].filter((score) => score.value !== null)

  const stamp = note.testedOn ?? note.createdAt

  return (
    <li className="rounded-card border border-line bg-surface p-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          {note.colour && <p className="truncate text-sm font-semibold text-ink">{note.colour}</p>}
          <p className="text-[11px] text-ink-soft">
            {formatDate(stamp, language)}
            {note.testedBy ? ` · ${note.testedBy}` : ''}
          </p>
        </div>
        <div className="flex shrink-0 gap-1">
          <button
            type="button"
            onClick={onEdit}
            aria-label={t('documents.quality.edit')}
            className="focus-ring rounded-pill p-1.5 text-ink-soft transition-colors hover:bg-surface-3"
          >
            <Icon name="pencil" className="size-3.5" />
          </button>
          <button
            type="button"
            onClick={onDelete}
            disabled={deleting}
            aria-label={t('common.delete')}
            className="focus-ring rounded-pill p-1.5 text-ink-soft transition-colors hover:bg-crimson-50 hover:text-crimson-600 disabled:opacity-50"
          >
            <Icon name="trash" className="size-3.5" />
          </button>
        </div>
      </div>

      {(scores.length > 0 || note.sedimentLevel || note.clarity) && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {scores.map((score) => (
            <Badge key={score.key} variant="primary" size="sm">
              {t(`documents.quality.${score.key}`).replace(/\s*\(.*\)$/, '')} {score.value}/5
            </Badge>
          ))}
          {note.clarity && (
            <Badge variant="info" size="sm">
              {note.clarity}
            </Badge>
          )}
          {note.sedimentLevel && (
            <Badge
              variant={
                note.sedimentLevel === 'HIGH'
                  ? 'danger'
                  : note.sedimentLevel === 'MEDIUM'
                    ? 'warning'
                    : 'success'
              }
              size="sm"
            >
              {t('documents.quality.sediment.label')}:{' '}
              {t(`documents.quality.sediment.${note.sedimentLevel}`)}
            </Badge>
          )}
        </div>
      )}

      {note.remarks && <p className="mt-2 text-sm text-ink">{note.remarks}</p>}
    </li>
  )
}
