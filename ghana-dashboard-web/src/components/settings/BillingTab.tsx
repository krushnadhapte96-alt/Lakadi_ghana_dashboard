import { useQuery } from '@tanstack/react-query'
import { fetchBillingSettings, fetchShopSettings } from '@/api/settings'
import type { BillingSettings } from '@/api/settings'
import { BillPreview } from '@/components/settings/BillPreview'
import { useSyncedForm } from '@/components/settings/useSyncedForm'
import { SaveBar, Section } from '@/components/settings/Section'
import { useSettingsMutations } from '@/components/settings/useSettingsMutations'
import { ErrorState, Input, Skeleton, Switch, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'

const FALLBACK: BillingSettings = {
  billPrefix: 'S',
  footerMessage: '',
  showBatchNumber: false,
  showUpiQr: true,
  showBankDetails: false,
}

export function BillingTab() {
  const { t } = useLanguage()
  const billing = useQuery({ queryKey: ['settings', 'billing'], queryFn: fetchBillingSettings })
  const shop = useQuery({ queryKey: ['settings', 'shop'], queryFn: fetchShopSettings })
  const { saveBilling } = useSettingsMutations()

  const { form, setForm, dirty, reset } = useSyncedForm(billing.data, FALLBACK)

  const set = <K extends keyof BillingSettings>(key: K, value: BillingSettings[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))
  const loading = billing.isPending || shop.isPending

  if (billing.isError) {
    return (
      <ErrorState
        title={t('settings.billing.loadFailed')}
        message={t('settings.billing.loadFailedBody')}
        retryLabel={t('common.retry')}
        onRetry={() => void billing.refetch()}
      />
    )
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <div className="space-y-4">
        <Section
          title={t('settings.billing.numbersTitle')}
          description={t('settings.billing.numbersDescription')}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              label={t('settings.billing.prefix')}
              value={form.billPrefix}
              maxLength={10}
              onChange={(e) => set('billPrefix', e.target.value.toUpperCase())}
              placeholder="S"
              hint={t('settings.billing.prefixHint')}
            />
            <div className="flex items-end">
              <p className="pb-2 text-sm text-ink-soft">
                {t('settings.billing.prefixExample', {
                  prefix: form.billPrefix || 'S',
                  date: '202610',
                  number: '0001',
                })}
              </p>
            </div>
          </div>
        </Section>

        <Section
          title={t('settings.billing.optionsTitle')}
          description={t('settings.billing.optionsDescription')}
        >
          <div className="space-y-4">
            <Switch
              label={t('settings.billing.showBatch')}
              hint={t('settings.billing.showBatchHint')}
              checked={form.showBatchNumber}
              onChange={(e) => set('showBatchNumber', e.target.checked)}
            />
            <Switch
              label={t('settings.billing.showQr')}
              hint={
                shop.data?.upiId
                  ? t('settings.billing.showQrHint')
                  : t('settings.billing.showQrNoUpi')
              }
              checked={form.showUpiQr}
              onChange={(e) => set('showUpiQr', e.target.checked)}
            />
            <Switch
              label={t('settings.billing.showBank')}
              hint={
                shop.data?.bankDetailsOnFile
                  ? t('settings.billing.showBankHint')
                  : t('settings.billing.showBankNoDetails')
              }
              checked={form.showBankDetails}
              onChange={(e) => set('showBankDetails', e.target.checked)}
            />
            <Textarea
              label={t('settings.billing.footer')}
              rows={2}
              value={form.footerMessage}
              maxLength={300}
              onChange={(e) => set('footerMessage', e.target.value)}
              placeholder={t('settings.billing.footerPlaceholder')}
              hint={t('settings.billing.footerHint')}
            />
          </div>
          <SaveBar
            dirty={dirty}
            saving={saveBilling.isPending}
            onSave={() => saveBilling.mutate(form)}
            onReset={() => reset()}
            saveLabel={t('common.save')}
            resetLabel={t('common.reset')}
            savingLabel={t('common.saving')}
          />
        </Section>
      </div>

      <div className="lg:sticky lg:top-4 lg:self-start">
        <h2 className="mb-2 text-sm font-semibold text-ink">{t('settings.billing.preview.title')}</h2>
        {loading ? <Skeleton className="mx-auto h-[32rem] w-full max-w-sm" /> : null}
        {!loading ? (
          <BillPreview shop={shop.data} billing={billing.data ?? FALLBACK} />
        ) : null}
      </div>
    </div>
  )
}
