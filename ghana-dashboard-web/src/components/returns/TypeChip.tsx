import { Badge } from '@/components/ui'
import { RESOLUTION_VARIANT, STATUS_VARIANT, TYPE_COLOUR } from '@/components/returns/helpers'
import type { ReturnItemAction, ReturnResolution, ReturnStatus, ReturnType } from '@/api/returns'
import { useLanguage } from '@/i18n'

export function TypeChip({ type, size = 'md' }: { type: ReturnType; size?: 'sm' | 'md' | 'lg' }) {
  const { t } = useLanguage()
  return (
    <Badge size={size} className="bg-surface-3 text-ink">
      <span
        className="size-2.5 rounded-full ring-1 ring-white/70 dark:ring-black/30"
        style={{ backgroundColor: TYPE_COLOUR[type] }}
        aria-hidden="true"
      />
      {t(`returns.type.${type}`)}
    </Badge>
  )
}

export function StatusBadge({ status }: { status: ReturnStatus }) {
  const { t } = useLanguage()
  return <Badge variant={STATUS_VARIANT[status]}>{t(`returns.status.${status}`)}</Badge>
}

export function ResolutionBadge({ resolution }: { resolution: ReturnResolution }) {
  const { t } = useLanguage()
  return (
    <Badge variant={RESOLUTION_VARIANT[resolution]}>{t(`returns.resolution.${resolution}`)}</Badge>
  )
}

export function ActionBadge({ action }: { action: ReturnItemAction }) {
  const { t } = useLanguage()
  return (
    <Badge
      variant={action === 'RESTOCK' ? 'success' : 'danger'}
      size="sm"
    >
      {t(`returns.action.${action}`)}
    </Badge>
  )
}

