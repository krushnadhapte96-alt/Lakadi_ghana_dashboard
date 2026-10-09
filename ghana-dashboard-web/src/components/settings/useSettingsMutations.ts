import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import {
  createOilType,
  createStaff,
  deactivateOilType,
  removeShopLogo,
  resetStaffPassword,
  setStaffActive,
  updateBillingSettings,
  updateDefaults,
  updateMyAccount,
  updateNotificationSettings,
  updateOilType,
  uploadShopLogo,
  updateShopSettings,
  updateStaff,
} from '@/api/settings'
import type {
  BillingSettingsInput,
  DefaultsSettingsInput,
  NotificationSettingsInput,
  OilTypeInput,
  ShopSettingsInput,
  StaffUpdateInput,
} from '@/api/settings'
import { useLanguage } from '@/i18n'

const KEYS = {
  shop: ['settings', 'shop'] as const,
  logo: ['settings', 'shop', 'logo'] as const,
  billing: ['settings', 'billing'] as const,
  defaults: ['settings', 'defaults'] as const,
  notifications: ['settings', 'notifications'] as const,
  staff: ['settings', 'users'] as const,
  oilTypes: ['settings', 'oil-types'] as const,
  account: ['settings', 'my-account'] as const,
}

/**
 * Mutations for every settings surface, each reporting its own toast so the tab
 * components stay presentational.
 */
export function useSettingsMutations() {
  const client = useQueryClient()
  const { t } = useLanguage()

  const fail = (fallback: string) => (error: unknown) => {
    toast.error(normalizeError(error).message || fallback)
  }

  const saveShop = useMutation({
    mutationFn: ({ input, logo }: { input: ShopSettingsInput; logo: File | null }) =>
      updateShopSettings(input, logo),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.shop })
      void client.invalidateQueries({ queryKey: KEYS.logo })
      toast.success(t('settings.shop.saved'))
    },
    onError: fail(t('settings.shop.saveFailed')),
  })

  const uploadLogo = useMutation({
    mutationFn: (file: File) => uploadShopLogo(file),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.shop })
      void client.invalidateQueries({ queryKey: KEYS.logo })
      toast.success(t('settings.shop.logoUpdated'))
    },
    onError: fail(t('settings.shop.saveFailed')),
  })

  const deleteLogo = useMutation({
    mutationFn: removeShopLogo,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.shop })
      void client.invalidateQueries({ queryKey: KEYS.logo })
      toast.success(t('settings.shop.logoRemoved'))
    },
    onError: fail(t('settings.shop.saveFailed')),
  })

  const saveBilling = useMutation({
    mutationFn: (input: BillingSettingsInput) => updateBillingSettings(input),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.billing })
      toast.success(t('settings.billing.saved'))
    },
    onError: fail(t('settings.billing.saveFailed')),
  })

  const saveDefaults = useMutation({
    mutationFn: (input: DefaultsSettingsInput) => updateDefaults(input),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.defaults })
      toast.success(t('settings.defaults.saved'))
    },
    onError: fail(t('settings.defaults.saveFailed')),
  })

  const saveNotifications = useMutation({
    mutationFn: (input: NotificationSettingsInput) => updateNotificationSettings(input),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.notifications })
      toast.success(t('settings.notifications.saved'))
    },
    onError: fail(t('settings.notifications.saveFailed')),
  })

  const addStaff = useMutation({
    mutationFn: (input: { name: string; phone: string; email: string | null; password: string }) =>
      createStaff(input),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.staff })
      toast.success(t('settings.staff.created'))
    },
    onError: fail(t('settings.staff.saveFailed')),
  })

  const editStaff = useMutation({
    mutationFn: ({ id, input }: { id: number; input: StaffUpdateInput }) => updateStaff(id, input),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.staff })
      toast.success(t('settings.staff.updated'))
    },
    onError: fail(t('settings.staff.saveFailed')),
  })

  const toggleStaff = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => setStaffActive(id, active),
    onSuccess: (member) => {
      void client.invalidateQueries({ queryKey: KEYS.staff })
      toast.success(member.active ? t('settings.staff.reactivated') : t('settings.staff.deactivated'))
    },
    onError: fail(t('settings.staff.saveFailed')),
  })

  const resetPassword = useMutation({
    mutationFn: ({ id, password }: { id: number; password: string }) => resetStaffPassword(id, password),
    onSuccess: () => toast.success(t('settings.staff.passwordReset')),
    onError: fail(t('settings.staff.saveFailed')),
  })

  const addOilType = useMutation({
    mutationFn: (input: OilTypeInput) => createOilType(input),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.oilTypes })
      toast.success(t('settings.oilTypes.created'))
    },
    onError: fail(t('settings.oilTypes.saveFailed')),
  })

  const editOilType = useMutation({
    mutationFn: ({ id, input }: { id: number; input: OilTypeInput }) => updateOilType(id, input),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.oilTypes })
      toast.success(t('settings.oilTypes.updated'))
    },
    onError: fail(t('settings.oilTypes.saveFailed')),
  })

  const retireOilType = useMutation({
    mutationFn: (id: number) => deactivateOilType(id),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.oilTypes })
      toast.success(t('settings.oilTypes.deactivated'))
    },
    onError: fail(t('settings.oilTypes.saveFailed')),
  })

  const saveAccount = useMutation({
    mutationFn: (language: string | null) => updateMyAccount(language),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: KEYS.account })
      toast.success(t('settings.account.saved'))
    },
    onError: fail(t('settings.account.saveFailed')),
  })

  return {
    saveShop,
    uploadLogo,
    deleteLogo,
    saveBilling,
    saveDefaults,
    saveNotifications,
    addStaff,
    editStaff,
    toggleStaff,
    resetPassword,
    addOilType,
    editOilType,
    retireOilType,
    saveAccount,
  }
}
