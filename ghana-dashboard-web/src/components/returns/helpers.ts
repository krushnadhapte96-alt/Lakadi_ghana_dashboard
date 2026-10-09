import { MAX_PHOTOS, type ReturnResolution, type ReturnStatus, type ReturnType } from '@/api/returns'

type BadgeVariant =
  | 'default'
  | 'primary'
  | 'secondary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'clay'

/** Matches app.storage.max-file-size-mb in the dev profile. */
export const MAX_PHOTO_BYTES = 10 * 1024 * 1024
export const PHOTO_ACCEPT = '.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp'

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const ALLOWED_EXTENSIONS = /\.(jpe?g|png|webp)$/i

/** Chart colours are plain hex so the SVG donut reads the same in both themes. */
export const TYPE_COLOUR: Record<ReturnType, string> = {
  LEAKAGE: '#C2410C',
  BROKEN: '#B91C1C',
  TASTE_SMELL: '#A16207',
  WRONG_ITEM: '#7C3AED',
  QTY_SHORT: '#0F766E',
  OTHER: '#78716C',
}

export const STATUS_VARIANT: Record<ReturnStatus, BadgeVariant> = {
  OPEN: 'danger',
  IN_REVIEW: 'warning',
  RESOLVED: 'success',
}

export const RESOLUTION_VARIANT: Record<ReturnResolution, BadgeVariant> = {
  REFUND: 'info',
  REPLACE: 'primary',
  REJECT: 'default',
}

/** A lookup row: the quantity still returnable and the rate it was sold at. */
export interface ReturnableLine {
  productId: number
  qty: number
  rate: number
}

/**
 * True when a typed quantity cannot be returned: negative, non-numeric, or more
 * than what the bill still allows. An empty field is not a problem - the row is
 * simply not being returned.
 */
export function qtyProblem(line: ReturnableLine, raw: string): boolean {
  if (raw === '') return false
  const qty = Number(raw)
  return !Number.isFinite(qty) || qty <= 0 || qty > line.qty
}

export function photoFileError(file: File): 'fileType' | 'fileSize' | null {
  if (!ALLOWED_TYPES.includes(file.type) && !ALLOWED_EXTENSIONS.test(file.name)) return 'fileType'
  if (file.size > MAX_PHOTO_BYTES) return 'fileSize'
  return null
}

/** Filters the picks a user made down to the ones the API will accept. */
export function validPhotoFiles(files: File[]): { accepted: File[]; rejected: number } {
  const accepted = files.filter((file) => photoFileError(file) === null)
  return { accepted: accepted.slice(0, MAX_PHOTOS), rejected: files.length - accepted.length }
}

