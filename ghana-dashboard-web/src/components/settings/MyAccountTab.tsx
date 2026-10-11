import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { fetchMyAccount } from '@/api/settings'
import { SaveBar, Section } from '@/components/settings/Section'
import { useSyncedForm } from '@/components/settings/useSyncedForm'
import { useSettingsMutations } from '@/components/settings/useSettingsMutations'
import { Badge, Button, ErrorState, Select, Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import type { Language } from '@/i18n'
import { Icon } from '@/utils/icons'

/**
 * Self-service profile. Deliberately cannot change name, phone, email or role - the
 * owner edits staff details from the Staff tab, and this page is the one settings
 * surface a staff member is allowed to open.
 */
export function MyAccountTab() {
  const { t, language, setLanguage } = useLanguage()
  const navigate = useNavigate()
  const account = useQuery({ queryKey: ['settings', 'my-account'], queryFn: fetchMyAccount })
  const { saveAccount } = useSettingsMutations()
  const stored = account.data?.preferredLanguage ?? language
  const { form: preferred, setForm: setPreferred, dirty, reset } = useSyncedForm(stored, language)

  if (account.isPending) {
    return <Skeleton className="h-72 w-full" />
  }

  if (account.isError) {
    return (
      <ErrorState
        title={t('settings.account.loadFailed')}
        message={t('settings.account.loadFailedBody')}
        retryLabel={t('common.retry')}
        onRetry={() => void account.refetch()}
      />
    )
  }

  const me = account.data
  return (
    <div className="space-y-4">
      <Section title={t('settings.account.profileTitle')} description={t('settings.account.profileDescription')}>
        <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
          <Row label={t('settings.staff.name')} value={me?.name} />
          <Row
            label={t('settings.account.role')}
            value={
              <Badge variant={me?.role === 'OWNER' ? 'warning' : 'secondary'}>
                {me?.role === 'OWNER' ? t('settings.staff.owner') : t('settings.account.staff')}
              </Badge>
            }
          />
          <Row label={t('settings.staff.phone')} value={me?.phone} />
          <Row label={t('settings.staff.email')} value={me?.email ?? '-'} />
          <Row label={t('settings.account.shop')} value={me?.shopName} />
          <Row
            label={t('settings.account.lastLogin')}
            value={
              me?.lastLoginAt
                ? new Date(me.lastLoginAt).toLocaleString('en-IN')
                : t('settings.staff.neverLoggedIn')
            }
          />
        </dl>
        <p className="mt-4 rounded-card border border-line bg-surface-3 px-3 py-2 text-xs text-ink-soft">
          {t('settings.account.readOnlyNote')}
        </p>
      </Section>

      <Section
        title={t('settings.account.languageTitle')}
        description={t('settings.account.languageDescription')}
      >
        <Select
          label={t('settings.account.language')}
          value={preferred}
          onChange={(value) => {
            setPreferred(value)
            // Apply immediately so the owner sees the effect without a reload.
            setLanguage(value as Language)
          }}
          options={[
            { value: 'en', label: 'English' },
            { value: 'hi', label: '\u0939\u093f\u0902\u0926\u0940 (Hindi)' },
            { value: 'mr', label: '\u092e\u0930\u093e\u0920\u0940 (Marathi)' },
          ]}
        />
        <SaveBar
          dirty={dirty}
          saving={saveAccount.isPending}
          onSave={() => saveAccount.mutate(preferred)}
          onReset={() => {
            reset()
            setLanguage(stored as Language)
          }}
          saveLabel={t('common.save')}
          resetLabel={t('common.reset')}
          savingLabel={t('common.saving')}
        />
      </Section>

      <Section
        title={t('settings.account.passwordTitle')}
        description={t('settings.account.passwordDescription')}
      >
        <Button variant="secondary" onClick={() => navigate('/change-password')}>
          <Icon name="password" className="size-4" />
          {t('settings.account.changePassword')}
        </Button>
      </Section>
    </div>
  )
}

function Row({ label, value }: { label: string; value?: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-ink-soft">{label}</dt>
      <dd className="mt-0.5 text-sm text-ink">{value ?? '-'}</dd>
    </div>
  )
}
