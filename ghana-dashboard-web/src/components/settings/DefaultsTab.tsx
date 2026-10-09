import { useQuery } from '@tanstack/react-query'
import { fetchDefaults } from '@/api/settings'
import type { DefaultsSettings } from '@/api/settings'
import { SaveBar, Section } from '@/components/settings/Section'
import { useSyncedForm } from '@/components/settings/useSyncedForm'
import { useSettingsMutations } from '@/components/settings/useSettingsMutations'
import { ErrorState, Input, Skeleton, Switch } from '@/components/ui'
import { useLanguage } from '@/i18n'

const FALLBACK: DefaultsSettings = {
  defaultLowStockThreshold: 0,
  defaultBestBeforeDays: 365,
  creditSalesAllowed: true,
  defaultCreditPeriodDays: 30,
  defaultCreditLimit: 0,
}

export function DefaultsTab() {
  const { t } = useLanguage()
  const defaults = useQuery({ queryKey: ['settings', 'defaults'], queryFn: fetchDefaults })
  const { saveDefaults } = useSettingsMutations()

  const { form, setForm, dirty, reset } = useSyncedForm(defaults.data, FALLBACK)

  const set = <K extends keyof DefaultsSettings>(key: K, value: DefaultsSettings[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  if (defaults.isPending) {
    return <Skeleton className="h-96 w-full" />
  }

  if (defaults.isError) {
    return (
      <ErrorState
        title={t('settings.defaults.loadFailed')}
        message={t('settings.defaults.loadFailedBody')}
        retryLabel={t('common.retry')}
        onRetry={() => void defaults.refetch()}
      />
    )
  }

  return (
    <div className="space-y-4">
      <Section
        title={t('settings.defaults.stockTitle')}
        description={t('settings.defaults.stockDescription')}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label={t('settings.defaults.lowStock')}
            type="number"
            step="0.001"
            min="0"
            value={String(form.defaultLowStockThreshold)}
            onChange={(e) => set('defaultLowStockThreshold', Number(e.target.value))}
            hint={t('settings.defaults.lowStockHint')}
          />
          <Input
            label={t('settings.defaults.bestBefore')}
            type="number"
            min="1"
            value={String(form.defaultBestBeforeDays)}
            onChange={(e) => set('defaultBestBeforeDays', Number(e.target.value))}
            hint={t('settings.defaults.bestBeforeHint')}
          />
        </div>
        <SaveBar
          dirty={dirty}
          saving={saveDefaults.isPending}
          onSave={() => saveDefaults.mutate(form)}
          onReset={() => reset()}
          saveLabel={t('common.save')}
          resetLabel={t('common.reset')}
          savingLabel={t('common.saving')}
        />
      </Section>

      <Section
        title={t('settings.defaults.creditTitle')}
        description={t('settings.defaults.creditDescription')}
      >
        <div className="space-y-4">
          <Switch
            label={t('settings.defaults.creditAllowed')}
            hint={t('settings.defaults.creditAllowedHint')}
            checked={form.creditSalesAllowed}
            onChange={(e) => set('creditSalesAllowed', e.target.checked)}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              label={t('settings.defaults.creditDays')}
              type="number"
              min="0"
              max="365"
              disabled={!form.creditSalesAllowed}
              value={String(form.defaultCreditPeriodDays)}
              onChange={(e) => set('defaultCreditPeriodDays', Number(e.target.value))}
              hint={t('settings.defaults.creditDaysHint')}
            />
            <Input
              label={t('settings.defaults.creditLimit')}
              type="number"
              min="0"
              step="0.01"
              disabled={!form.creditSalesAllowed}
              value={String(form.defaultCreditLimit)}
              onChange={(e) => set('defaultCreditLimit', Number(e.target.value))}
              hint={t('settings.defaults.creditLimitHint')}
            />
          </div>
        </div>
        {!form.creditSalesAllowed && (
          <p className="mt-3 rounded-card border border-line bg-surface-3 px-3 py-2 text-xs text-ink-soft">
            {t('settings.defaults.creditOffNote')}
          </p>
        )}
      </Section>
    </div>
  )
}
