import { api } from '@/api/axios'
import { fetchBlob, saveBlob } from '@/api/download'
import type { ApiResponse } from '@/api/types'

/** Mirrors FileStorageService: image types it accepts, and the shared size ceiling. */
export const LOGO_ACCEPTED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp'] as const
export const MAX_LOGO_MB = 10

// ==================== shop profile ====================

export interface ShopSettings {
  id: number
  name: string
  phone: string
  email: string | null
  address: string | null
  gstin: string | null
  fssaiNumber: string | null
  upiId: string | null
  hasLogo: boolean
  logoName: string | null
  logoUrl: string | null
  /** True when encrypted bank details are stored; the plaintext is never returned. */
  bankDetailsOnFile: boolean
  bankDetailsMasked: string | null
  currencyCode: string
}

export interface ShopSettingsInput {
  name: string
  phone: string
  email: string | null
  address: string | null
  gstin: string | null
  fssaiNumber: string | null
  upiId: string | null
  /**
   * Write-only. Leave null or empty to keep whatever is already stored, so the masked
   * value shown in the form can be round-tripped without erasing the real account.
   */
  bankDetails: string | null
}

// ==================== billing ====================

export interface BillingSettings {
  billPrefix: string
  footerMessage: string
  showBatchNumber: boolean
  showUpiQr: boolean
  showBankDetails: boolean
}

export type BillingSettingsInput = BillingSettings

// ==================== defaults ====================

export interface DefaultsSettings {
  defaultLowStockThreshold: number
  defaultBestBeforeDays: number
  creditSalesAllowed: boolean
  defaultCreditPeriodDays: number
  defaultCreditLimit: number
}

export type DefaultsSettingsInput = DefaultsSettings

// ==================== notifications ====================

export interface NotificationSettings {
  alertEmail: string
  lowStockAlerts: boolean
  expiryAlerts: boolean
  licenceReminders: boolean
  paymentReminders: boolean
  dailyDigest: boolean
}

export type NotificationSettingsInput = NotificationSettings

// ==================== staff ====================

export type StaffRole = 'OWNER' | 'STAFF'

export interface StaffMember {
  id: number
  name: string
  phone: string
  email: string | null
  role: StaffRole
  active: boolean
  lastLoginAt: string | null
  createdAt: string | null
}

export interface StaffCreateInput {
  name: string
  phone: string
  email: string | null
  password: string
}

export interface StaffUpdateInput {
  name: string
  phone: string
  email: string | null
}

// ==================== my account ====================

export interface MyAccount {
  id: number
  name: string
  phone: string
  email: string | null
  role: StaffRole
  active: boolean
  shopName: string
  lastLoginAt: string | null
  preferredLanguage: string | null
}

// ==================== export ====================

export interface ExportManifestEntry {
  key: string
  label: string
  rows: number
}

export interface ExportManifest {
  generatedAt: string
  entries: ExportManifestEntry[]
}

// ==================== helpers ====================

/** Multipart parts carrying JSON must be tagged, or Spring binds them as plain text. */
function jsonPart(payload: unknown): Blob {
  return new Blob([JSON.stringify(payload)], { type: 'application/json' })
}

function unwrap<T>(response: ApiResponse<T>): T {
  if (!response.success || response.data == null) {
    throw new Error(response.message || 'Request failed')
  }
  return response.data
}

// ==================== shop profile ====================

export async function fetchShopSettings(): Promise<ShopSettings> {
  const { data } = await api.get<ApiResponse<ShopSettings>>('/settings/shop')
  return unwrap(data)
}

/** Saves the profile, attaching a new logo in the same request when one is chosen. */
export async function updateShopSettings(
  input: ShopSettingsInput,
  logo: File | null,
): Promise<ShopSettings> {
  if (!logo) {
    const { data } = await api.put<ApiResponse<ShopSettings>>('/settings/shop', input)
    return unwrap(data)
  }
  const form = new FormData()
  form.append('shop', jsonPart(input))
  form.append('logo', logo)
  const { data } = await api.put<ApiResponse<ShopSettings>>('/settings/shop', form)
  return unwrap(data)
}

export async function uploadShopLogo(logo: File): Promise<ShopSettings> {
  const form = new FormData()
  form.append('logo', logo)
  const { data } = await api.post<ApiResponse<ShopSettings>>('/settings/shop/logo', form)
  return unwrap(data)
}

export async function removeShopLogo(): Promise<ShopSettings> {
  const { data } = await api.delete<ApiResponse<ShopSettings>>('/settings/shop/logo')
  return unwrap(data)
}

/** The logo is behind the JWT filter, so the preview uses a blob URL. */
export function fetchShopLogoBlob(): Promise<Blob> {
  return fetchBlob('/settings/shop/logo')
}

// ==================== billing ====================

export async function fetchBillingSettings(): Promise<BillingSettings> {
  const { data } = await api.get<ApiResponse<BillingSettings>>('/settings/billing')
  return unwrap(data)
}

export async function updateBillingSettings(
  input: BillingSettingsInput,
): Promise<BillingSettings> {
  const { data } = await api.put<ApiResponse<BillingSettings>>('/settings/billing', input)
  return unwrap(data)
}

// ==================== defaults ====================

export async function fetchDefaults(): Promise<DefaultsSettings> {
  const { data } = await api.get<ApiResponse<DefaultsSettings>>('/settings/defaults')
  return unwrap(data)
}

export async function updateDefaults(input: DefaultsSettingsInput): Promise<DefaultsSettings> {
  const { data } = await api.put<ApiResponse<DefaultsSettings>>('/settings/defaults', input)
  return unwrap(data)
}

// ==================== notifications ====================

export async function fetchNotificationSettings(): Promise<NotificationSettings> {
  const { data } = await api.get<ApiResponse<NotificationSettings>>('/settings/notifications')
  return unwrap(data)
}

export async function updateNotificationSettings(
  input: NotificationSettingsInput,
): Promise<NotificationSettings> {
  const { data } = await api.put<ApiResponse<NotificationSettings>>(
    '/settings/notifications',
    input,
  )
  return unwrap(data)
}

// ==================== staff ====================

export async function fetchStaff(): Promise<StaffMember[]> {
  const { data } = await api.get<ApiResponse<StaffMember[]>>('/settings/users')
  return unwrap(data)
}

export async function createStaff(input: StaffCreateInput): Promise<StaffMember> {
  const { data } = await api.post<ApiResponse<StaffMember>>('/settings/users', input)
  return unwrap(data)
}

export async function updateStaff(
  id: number,
  input: StaffUpdateInput,
): Promise<StaffMember> {
  const { data } = await api.put<ApiResponse<StaffMember>>(`/settings/users/${id}`, input)
  return unwrap(data)
}

export async function setStaffActive(id: number, active: boolean): Promise<StaffMember> {
  const { data } = await api.put<ApiResponse<StaffMember>>(`/settings/users/${id}/active`, null, {
    params: { active },
  })
  return unwrap(data)
}

export async function resetStaffPassword(id: number, password: string): Promise<StaffMember> {
  const { data } = await api.post<ApiResponse<StaffMember>>(
    `/settings/users/${id}/reset-password`,
    { password },
  )
  return unwrap(data)
}

// ==================== oil types ====================

export interface OilType {
  id: number
  nameEn: string
  nameHi: string
  density: number
  bestBeforeDays: number
  colourTag: string
  active: boolean
  createdAt: string | null
}

export interface OilTypeInput {
  nameEn: string
  nameHi: string
  density: number
  bestBeforeDays: number
  colourTag: string
  active?: boolean
}

/** `includeInactive` is what lets Settings show deactivated oil types and revive them. */
export async function fetchOilTypes(includeInactive = false): Promise<OilType[]> {
  const { data } = await api.get<ApiResponse<OilType[]>>('/oil-types', {
    params: includeInactive ? { includeInactive: 'true' } : undefined,
  })
  return unwrap(data)
}

export async function createOilType(input: OilTypeInput): Promise<OilType> {
  const { data } = await api.post<ApiResponse<OilType>>('/oil-types', { ...input, active: true })
  return unwrap(data)
}

export async function updateOilType(id: number, input: OilTypeInput): Promise<OilType> {
  const { data } = await api.put<ApiResponse<OilType>>(`/oil-types/${id}`, input)
  return unwrap(data)
}

/** Deactivates the oil type; products already using it keep working. */
export async function deactivateOilType(id: number): Promise<void> {
  await api.delete(`/oil-types/${id}`)
}

// ==================== my account ====================

export async function fetchMyAccount(): Promise<MyAccount> {
  const { data } = await api.get<ApiResponse<MyAccount>>('/settings/my-account')
  return unwrap(data)
}

export async function updateMyAccount(preferredLanguage: string | null): Promise<MyAccount> {
  const { data } = await api.put<ApiResponse<MyAccount>>('/settings/my-account', {
    preferredLanguage,
  })
  return unwrap(data)
}

// ==================== export ====================

export async function fetchExportManifest(): Promise<ExportManifest> {
  const { data } = await api.get<ApiResponse<ExportManifest>>('/settings/export/manifest')
  return unwrap(data)
}

/** Downloads the whole-shop zip; the file name comes from Content-Disposition. */
export async function downloadExport(): Promise<void> {
  const { data, headers } = await api.get<Blob>('/settings/export', { responseType: 'blob' })
  const disposition = String(headers['content-disposition'] ?? '')
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
  saveBlob(data, match?.[1] ?? `lakadi-ghana-export-${new Date().toISOString().slice(0, 10)}.zip`)
}

