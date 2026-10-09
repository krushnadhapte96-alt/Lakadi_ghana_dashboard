import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchOilTypes } from '@/api/settings'
import type { OilType, OilTypeInput } from '@/api/settings'
import { SaveBar, Section } from '@/components/settings/Section'
import { useSettingsMutations } from '@/components/settings/useSettingsMutations'
import {
  Badge,
  Button,
  ConfirmDialog,
  ErrorState,
  Input,
  Modal,
  Select,
  Skeleton,
} from '@/components/ui'
import { useLanguage } from '@/i18n'
import { Icon } from '@/utils/icons'

/** Matches the colour_tag values seeded by V3 and the tag chips used elsewhere. */
const COLOUR_TAGS = ['mustard', 'gold', 'red', 'white', 'green', 'brown'] as const

const BLANK: OilTypeInput = {
  nameEn: '',
  nameHi: '',
  density: 0.91,
  bestBeforeDays: 365,
  colourTag: 'mustard',
}

type Sheet = { kind: 'create' } | { kind: 'edit'; oil: OilType } | null

export function OilTypesTab() {
  const { t } = useLanguage()
  // Settings needs the deactivated rows too, so an oil type can be brought back.
  const oilTypes = useQuery({
    queryKey: ['settings', 'oil-types'],
    queryFn: () => fetchOilTypes(true),
  })
  const { addOilType, editOilType, retireOilType } = useSettingsMutations()
  const [sheet, setSheet] = useState<Sheet>(null)
  const [confirm, setConfirm] = useState<OilType | null>(null)

  if (oilTypes.isPending) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    )
  }

  if (oilTypes.isError) {
    return (
      <ErrorState
        title={t('settings.oilTypes.loadFailed')}
        message={t('settings.oilTypes.loadFailedBody')}
        retryLabel={t('common.retry')}
        onRetry={() => void oilTypes.refetch()}
      />
    )
  }

  const rows = oilTypes.data ?? []

  return (
    <Section
      title={t('settings.oilTypes.title')}
      description={t('settings.oilTypes.description')}
      actions={
        <Button onClick={() => setSheet({ kind: 'create' })}>
          <Icon name="plus" className="size-4" />
          {t('settings.oilTypes.add')}
        </Button>
      }
      note={
        <p className="rounded-card border border-line bg-surface-3 px-3 py-2 text-xs text-ink-soft">
          {t('settings.oilTypes.sharedNote')}
        </p>
      }
    >
      {rows.length === 0 ? (
        <p className="py-6 text-center text-sm text-ink-soft">{t('settings.oilTypes.empty')}</p>
      ) : (
        <div className="-mx-4 overflow-x-auto sm:-mx-5">
          <table className="w-full min-w-[34rem] border-collapse text-sm">
            <thead>
              <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-soft">
                <th className="px-4 py-2 font-medium sm:px-5">{t('settings.oilTypes.name')}</th>
                <th className="px-4 py-2 font-medium">{t('settings.oilTypes.density')}</th>
                <th className="px-4 py-2 font-medium">{t('settings.oilTypes.bestBefore')}</th>
                <th className="px-4 py-2 font-medium">{t('settings.oilTypes.colour')}</th>
                <th className="px-4 py-2 text-right font-medium sm:px-5">
                  {t('settings.oilTypes.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((oil) => (
                <tr key={oil.id} className="border-b border-line/60 last:border-0">
                  <td className="px-4 py-2.5 sm:px-5">
                    <span className="font-medium text-ink">{oil.nameEn}</span>
                    {oil.nameHi && <span className="ml-2 text-ink-soft">{oil.nameHi}</span>}
                    {!oil.active && (
                      <Badge variant="secondary" className="ml-2">
                        {t('settings.oilTypes.inactive')}
                      </Badge>
                    )}
                  </td>
                  <td className="px-4 py-2.5 tabular-nums text-ink-soft">{oil.density.toFixed(2)}</td>
                  <td className="px-4 py-2.5 tabular-nums text-ink-soft">
                    {t('settings.oilTypes.days', { count: oil.bestBeforeDays })}
                  </td>
                  <td className="px-4 py-2.5 text-ink-soft">{oil.colourTag}</td>
                  <td className="px-4 py-2.5 sm:px-5">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setSheet({ kind: 'edit', oil })}
                      >
                        <Icon name="pencil" className="size-4" />
                        {t('common.edit')}
                      </Button>
                      {oil.active ? (
                        <Button variant="ghost" size="sm" onClick={() => setConfirm(oil)}>
                          <Icon name="trash" className="size-4" />
                          {t('settings.oilTypes.deactivate')}
                        </Button>
                      ) : (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() =>
                            editOilType.mutate({
                              id: oil.id,
                              input: {
                                nameEn: oil.nameEn,
                                nameHi: oil.nameHi,
                                density: oil.density,
                                bestBeforeDays: oil.bestBeforeDays,
                                colourTag: oil.colourTag,
                                active: true,
                              },
                            })
                          }
                        >
                          <Icon name="check" className="size-4" />
                          {t('settings.oilTypes.reactivate')}
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {sheet && (
        <OilTypeSheet
          oil={sheet.kind === 'edit' ? sheet.oil : null}
          saving={sheet.kind === 'create' ? addOilType.isPending : editOilType.isPending}
          onClose={() => setSheet(null)}
          onSubmit={(input) => {
            if (sheet.kind === 'create') {
              addOilType.mutate(input, { onSuccess: () => setSheet(null) })
            } else {
              editOilType.mutate({ id: sheet.oil.id, input }, { onSuccess: () => setSheet(null) })
            }
          }}
        />
      )}

      <ConfirmDialog
        isOpen={confirm !== null}
        onClose={() => setConfirm(null)}
        onConfirm={() => {
          if (confirm) retireOilType.mutate(confirm.id)
          setConfirm(null)
        }}
        title={t('settings.oilTypes.deactivateTitle')}
        message={t('settings.oilTypes.deactivateMessage', { name: confirm?.nameEn ?? '' })}
        confirmLabel={t('settings.oilTypes.deactivate')}
        confirmVariant="danger"
        loading={retireOilType.isPending}
      />
    </Section>
  )
}

function OilTypeSheet({
  oil,
  saving,
  onClose,
  onSubmit,
}: {
  oil: OilType | null
  saving: boolean
  onClose: () => void
  onSubmit: (input: OilTypeInput) => void
}) {
  const { t } = useLanguage()
  // Seeded from props; the caller remounts this per oil type via the `key` on <Modal>.
  const pristine: OilTypeInput = oil
    ? {
        nameEn: oil.nameEn,
        nameHi: oil.nameHi,
        density: oil.density,
        bestBeforeDays: oil.bestBeforeDays,
        colourTag: oil.colourTag,
        active: oil.active,
      }
    : BLANK
  const [form, setForm] = useState<OilTypeInput>(pristine)
  const [error, setError] = useState<string | null>(null)

  const set = <K extends keyof OilTypeInput>(key: K, value: OilTypeInput[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const dirty = JSON.stringify(form) !== JSON.stringify(pristine)

  const submit = () => {
    if (!form.nameEn.trim()) return setError(t('settings.oilTypes.errors.nameEn'))
    if (!form.nameHi.trim()) return setError(t('settings.oilTypes.errors.nameHi'))
    if (form.bestBeforeDays < 1) return setError(t('settings.oilTypes.errors.bestBefore'))
    setError(null)
    onSubmit(form)
  }

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={oil ? t('settings.oilTypes.editTitle') : t('settings.oilTypes.addTitle')}
    >
      <div className="space-y-4">
        <Input
          label={t('settings.oilTypes.nameEn')}
          value={form.nameEn}
          onChange={(e) => set('nameEn', e.target.value)}
          placeholder={'\u0924\u0947\u0932 \u0915\u093e \u0928\u093e\u092e'}
        />
        <Input
          label={t('settings.oilTypes.nameHi')}
          value={form.nameHi}
          onChange={(e) => set('nameHi', e.target.value)}
          placeholder={'\u0924\u0947\u0932 \u0915\u093e \u0928\u093e\u092e'}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label={t('settings.oilTypes.density')}
            type="number"
            step="0.0001"
            min="0"
            value={String(form.density)}
            onChange={(e) => set('density', Number(e.target.value))}
            hint={t('settings.oilTypes.densityHint')}
          />
          <Input
            label={t('settings.oilTypes.bestBefore')}
            type="number"
            min="1"
            value={String(form.bestBeforeDays)}
            onChange={(e) => set('bestBeforeDays', Number(e.target.value))}
            hint={t('settings.oilTypes.bestBeforeHint')}
          />
        </div>
        <Select
          label={t('settings.oilTypes.colour')}
          value={form.colourTag}
          onChange={(value) => set('colourTag', value)}
          options={COLOUR_TAGS.map((tag) => ({ value: tag, label: tag }))}
        />
        {error && (
          <p role="alert" className="text-sm text-crimson-600">
            {error}
          </p>
        )}
        <SaveBar
          dirty={dirty}
          saving={saving}
          onSave={submit}
          onReset={() => setForm(pristine)}
          saveLabel={t('common.save')}
          resetLabel={t('common.reset')}
          savingLabel={t('common.saving')}
        />
      </div>
    </Modal>
  )
}
