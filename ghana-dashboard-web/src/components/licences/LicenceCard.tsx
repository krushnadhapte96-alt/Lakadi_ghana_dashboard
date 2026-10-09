import { Badge, Button, Card } from '@/components/ui'
import { DaysLeftRing } from '@/components/licences/DaysLeftRing'
import { TONE_BADGE, toneFor } from '@/components/licences/helpers'
import type { Licence } from '@/api/licences'
import { useLanguage } from '@/i18n'
import { formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'
import { cn } from '@/utils/cn'

interface LicenceCardProps {
  licence: Licence
  onRenew: (licence: Licence) => void
  onHistory: (licence: Licence) => void
  onPreview: (licence: Licence) => void
  onEdit: (licence: Licence) => void
  onDelete: (licence: Licence) => void
}

export function LicenceCard({
  licence,
  onRenew,
  onHistory,
  onPreview,
  onEdit,
  onDelete,
}: LicenceCardProps) {
  const { t, language } = useLanguage()
  const tone = toneFor(licence.status)
  const lapsed = licence.status === 'EXPIRED'

  const statusLabel = lapsed
    ? t('licences.status.expiredDays', { count: Math.abs(licence.daysLeft) })
    : licence.status === 'EXPIRING_SOON'
      ? t('licences.status.expiringIn', { count: licence.daysLeft })
      : t('licences.status.active')

  return (
    <Card
      className={cn(
        'space-y-4 p-4 transition-shadow hover:shadow-card',
        lapsed && 'ring-1 ring-clay-400',
        licence.status === 'EXPIRING_SOON' && 'ring-1 ring-gold-300',
      )}
    >
      <div className="flex items-start gap-3">
        <DaysLeftRing licence={licence} />

        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <p className="truncate font-medium text-ink">{licence.licenceNumber}</p>
              <p className="truncate text-xs text-ink-soft">
                {t(`licences.type.${licence.type}`)}
              </p>
            </div>
            <Badge variant={TONE_BADGE[tone]} size="sm" dot className="shrink-0">
              {statusLabel}
            </Badge>
          </div>

          {licence.issuer && (
            <p className="truncate text-xs text-ink-soft">{licence.issuer}</p>
          )}

          {licence.fssaiGstin && (
            <p className="truncate font-mono text-[11px] text-ink-soft">{licence.fssaiGstin}</p>
          )}
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-2 text-sm">
        <div>
          <dt className="text-xs text-ink-soft">{t('licences.card.issued')}</dt>
          <dd className="font-medium tabular-nums text-ink">
            {formatDate(licence.issueDate, language)}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-ink-soft">{t('licences.card.expires')}</dt>
          <dd className="font-medium tabular-nums text-ink">
            {formatDate(licence.expiryDate, language)}
          </dd>
        </div>
      </dl>

      <div className="flex flex-wrap items-center gap-2">
        {licence.hasDocument ? (
          <Button size="sm" variant="ghost" onClick={() => onPreview(licence)}>
            <Icon name="eye" className="size-4" />
            {t('licences.card.view')}
          </Button>
        ) : (
          <span className="text-xs text-ink-soft">{t('licences.card.noDocument')}</span>
        )}
        {licence.renewalCount > 0 && (
          <Button size="sm" variant="ghost" onClick={() => onHistory(licence)}>
            <Icon name="clock" className="size-4" />
            {t('licences.card.historyCount', { count: licence.renewalCount })}
          </Button>
        )}
      </div>

      <div className="flex flex-wrap gap-2 border-t border-line pt-3">
        <Button size="sm" onClick={() => onRenew(licence)}>
          <Icon name="calendar" className="size-4" />
          {t('licences.actions.renew')}
        </Button>
        <Button size="sm" variant="secondary" onClick={() => onEdit(licence)}>
          {t('licences.actions.edit')}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          onClick={() => onDelete(licence)}
          className="ml-auto text-crimson-600 hover:bg-crimson-50"
          aria-label={t('licences.actions.delete')}
        >
          <Icon name="trash" className="size-4" />
        </Button>
      </div>
    </Card>
  )
}
