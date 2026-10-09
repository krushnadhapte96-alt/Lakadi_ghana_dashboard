import { useQuery } from '@tanstack/react-query'
import { downloadRenewalDocument, fetchRenewals } from '@/api/licences'
import type { Licence } from '@/api/licences'
import { Badge, Button, Card, EmptyState, ErrorState, Sheet, Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'

interface HistorySheetProps {
  open: boolean
  onClose: () => void
  licence: Licence | null
}

/**
 * Every renewal the licence has been through. Records are appended on renew, so
 * this is the full chain rather than just the current document.
 */
export function HistorySheet({ open, onClose, licence }: HistorySheetProps) {
  const { t, language } = useLanguage()

  const history = useQuery({
    queryKey: ['licences', licence?.id ?? 0, 'renewals'],
    queryFn: () => fetchRenewals(licence?.id ?? 0),
    enabled: open && Boolean(licence),
  })

  const download = async (renewalId: number, fileName: string | null) => {
    if (!licence) return
    try {
      await downloadRenewalDocument(licence.id, renewalId, fileName ?? 'renewal.pdf')
    } catch (error) {
      toast.error(normalizeError(error).message || t('licences.history.downloadFailed'))
    }
  }

  const renewals = history.data ?? []

  return (
    <Sheet open={open} onClose={onClose} title={t('licences.history.title')} subtitle={licence?.licenceNumber}>
      <div className="p-5">
        {history.isError ? (
          <ErrorState
            icon={<Icon name="alert-circle" className="size-7" />}
            title={t('licences.history.error')}
            retryLabel={t('common.retry')}
            onRetry={() => void history.refetch()}
            className="p-6"
          />
        ) : history.isPending ? (
          <div className="space-y-3">
            <Skeleton className="h-20" />
            <Skeleton className="h-20" />
          </div>
        ) : renewals.length === 0 ? (
          <EmptyState
            icon={<Icon name="clock" className="size-7" />}
            title={t('licences.history.emptyTitle')}
            description={t('licences.history.emptyBody')}
            className="p-6"
          />
        ) : (
          <ol className="space-y-3">
            {renewals.map((renewal, index) => (
              <li key={renewal.id}>
                <Card className="space-y-2 p-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-ink">
                        {renewal.newNumber ?? licence?.licenceNumber}
                      </p>
                      <p className="text-xs text-ink-soft">
                        {t('licences.history.valid')} {formatDate(renewal.issueDate, language)} -{' '}
                        {formatDate(renewal.expiryDate, language)}
                      </p>
                    </div>
                    {index === 0 && (
                      <Badge variant="primary" size="sm">
                        {t('licences.history.current')}
                      </Badge>
                    )}
                  </div>

                  <p className="text-xs text-ink-soft">
                    {t('licences.history.was')}{' '}
                    <span className="tabular-nums">
                      {renewal.previousNumber ?? '-'} ·{' '}
                      {formatDate(renewal.previousExpiryDate, language)}
                    </span>
                  </p>

                  {renewal.notes && (
                    <p className="whitespace-pre-line text-xs text-ink-soft">{renewal.notes}</p>
                  )}

                  <div className="flex flex-wrap items-center gap-2 pt-1">
                    {renewal.renewedBy && (
                      <span className="text-[11px] text-ink-soft">
                        {t('licences.history.by', { name: renewal.renewedBy })}
                      </span>
                    )}
                    {renewal.hasDocument && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => void download(renewal.id, renewal.documentName)}
                        className="ml-auto"
                      >
                        <Icon name="download" className="size-4" />
                        {t('licences.history.download')}
                      </Button>
                    )}
                  </div>
                </Card>
              </li>
            ))}
          </ol>
        )}
      </div>
    </Sheet>
  )
}
