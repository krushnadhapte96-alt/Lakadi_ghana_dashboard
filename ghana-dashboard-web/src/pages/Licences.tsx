import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchLicenceSummary, fetchLicences } from '@/api/licences'
import type { Licence, LicenceStatus, LicenceType } from '@/api/licences'
import { DocumentPreview } from '@/components/licences/DocumentPreview'
import { HistorySheet } from '@/components/licences/HistorySheet'
import { LicenceCard } from '@/components/licences/LicenceCard'
import { LicenceFormSheet } from '@/components/licences/LicenceFormSheet'
import { RenewalSheet } from '@/components/licences/RenewalSheet'
import { useLicenceMutations } from '@/components/licences/useLicenceMutations'
import { byUrgency } from '@/components/licences/helpers'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  PageHeader,
  Skeleton,
  StatCard,
  Tabs,
} from '@/components/ui'
import type { TabItem } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { readEnumParam, withoutParam } from '@/utils/entryPoint'
import { formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'

type Filter = LicenceStatus | 'ALL'

const FILTERS: readonly Filter[] = ['ALL', 'ACTIVE', 'EXPIRING_SOON', 'EXPIRED']

export function LicencesPage() {
  const { t, language } = useLanguage()
  const { remove } = useLicenceMutations()
  const [searchParams, setSearchParams] = useSearchParams()

  // The dashboard's licence card links straight to the tab that needs attention.
  const [filter, setFilter] = useState<Filter>(
    () => readEnumParam(searchParams, 'status', FILTERS) ?? 'ALL',
  )
  const [typeFilter, setTypeFilter] = useState<LicenceType | ''>('')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Licence | null>(null)
  const [renewing, setRenewing] = useState<Licence | null>(null)
  const [historyFor, setHistoryFor] = useState<Licence | null>(null)
  const [previewFor, setPreviewFor] = useState<Licence | null>(null)
  const [deleting, setDeleting] = useState<Licence | null>(null)

  const licences = useQuery({ queryKey: ['licences', typeFilter], queryFn: () => fetchLicences({ type: typeFilter }) })
  const summary = useQuery({ queryKey: ['licences', 'summary'], queryFn: fetchLicenceSummary })

  const all = useMemo(() => [...(licences.data ?? [])].sort(byUrgency), [licences.data])

  const tabs = useMemo<TabItem[]>(() => {
    const count = (status: LicenceStatus) => all.filter((l) => l.status === status).length
    return [
      { id: 'ALL', label: t('licences.tabs.all'), count: all.length },
      { id: 'ACTIVE', label: t('licences.tabs.active'), count: count('ACTIVE') },
      { id: 'EXPIRING_SOON', label: t('licences.tabs.expiringSoon'), count: count('EXPIRING_SOON') },
      { id: 'EXPIRED', label: t('licences.tabs.expired'), count: count('EXPIRED') },
    ]
  }, [all, t])

  const visible = useMemo(
    () => (filter === 'ALL' ? all : all.filter((licence) => licence.status === filter)),
    [all, filter],
  )

  const openAdd = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openEdit = (licence: Licence) => {
    setEditing(licence)
    setFormOpen(true)
  }

  const confirmDelete = () => {
    if (!deleting) return
    remove
      .mutateAsync(deleting.id)
      .then(() => {
        toast.success(t('licences.deleted'))
        setDeleting(null)
      })
      .catch((error) => toast.error(normalizeError(error).message || t('licences.deleteFailed')))
  }

  return (
    <div>
      <PageHeader
        title={t('licences.title')}
        subtitle={t('licences.subtitle')}
        actions={
          <Button onClick={openAdd}>
            <Icon name="plus" className="size-4" />
            {t('licences.add')}
          </Button>
        }
      />

      {licences.isError ? (
        <Card>
          <ErrorState
            icon={<Icon name="alert-circle" className="size-7" />}
            title={t('licences.error.title')}
            message={t('licences.error.body')}
            retryLabel={t('common.retry')}
            onRetry={() => void licences.refetch()}
            className="p-6"
          />
        </Card>
      ) : (
        <>
          <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
            <StatCard
              icon={<Icon name="licences" className="size-5" />}
              label={t('licences.stat.total')}
              value={summary.data?.total ?? all.length}
            />
            <StatCard
              icon={<Icon name="check" className="size-5 text-brand-500" />}
              label={t('licences.stat.active')}
              value={summary.data?.active ?? 0}
            />
            <StatCard
              icon={<Icon name="clock" className="size-5 text-gold-500" />}
              label={t('licences.stat.expiringSoon')}
              value={summary.data?.expiringSoon ?? 0}
            />
            <StatCard
              icon={<Icon name="alert-circle" className="size-5 text-clay-500" />}
              label={t('licences.stat.expired')}
              value={summary.data?.expired ?? 0}
            />
          </div>

          {summary.data && (summary.data.fssaiNumber || summary.data.gstin || summary.data.missingTypes.length > 0) && (
            <Card className="mb-6 flex flex-wrap items-center gap-3 p-4 text-sm">
              {summary.data.fssaiNumber && (
                <Badge variant="primary" size="md" dot>
                  {t('licences.bill.fssai')} {summary.data.fssaiNumber}
                </Badge>
              )}
              {summary.data.gstin && (
                <Badge variant="primary" size="md" dot>
                  {t('licences.bill.gstin')} {summary.data.gstin}
                </Badge>
              )}
              {summary.data.missingTypes.map((missing) => (
                <Badge key={missing} variant="clay" size="md">
                  {t('licences.bill.missing', { type: t(`licences.type.${missing}`) })}
                </Badge>
              ))}
              {summary.data.nextExpiry && (
                <span className="ml-auto text-xs text-ink-soft">
                  {t('licences.bill.nextExpiry', { date: formatDate(summary.data.nextExpiry, language) })}
                </span>
              )}
            </Card>
          )}

          <div className="mb-6 flex flex-wrap items-center gap-3">
            <Tabs
              tabs={tabs}
              activeTab={filter}
              onTabChange={(id) => {
                setFilter(id as Filter)
                setSearchParams(withoutParam(searchParams, 'status'), { replace: true })
              }}
              className="min-w-0 flex-1"
            />
            <SelectFilter value={typeFilter} onChange={setTypeFilter} />
          </div>

          {licences.isPending ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {Array.from({ length: 3 }, (_, i) => (
                <Skeleton key={i} className="h-72" />
              ))}
            </div>
          ) : visible.length === 0 ? (
            <Card>
              <EmptyState
                icon={<Icon name="licences" className="size-7" />}
                title={all.length === 0 ? t('licences.empty.title') : t('licences.empty.filteredTitle')}
                description={all.length === 0 ? t('licences.empty.body') : t('licences.empty.filteredBody')}
                action={
                  all.length === 0 ? (
                    <Button onClick={openAdd}>{t('licences.add')}</Button>
                  ) : undefined
                }
                className="p-6"
              />
            </Card>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {visible.map((licence) => (
                <LicenceCard
                  key={licence.id}
                  licence={licence}
                  onRenew={setRenewing}
                  onHistory={setHistoryFor}
                  onPreview={setPreviewFor}
                  onEdit={openEdit}
                  onDelete={setDeleting}
                />
              ))}
            </div>
          )}
        </>
      )}

      <LicenceFormSheet open={formOpen} onClose={() => setFormOpen(false)} licence={editing} />
      <RenewalSheet open={Boolean(renewing)} onClose={() => setRenewing(null)} licence={renewing} />
      <HistorySheet open={Boolean(historyFor)} onClose={() => setHistoryFor(null)} licence={historyFor} />
      <DocumentPreview open={Boolean(previewFor)} onClose={() => setPreviewFor(null)} licence={previewFor} />

      <ConfirmDialog
        isOpen={Boolean(deleting)}
        onClose={() => setDeleting(null)}
        onConfirm={confirmDelete}
        title={t('licences.delete.title')}
        message={t('licences.delete.message', { number: deleting?.licenceNumber ?? '' })}
        confirmLabel={t('common.delete')}
        confirmVariant="danger"
        loading={remove.isPending}
      />
    </div>
  )
}

/** Type dropdown; kept inline because only this page needs the licence list. */
function SelectFilter({
  value,
  onChange,
}: {
  value: LicenceType | ''
  onChange: (value: LicenceType | '') => void
}) {
  const { t } = useLanguage()
  return (
    <select
      value={value}
      onChange={(event) => onChange(event.target.value as LicenceType | '')}
      aria-label={t('licences.filters.type')}
      className="focus-ring h-10 rounded-pill border border-line bg-surface-2 px-3 text-sm text-ink"
    >
      <option value="">{t('licences.filters.typeAll')}</option>
      {(['FSSAI', 'GST', 'SHOP_ESTABLISHMENT', 'TRADE', 'WEIGHING_SCALE_STAMPING', 'FIRE_NOC', 'OTHER'] as const).map(
        (type) => (
          <option key={type} value={type}>
            {t(`licences.type.${type}`)}
          </option>
        ),
      )}
    </select>
  )
}
