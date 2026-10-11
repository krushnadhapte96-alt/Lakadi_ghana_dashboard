import type { ReactNode } from 'react'
import { Button, Card } from '@/components/ui'
import { cn } from '@/utils/cn'

interface SectionProps {
  title: string
  description?: string
  actions?: ReactNode
  /** Rendered under the header, e.g. a short explanation of a risky field. */
  note?: ReactNode
  children: ReactNode
  className?: string
}

/** The standard settings block: a titled card with an optional action slot. */
export function Section({ title, description, actions, note, children, className }: SectionProps) {
  return (
    <Card tilt={false} className={cn('p-4 sm:p-5', className)}>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-base font-semibold text-ink">{title}</h2>
          {description && <p className="mt-1 text-sm text-ink-soft">{description}</p>}
        </div>
        {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
      </div>
      {note}
      <div className={cn(note && 'mt-4')}>{children}</div>
    </Card>
  )
}

interface SaveBarProps {
  dirty: boolean
  saving: boolean
  onSave: () => void
  onReset: () => void
  saveLabel: string
  resetLabel: string
  savingLabel: string
}

/**
 * Sticky footer for a tab's save action. Disabled until something actually changes so
 * a stray click cannot rewrite settings that were never edited.
 */
export function SaveBar({
  dirty,
  saving,
  onSave,
  onReset,
  saveLabel,
  resetLabel,
  savingLabel,
}: SaveBarProps) {
  return (
    <div className="mt-5 flex flex-wrap items-center justify-end gap-3 border-t border-line pt-4">
      <Button variant="ghost" onClick={onReset} disabled={!dirty || saving}>
        {resetLabel}
      </Button>
      <Button onClick={onSave} disabled={!dirty || saving}>
        {saving ? savingLabel : saveLabel}
      </Button>
    </div>
  )
}

