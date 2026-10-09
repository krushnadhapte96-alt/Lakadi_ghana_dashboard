import { useQuery } from '@tanstack/react-query'
import { fetchNotificationSettings } from '@/api/settings'
import type { NotificationSettings } from '@/api/settings'
import { SaveBar, Section } from '@/components/settings/Section'
import { useSyncedForm } from '@/components/settings/useSyncedForm'
import { useSettingsMutations } from '@/components/settings/useSettingsMutations'
import { ErrorState, Input, Skeleton, Switch } from '@/components/ui'
import { useLanguage } from '@/i18n'

const FALLBACK: NotificationSettings = {
  alertEmail: '',
  lowStockAlerts: true,
  expiryAlerts: true,
  licenceReminders: true,
  paymentReminders: false,
  dailyDigest: false,
}

export function NotificationsTab() {
  const { t } = useLanguage()
  const notifications = useQuery({
    queryKey: ['settings', 'notifications'],
    queryFn: fetchNotificationSettings,
  })
  const { saveNotifications } = useSettingsMutations()

  const { form, setForm, dirty, reset } = useSyncedForm(notifications.data, FALLBACK)

  const set = <K extends keyof NotificationSettings>(key: K, value: NotificationSettings[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  if (notifications.isPending) {
    return <Skeleton className="h-96 w-full" />
  }

  if (notifications.isError) {
    return (
      <ErrorState
        title={t('settings.notifications.loadFailed')}
        message={t('settings.notifications.loadFailedBody')}
        retryLabel={t('common.retry')}
        onRetry={() => void notifications.refetch()}
      />
    )
  }

  return (
    <Section
      title={t('settings.notifications.title')}
      description={t('settings.notifications.description')}
      note={
        <p className="rounded-card border border-line bg-surface-3 px-3 py-2 text-xs text-ink-soft">
          {t('settings.notifications.previewNote')}
        </p>
      }
    >
      <div className="space-y-4">
        <Input
          label={t('settings.notifications.email')}
          type="email"
          value={form.alertEmail}
          onChange={(e) => set('alertEmail', e.target.value)}
          placeholder="alerts@example.com"
          hint={t('settings.notifications.emailHint')}
        />
        <div className="divide-y divide-line">
          <Switch
            label={t('settings.notifications.lowStock')}
            hint={t('settings.notifications.lowStockHint')}
            checked={form.lowStockAlerts}
            onChange={(e) => set('lowStockAlerts', e.target.checked)}
          />
          <Switch
            label={t('settings.notifications.expiry')}
            hint={t('settings.notifications.expiryHint')}
            checked={form.expiryAlerts}
            onChange={(e) => set('expiryAlerts', e.target.checked)}
          />
          <Switch
            label={t('settings.notifications.licences')}
            hint={t('settings.notifications.licencesHint')}
            checked={form.licenceReminders}
            onChange={(e) => set('licenceReminders', e.target.checked)}
          />
          <Switch
            label={t('settings.notifications.payments')}
            hint={t('settings.notifications.paymentsHint')}
            checked={form.paymentReminders}
            onChange={(e) => set('paymentReminders', e.target.checked)}
          />
          <Switch
            label={t('settings.notifications.digest')}
            hint={t('settings.notifications.digestHint')}
            checked={form.dailyDigest}
            onChange={(e) => set('dailyDigest', e.target.checked)}
          />
        </div>
      </div>
      <SaveBar
        dirty={dirty}
        saving={saveNotifications.isPending}
        onSave={() => saveNotifications.mutate(form)}
        onReset={() => reset()}
        saveLabel={t('common.save')}
        resetLabel={t('common.reset')}
        savingLabel={t('common.saving')}
      />
    </Section>
  )
}
