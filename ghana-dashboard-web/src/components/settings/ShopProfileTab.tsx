import { useEffect, useRef, useState } from 'react'
import { useSyncedForm } from '@/components/settings/useSyncedForm'
import { useQuery } from '@tanstack/react-query'
import { fetchShopLogoBlob, fetchShopSettings, LOGO_ACCEPTED_EXTENSIONS, MAX_LOGO_MB } from '@/api/settings'
import type { ShopSettingsInput } from '@/api/settings'
import { Section, SaveBar } from '@/components/settings/Section'
import { useSettingsMutations } from '@/components/settings/useSettingsMutations'
import { Button, ErrorState, Input, Skeleton, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { Icon } from '@/utils/icons'

interface FormState {
  name: string
  phone: string
  email: string
  address: string
  gstin: string
  fssaiNumber: string
  upiId: string
  bankDetails: string
}

const EMPTY: FormState = {
  name: '',
  phone: '',
  email: '',
  address: '',
  gstin: '',
  fssaiNumber: '',
  upiId: '',
  bankDetails: '',
}

/** Bank details are write-only, so the loaded value only records that one exists. */
function toFormState(shop: Awaited<ReturnType<typeof fetchShopSettings>>): FormState {
  return {
    name: shop.name,
    phone: shop.phone,
    email: shop.email ?? '',
    address: shop.address ?? '',
    gstin: shop.gstin ?? '',
    fssaiNumber: shop.fssaiNumber ?? '',
    upiId: shop.upiId ?? '',
    bankDetails: '',
  }
}

export function ShopProfileTab() {
  const { t } = useLanguage()
  const shop = useQuery({ queryKey: ['settings', 'shop'], queryFn: fetchShopSettings })
  const { saveShop, uploadLogo, deleteLogo } = useSettingsMutations()

  const { form, setForm, dirty, reset, commit } = useSyncedForm(
    shop.data ? toFormState(shop.data) : undefined,
    EMPTY,
  )
  const [logoName, setLogoName] = useState<string | null>(null)
  const [logoError, setLogoError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))
  const pickLogo = (file: File | null) => {
    if (!file) return
    const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!LOGO_ACCEPTED_EXTENSIONS.includes(extension as (typeof LOGO_ACCEPTED_EXTENSIONS)[number])) {
      setLogoError(t('settings.shop.logoTypeError'))
      return
    }
    if (file.size > MAX_LOGO_MB * 1024 * 1024) {
      setLogoError(t('settings.shop.logoSizeError', { limit: MAX_LOGO_MB }))
      return
    }
    setLogoError(null)
    setLogoName(file.name)
    uploadLogo.mutate(file)
  }

  const submit = () => {
    const input: ShopSettingsInput = {
      name: form.name.trim(),
      phone: form.phone.trim(),
      email: form.email.trim() || null,
      address: form.address.trim() || null,
      gstin: form.gstin.trim() || null,
      fssaiNumber: form.fssaiNumber.trim() || null,
      upiId: form.upiId.trim() || null,
      // Empty means "keep the stored value"; the field is never sent back to the client.
      bankDetails: form.bankDetails.trim() || null,
    }
    saveShop.mutate({ input, logo: null })
    // The bank field is write-only, so clear it locally once the write lands; the
    // masked summary from the refetched query is what the user sees from now on.
    const cleared: FormState = {
      name: input.name,
      phone: input.phone,
      email: input.email ?? '',
      address: input.address ?? '',
      gstin: input.gstin ?? '',
      fssaiNumber: input.fssaiNumber ?? '',
      upiId: input.upiId ?? '',
      bankDetails: '',
    }
    commit(cleared)
  }

  if (shop.isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-64 w-full" />
        <Skeleton className="h-72 w-full" />
      </div>
    )
  }

  if (shop.isError) {
    return (
      <ErrorState
        title={t('settings.shop.loadFailed')}
        message={t('settings.shop.loadFailedBody')}
        retryLabel={t('common.retry')}
        onRetry={() => void shop.refetch()}
      />
    )
  }

  const current = shop.data

  return (
    <div className="space-y-4">
      <Section
        title={t('settings.shop.logoTitle')}
        description={t('settings.shop.logoDescription')}
      >
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-card border border-dashed border-line bg-surface-3">
            {current?.hasLogo ? (
              <LogoThumb />
            ) : (
              <Icon name="image" className="size-8 text-ink-soft/50" />
            )}
          </div>
          <div className="min-w-0 flex-1">
            <input
              ref={fileRef}
              type="file"
              accept={LOGO_ACCEPTED_EXTENSIONS.map((e) => `.${e}`).join(',')}
              className="hidden"
              onChange={(event) => {
                pickLogo(event.target.files?.[0] ?? null)
                event.target.value = ''
              }}
            />
            <div className="flex flex-wrap gap-2">
              <Button
                variant="secondary"
                onClick={() => fileRef.current?.click()}
                disabled={uploadLogo.isPending}
              >
                <Icon name="upload" className="size-4" />
                {uploadLogo.isPending ? t('common.uploading') : t('settings.shop.logoChoose')}
              </Button>
              {current?.hasLogo && (
                <Button
                  variant="ghost"
                  onClick={() => deleteLogo.mutate()}
                  disabled={deleteLogo.isPending}
                >
                  <Icon name="trash" className="size-4" />
                  {t('settings.shop.logoRemove')}
                </Button>
              )}
            </div>
            <p className="mt-2 text-xs text-ink-soft">
              {t('settings.shop.logoHint', { limit: MAX_LOGO_MB })}
            </p>
            {logoName && (
              <p className="mt-1 truncate text-xs text-brand-600">{logoName}</p>
            )}
            {logoError && (
              <p role="alert" className="mt-1 text-xs text-crimson-600">
                {logoError}
              </p>
            )}
          </div>
        </div>
      </Section>

      <Section
        title={t('settings.shop.detailsTitle')}
        description={t('settings.shop.detailsDescription')}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label={t('settings.shop.name')}
            value={form.name}
            onChange={(e) => set('name', e.target.value)}
            placeholder={t('settings.shop.namePlaceholder')}
          />
          <Input
            label={t('settings.shop.phone')}
            value={form.phone}
            onChange={(e) => set('phone', e.target.value)}
            placeholder="9876543210"
          />
          <Input
            label={t('settings.shop.email')}
            type="email"
            value={form.email}
            onChange={(e) => set('email', e.target.value)}
            placeholder="shop@example.com"
          />
          <Input
            label={t('settings.shop.upi')}
            value={form.upiId}
            onChange={(e) => set('upiId', e.target.value)}
            placeholder="shop@okaxis"
            hint={t('settings.shop.upiHint')}
          />
          <div className="sm:col-span-2">
            <Textarea
              label={t('settings.shop.address')}
              rows={2}
              value={form.address}
              onChange={(e) => set('address', e.target.value)}
              placeholder={t('settings.shop.addressPlaceholder')}
            />
          </div>
          <Input
            label={t('settings.shop.gstin')}
            value={form.gstin}
            onChange={(e) => set('gstin', e.target.value.toUpperCase())}
            placeholder="24ABCDE1234F1Z5"
            hint={t('settings.shop.gstinHint')}
          />
          <Input
            label={t('settings.shop.fssai')}
            value={form.fssaiNumber}
            onChange={(e) => set('fssaiNumber', e.target.value)}
            placeholder="11223344000567"
            hint={t('settings.shop.fssaiHint')}
          />
        </div>
        <SaveBar
          dirty={dirty}
          saving={saveShop.isPending}
          onSave={submit}
          onReset={() => reset()}
          saveLabel={t('common.save')}
          resetLabel={t('common.reset')}
          savingLabel={t('common.saving')}
        />
      </Section>

      <Section
        title={t('settings.shop.bankTitle')}
        description={t('settings.shop.bankDescription')}
      >
        <div className="space-y-3">
          {current?.bankDetailsOnFile && (
            <div className="rounded-card border border-line bg-surface-3 px-3 py-2">
              <p className="text-xs font-medium text-ink-soft">{t('settings.shop.bankOnFile')}</p>
              <p className="mt-0.5 break-words text-sm text-ink">{current.bankDetailsMasked}</p>
            </div>
          )}
          <Textarea
            label={current?.bankDetailsOnFile ? t('settings.shop.bankReplace') : t('settings.shop.bankDetails')}
            rows={4}
            value={form.bankDetails}
            onChange={(e) => set('bankDetails', e.target.value)}
            placeholder={t('settings.shop.bankPlaceholder')}
            hint={t('settings.shop.bankHint')}
          />
        </div>
      </Section>
    </div>
  )
}

/** Fetches the protected logo as a blob so the thumbnail is not a broken <img src>. */
function LogoThumb() {
  const [url, setUrl] = useState<string | null>(null)

  useEffect(() => {
    let objectUrl: string | null = null
    let cancelled = false
    fetchShopLogoBlob()
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [])

  if (!url) return <Skeleton className="h-full w-full" />
  return <img src={url} alt="" className="h-full w-full object-contain" />
}
