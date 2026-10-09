import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import {
  MAX_PHOTOS,
  evictReturnPhoto,
  fetchReturn,
  reviewReturn,
  uploadReturnPhotos,
} from '@/api/returns'
import type { ReturnDetail } from '@/api/returns'
import { PhotoStrip } from '@/components/returns/PhotoStrip'
import { ActionBadge, ResolutionBadge, StatusBadge, TypeChip } from '@/components/returns/TypeChip'
import { PHOTO_ACCEPT, validPhotoFiles } from '@/components/returns/helpers'
import { Button, ErrorState, Sheet, Skeleton, Textarea } from '@/components/ui'
import { useIsOwner } from '@/auth/useCan'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatDate, formatQty } from '@/utils/format'
import { Icon } from '@/utils/icons'

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 py-1.5">
      <dt className="shrink-0 text-xs text-ink-soft">{label}</dt>
      <dd className="min-w-0 text-right text-sm font-medium text-ink">{value}</dd>
    </div>
  )
}

export function ReturnDetailDrawer({
  returnId,
  onClose,
  onResolve,
}: {
  returnId: number | null
  onClose: () => void
  onResolve: (id: number) => void
}) {
  const { t, language } = useLanguage()
  const queryClient = useQueryClient()
  const isOwner = useIsOwner()
  const fileRef = useRef<HTMLInputElement>(null)
  const [note, setNote] = useState('')

  const detail = useQuery({
    queryKey: ['returns', 'detail', returnId],
    queryFn: () => fetchReturn(returnId as number),
    enabled: returnId !== null,
  })

  function invalidate() {
    return Promise.all(
      [['returns'], ['inventory'], ['pnl'], ['payments'], ['cash-book']].map((queryKey) =>
        queryClient.invalidateQueries({ queryKey }),
      ),
    )
  }

  const review = useMutation({
    mutationFn: () => reviewReturn(returnId as number, note),
    onSuccess: async () => {
      toast.success(t('returns.review.success'))
      setNote('')
      await invalidate()
    },
    onError: (error) => toast.error(normalizeError(error).message || t('returns.review.failed')),
  })

  const upload = useMutation({
    mutationFn: async (files: File[]) => {
      const added = await uploadReturnPhotos(returnId as number, files)
      for (const photo of added) evictReturnPhoto(returnId as number, photo.id)
      return added
    },
    onSuccess: async () => {
      toast.success(t('returns.photosAdded'))
      await invalidate()
    },
    onError: (error) => toast.error(normalizeError(error).message || t('returns.photosFailed')),
  })

  const data: ReturnDetail | undefined = detail.data
  const canReview = data?.status === 'OPEN'
  const canResolve = data?.status === 'IN_REVIEW'
  const canAddPhotos = Boolean(data) && data!.status !== 'RESOLVED'
  const slotsLeft = data ? MAX_PHOTOS - data.photos.length : 0
  const busy = review.isPending || upload.isPending

  return (
    <Sheet
      open={returnId !== null}
      onClose={onClose}
      title={data?.returnNo ?? t('returns.detail.title')}
      subtitle={data ? t(`returns.source.${data.sourceType}`) : undefined}
      footer={
        data ? (
          <>
            {canReview && (
              <Button
                onClick={() => review.mutate()}
                disabled={busy}
                className="sm:flex-1"
              >
                <Icon name="eye" className="size-4" />
                {review.isPending ? t('returns.review.working') : t('returns.actions.review')}
              </Button>
            )}
            {canResolve && (
              isOwner ? (
                <Button onClick={() => onResolve(data.id)} className="sm:flex-1">
                  <Icon name="check" className="size-4" />
                  {t('returns.actions.resolve')}
                </Button>
              ) : (
                <p className="text-xs text-ink-soft">{t('returns.resolve.ownerOnly')}</p>
              )
            )}
            <Button variant="secondary" onClick={onClose} disabled={busy}>
              {t('common.close')}
            </Button>
          </>
        ) : null
      }
    >
      {detail.isError ? (
        <ErrorState
          title={t('returns.error.title')}
          message={t('returns.error.body')}
          retryLabel={t('common.retry')}
          onRetry={() => void detail.refetch()}
        />
      ) : !data ? (
        <div className="space-y-3 p-5">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-24" />
          <Skeleton className="h-32" />
        </div>
      ) : (
        <div className="space-y-5 px-5 py-5">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={data.status} />
            <TypeChip type={data.type} />
            {data.resolution && <ResolutionBadge resolution={data.resolution} />}
          </div>

          <dl className="divide-y divide-line rounded-card border border-line bg-surface px-3 py-1">
            <DetailRow
              label={t('returns.card.billLabel')}
              value={data.sourceBillNo}
            />
            <DetailRow
              label={t('returns.detail.customer')}
              value={data.customerName ?? t('returns.detail.walkIn')}
            />
            <DetailRow
              label={t('returns.detail.reportedBy')}
              value={`${data.reportedByName} · ${formatDate(data.createdAt, language)}`}
            />
            {data.resolvedAt && (
              <DetailRow
                label={t('returns.detail.resolvedBy')}
                value={`${data.resolvedByName ?? '-'} · ${formatDate(data.resolvedAt, language)}`}
              />
            )}
            {data.refundAmount != null && (
              <DetailRow
                label={t('returns.detail.refund')}
                value={formatCurrency(data.refundAmount, { decimals: 2 })}
              />
            )}
            {data.replacementSaleId != null && (
              <DetailRow
                label={t('returns.detail.replacement')}
                value={t('returns.detail.saleRef', { id: data.replacementSaleId })}
              />
            )}
          </dl>

          {data.description && (
            <div>
              <h3 className="mb-1.5 text-sm font-medium text-ink">{t('returns.form.description')}</h3>
              <p className="whitespace-pre-wrap rounded-card bg-surface-3 px-3 py-2 text-sm text-ink-soft">
                {data.description}
              </p>
            </div>
          )}

          <div>
            <h3 className="mb-2 text-sm font-medium text-ink">
              {t('returns.form.items')} ({data.items.length})
            </h3>
            <ul className="divide-y divide-line rounded-card border border-line">
              {data.items.map((item) => (
                <li key={item.id} className="flex items-center gap-3 px-3 py-2.5">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-ink">{item.productName}</p>
                    <p className="text-xs text-ink-soft">
                      {formatQty(item.qty)}  ×  {formatCurrency(item.unitPrice, { decimals: 2 })}
                      {item.batchId ? ` · ${item.batchId}` : ''}
                    </p>
                  </div>
                  <p className="shrink-0 text-sm font-semibold tabular-nums text-ink">
                    {formatCurrency(item.qty * item.unitPrice, { decimals: 2 })}
                  </p>
                  {item.action && <ActionBadge action={item.action} />}
                </li>
              ))}
            </ul>
            <p className="mt-2 flex items-center justify-between text-sm">
              <span className="text-ink-soft">{t('returns.card.value')}</span>
              <span className="font-bold tabular-nums text-ink">
                {formatCurrency(
                  data.items.reduce((sum, item) => sum + item.qty * item.unitPrice, 0),
                  { decimals: 2 },
                )}
              </span>
            </p>
          </div>

          <div>
            <h3 className="mb-2 text-sm font-medium text-ink">
              {t('returns.form.photos')} ({data.photos.length})
            </h3>
            <PhotoStrip returnId={data.id} photos={data.photos} max={6} />
            {canAddPhotos && (
              <>
                <input
                  ref={fileRef}
                  type="file"
                  accept={PHOTO_ACCEPT}
                  multiple
                  className="sr-only"
                  tabIndex={-1}
                  onChange={(event) => {
                    // Never send more than the remaining slots or the API rejects
                    // the whole batch, so cap the pick before uploading.
                    const { accepted, rejected } = validPhotoFiles([...(event.target.files ?? [])])
                    const batch = accepted.slice(0, slotsLeft)
                    if (rejected > 0) toast.error(t('returns.form.photosRejected', { count: rejected }))
                    if (batch.length > 0) upload.mutate(batch)
                    event.target.value = ''
                  }}
                />
                <Button
                  size="sm"
                  variant="secondary"
                  className="mt-3"
                  disabled={slotsLeft <= 0 || upload.isPending}
                  onClick={() => fileRef.current?.click()}
                >
                  <Icon name="upload" className="size-4" />
                  {slotsLeft <= 0
                    ? t('returns.form.photosFull')
                    : upload.isPending
                      ? t('returns.form.photosUploading')
                      : t('returns.actions.addPhotos')}
                </Button>
              </>
            )}
          </div>

          {canReview && (
            <div className="rounded-card border border-line bg-surface p-3">
              <Textarea
                label={t('returns.review.note')}
                placeholder={t('returns.review.notePlaceholder')}
                rows={2}
                value={note}
                onChange={(event) => setNote(event.target.value)}
              />
              <p className="mt-2 flex items-start gap-1.5 text-xs text-ink-soft">
                <Icon name="alert-circle" className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                {t('returns.review.hint')}
              </p>
            </div>
          )}
        </div>
      )}
    </Sheet>
  )
}

