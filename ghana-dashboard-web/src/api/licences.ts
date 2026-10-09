import { api } from '@/api/axios'
import { fetchBlob, saveBlob } from '@/api/download'
import type { ApiResponse } from '@/api/types'

/** Mirrors the chk_licences_type constraint. */
export const LICENCE_TYPES = [
  'FSSAI',
  'GST',
  'SHOP_ESTABLISHMENT',
  'TRADE',
  'WEIGHING_SCALE_STAMPING',
  'FIRE_NOC',
  'OTHER',
] as const
export type LicenceType = (typeof LICENCE_TYPES)[number]

export const LICENCE_STATUSES = ['ACTIVE', 'EXPIRING_SOON', 'EXPIRED'] as const
export type LicenceStatus = (typeof LICENCE_STATUSES)[number]

/** Reminder thresholds the daily sweep fires; matches LicenceService.REMINDER_DAYS. */
export const REMINDER_DAYS = [60, 30, 7] as const

/** Inside this window a licence reads as EXPIRING_SOON rather than ACTIVE. */
export const EXPIRING_SOON_DAYS = 30

/** FileStorageService allows these for non-video uploads. */
export const LICENCE_ACCEPTED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'pdf'] as const

export const MAX_LICENCE_DOC_MB = 10

export interface Licence {
  id: number
  licenceNumber: string
  type: LicenceType
  issuer: string | null
  issueDate: string
  expiryDate: string
  /** Negative once the licence has lapsed. */
  daysLeft: number
  status: LicenceStatus
  fssaiGstin: string | null
  hasDocument: boolean
  documentName: string | null
  documentType: string | null
  notes: string | null
  renewalCount: number
  lastRenewedOn: string | null
  createdAt: string
  updatedAt: string
}

export interface LicenceInput {
  licenceNumber: string
  type: LicenceType
  issuer: string | null
  issueDate: string
  expiryDate: string
  fssaiGstin: string | null
  notes: string | null
}

export interface Renewal {
  id: number
  licenceId: number
  previousNumber: string | null
  previousExpiryDate: string
  newNumber: string | null
  issueDate: string
  expiryDate: string
  hasDocument: boolean
  documentName: string | null
  notes: string | null
  renewedBy: string | null
  createdAt: string
}

export interface RenewalInput {
  newNumber: string | null
  issueDate: string
  expiryDate: string
  notes: string | null
}

export interface LicenceSummary {
  total: number
  active: number
  expiringSoon: number
  expired: number
  expiringIn7Days: number
  expiringIn30Days: number
  expiringIn60Days: number
  nextExpiry: string | null
  /** Printed on bills; null when the shop has no live FSSAI licence. */
  fssaiNumber: string | null
  gstin: string | null
  /** Registration types the shop is still missing. */
  missingTypes: string[]
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

/** Multipart parts carrying JSON must be tagged, or Spring binds them as plain text. */
function jsonPart(payload: unknown): Blob {
  return new Blob([JSON.stringify(payload)], { type: 'application/json' })
}

export async function fetchLicences(filters?: {
  status?: LicenceStatus | ''
  type?: LicenceType | ''
}): Promise<Licence[]> {
  const { data } = await api.get<ApiResponse<Licence[]>>('/licences', {
    params: { status: filters?.status || undefined, type: filters?.type || undefined },
  })
  return unwrap(data)
}

export async function fetchLicence(id: number): Promise<Licence> {
  const { data } = await api.get<ApiResponse<Licence>>(`/licences/${id}`)
  return unwrap(data)
}

export async function fetchLicenceSummary(): Promise<LicenceSummary> {
  const { data } = await api.get<ApiResponse<LicenceSummary>>('/licences/summary')
  return unwrap(data)
}

/** Register a licence, optionally with its scan attached in the same request. */
export async function createLicence(
  input: LicenceInput,
  file: File | null,
): Promise<Licence> {
  const form = new FormData()
  form.append('licence', jsonPart(input))
  if (file) form.append('file', file)
  const { data } = await api.post<ApiResponse<Licence>>('/licences', form)
  return unwrap(data)
}

export async function updateLicence(id: number, input: LicenceInput): Promise<Licence> {
  const { data } = await api.put<ApiResponse<Licence>>(`/licences/${id}`, input)
  return unwrap(data)
}

export async function deleteLicence(id: number): Promise<void> {
  await api.delete(`/licences/${id}`)
}

/** Replace the stored scan; the previous file is dropped server-side. */
export async function uploadLicenceDocument(id: number, file: File): Promise<Licence> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.post<ApiResponse<Licence>>(`/licences/${id}/document`, form)
  return unwrap(data)
}

/** Renewing without a file is the "Mark renewed" path. */
export async function renewLicence(
  id: number,
  input: RenewalInput,
  file: File | null,
): Promise<Licence> {
  const form = new FormData()
  form.append('renewal', jsonPart(input))
  if (file) form.append('file', file)
  const { data } = await api.post<ApiResponse<Licence>>(`/licences/${id}/renew`, form)
  return unwrap(data)
}

export async function fetchRenewals(id: number): Promise<Renewal[]> {
  const { data } = await api.get<ApiResponse<Renewal[]>>(`/licences/${id}/renewals`)
  return unwrap(data)
}

/**
 * Documents sit behind the JWT filter, so they come back as a blob rather than a
 * plain <img src> that would 401.
 */
export function fetchLicenceDocumentBlob(id: number, download = false): Promise<Blob> {
  return fetchBlob(`/licences/${id}/document`, download ? { download: 'true' } : undefined)
}

export function fetchRenewalDocumentBlob(
  id: number,
  renewalId: number,
  download = false,
): Promise<Blob> {
  return fetchBlob(`/licences/${id}/renewals/${renewalId}/document`, download ? { download: 'true' } : undefined)
}

export async function downloadLicenceDocument(id: number, fileName: string): Promise<void> {
  saveBlob(await fetchLicenceDocumentBlob(id, true), fileName)
}

export async function downloadRenewalDocument(
  id: number,
  renewalId: number,
  fileName: string,
): Promise<void> {
  saveBlob(await fetchRenewalDocumentBlob(id, renewalId, true), fileName)
}
