import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { DOCUMENT_TYPES, deleteDocument, fetchDocuments } from '@/api/documents'
import type { DocumentFilters, DocumentItem, DocumentType } from '@/api/documents'
import { useIsOwner } from '@/auth/useCan'
import { DocumentThumb } from '@/components/documents/DocumentThumb'
import { DocumentLightbox } from '@/components/documents/DocumentLightbox'
import { useLightbox } from '@/components/documents/useLightbox'
import { downloadDocument } from '@/components/documents/downloadDocument'
import { EditDrawer } from '@/components/documents/EditDrawer'
import { UploadDrawer } from '@/components/documents/UploadDrawer'
import { BatchPanel } from '@/components/documents/BatchPanel'
import {
  Button,
  EmptyState,
  ErrorState,
  Input,
  PageHeader,
  Select,
  Skeleton,
  ConfirmDialog,
} from '@/components/ui'
import { useDebounced } from '@/hooks/useDebounced'
import { useLanguage } from '@/i18n'
import { formatBytes, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { normalizeError } from '@/api/axios'

/** The gallery page: typed filters, protected previews, upload/edit, and batch panel entry. */
export default function Documents() {
  const { t, language } = useLanguage()
  const queryClient = useQueryClient()
  const isOwner = useIsOwner()
  const [filters, setFilters] = useState<DocumentFilters>({ type: '', q: '' })
  const debouncedQuery = useDebounced(filters.q, 300)
  const effectiveFilters = { type: filters.type, q: debouncedQuery }

  const documentsQuery = useQuery({
    queryKey: ['documents', effectiveFilters],
    queryFn: () => fetchDocuments(effectiveFilters),
  })

  const items = documentsQuery.data ?? []
  const { index, open, close, setIndex } = useLightbox(items)

  const [uploadOpen, setUploadOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<DocumentItem | null>(null)
  const [toDelete, setToDelete] = useState<DocumentItem | null>(null)
  const [batchId, setBatchId] = useState<number | null>(null)
  const [batchCode, setBatchCode] = useState<string | null>(null)

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteDocument(id),
    onSuccess: () => {
      toast.success(t('documents.deleted'))
      setToDelete(null)
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
    onError: (error) =>
      toast.error(normalizeError(error).message || t('documents.deleteFailed')),
  })

  const handleUploaded = (doc: DocumentItem) => {
    queryClient.invalidateQueries({ queryKey: ['documents'] })
    if (doc.batchId) {
      queryClient.invalidateQueries({ queryKey: ['documents', { batchId: doc.batchId }] })
      queryClient.invalidateQueries({ queryKey: ['batch-quality', doc.batchId] })
    }
  }

  const handleSaved = (doc: DocumentItem) => {
    queryClient.invalidateQueries({ queryKey: ['documents'] })
    if (doc.batchId) {
      queryClient.invalidateQueries({ queryKey: ['documents', { batchId: doc.batchId }] })
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t('documents.title')}
        subtitle={t('documents.subtitle')}
        actions={
          <Button onClick={() => setUploadOpen(true)}>
            <Icon name="upload" className="size-4" />
            {t('documents.upload.title')}
          </Button>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="grid flex-1 grid-cols-1 gap-3 sm:grid-cols-5">
          <Input
            label={t('documents.filters.search')}
            placeholder={t('documents.filters.searchPlaceholder')}
            value={filters.q}
            onChange={(event) => setFilters((prev) => ({ ...prev, q: event.target.value }))}
          />
          <Select
            label={t('documents.filters.type')}
            value={filters.type}
            onChange={(value) => setFilters((prev) => ({ ...prev, type: value as DocumentType | '' }))}
            placeholder={t('documents.filters.typeAll')}
            options={DOCUMENT_TYPES.map((type) => ({
              value: type,
              label: t(`documents.type.${type}`),
            }))}
            className="col-span-1 sm:col-span-2"
          />
        </div>
      </div>

      {documentsQuery.isPending ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5">
          {[0, 1, 2, 3, 4].map((n) => (
            <Skeleton key={n} className="aspect-4/3 w-full" />
          ))}
        </div>
      ) : documentsQuery.isError ? (
        <ErrorState
          title={t('documents.error.title')}
          message={normalizeError(documentsQuery.error).message}
          onRetry={() => documentsQuery.refetch()}
          retryLabel={t('common.retry')}
        />
      ) : items.length === 0 ? (
        <EmptyState
          icon={<Icon name="documents" className="size-7" />}
          title={t('documents.empty.title')}
          description={t('documents.empty.subtitle')}
          action={
            <Button variant="outline" onClick={() => setUploadOpen(true)}>
              {t('documents.upload.title')}
            </Button>
          }
        />
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5">
          {items.map((item) => (
            <li key={item.id} className="group flex flex-col gap-2">
              <button
                type="button"
                onClick={() => open(item)}
                className="focus-ring rounded-card text-left"
              >
                <DocumentThumb document={item} />
              </button>
              <div className="flex flex-col gap-1 px-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="truncate text-sm font-medium text-ink">{item.title}</p>
                  <div className="flex shrink-0 items-center">
                    {item.batchId && item.batchCode && (
                      <button
                        type="button"
                        onClick={() => {
                          setBatchId(item.batchId)
                          setBatchCode(item.batchCode)
                        }}
                        className="focus-ring rounded-pill px-2 py-0.5 text-[11px] text-brand-600 transition-colors hover:bg-brand-50"
                        title={item.batchCode}
                      >
                        {item.batchCode}
                      </button>
                    )}
                  </div>
                </div>
                <p className="truncate text-[11px] text-ink-soft">
                  {item.fileName} · {formatBytes(item.fileSize)} · {formatDate(item.createdAt, language)}
                </p>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] text-ink-soft">{t(`documents.type.${item.type}`)}</span>
                  <div className="flex items-center gap-0.5">
                    <button
                      type="button"
                      onClick={() => downloadDocument(item)}
                      className="focus-ring rounded-pill p-1.5 text-ink-soft transition-colors hover:bg-surface-3"
                      title={t('documents.download')}
                    >
                      <Icon name="download" className="size-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setEditing(item)
                        setEditOpen(true)
                      }}
                      className="focus-ring rounded-pill p-1.5 text-ink-soft transition-colors hover:bg-surface-3"
                      title={t('documents.edit.title')}
                    >
                      <Icon name="pencil" className="size-3.5" />
                    </button>
                    {isOwner && (
                      <button
                        type="button"
                        onClick={() => setToDelete(item)}
                        className="focus-ring rounded-pill p-1.5 text-ink-soft transition-colors hover:bg-crimson-50 hover:text-crimson-600"
                        title={t('common.delete')}
                      >
                        <Icon name="trash" className="size-3.5" />
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      <UploadDrawer open={uploadOpen} onClose={() => setUploadOpen(false)} onUploaded={handleUploaded} />
      <EditDrawer open={editOpen} onClose={() => setEditOpen(false)} document={editing} onSaved={handleSaved} />
      <BatchPanel
        batchId={batchId}
        batchCode={batchCode}
        onClose={() => {
          setBatchId(null)
          setBatchCode(null)
        }}
      />

      {index !== null && (
        <DocumentLightbox
          documents={items}
          index={index}
          onClose={close}
          onIndexChange={setIndex}
        />
      )}

      <ConfirmDialog
        isOpen={toDelete !== null}
        onClose={() => setToDelete(null)}
        title={t('documents.delete.title')}
        message={toDelete ? t('documents.delete.body', { name: toDelete.title }) : ''}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        confirmVariant="danger"
        loading={deleteMutation.isPending}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
      />
    </div>
  )
}
