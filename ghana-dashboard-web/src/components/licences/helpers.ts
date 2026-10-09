import {
  LICENCE_ACCEPTED_EXTENSIONS,
  MAX_LICENCE_DOC_MB,
} from '@/api/licences'
import type { Licence, LicenceStatus, LicenceType } from '@/api/licences'

/** The three rings on the licence cards: healthy, due soon, lapsed. */
export type LicenceTone = 'leaf' | 'mustard' | 'clay'

export function toneFor(status: LicenceStatus): LicenceTone {
  if (status === 'EXPIRED') return 'clay'
  if (status === 'EXPIRING_SOON') return 'mustard'
  return 'leaf'
}

export const TONE_STROKE: Record<LicenceTone, string> = {
  leaf: 'stroke-brand-500',
  mustard: 'stroke-gold-500',
  clay: 'stroke-clay-500',
}

export const TONE_TRACK: Record<LicenceTone, string> = {
  leaf: 'stroke-brand-100 dark:stroke-brand-500/20',
  mustard: 'stroke-gold-100 dark:stroke-gold-500/20',
  clay: 'stroke-clay-100 dark:stroke-clay-500/20',
}

export const TONE_TEXT: Record<LicenceTone, string> = {
  leaf: 'text-brand-600 dark:text-brand-400',
  mustard: 'text-gold-600 dark:text-gold-400',
  clay: 'text-clay-600 dark:text-clay-400',
}

export const TONE_BADGE: Record<LicenceTone, 'primary' | 'success' | 'clay'> = {
  leaf: 'primary',
  mustard: 'success',
  clay: 'clay',
}

/**
 * How much of the licence's own validity window is left, as 0..1. Measuring
 * against the issue date (rather than a fixed year) keeps a one-month and a
 * one-year licence filling the ring by the same rule.
 */
export function remainingFraction(licence: Licence): number {
  const total = Date.parse(licence.expiryDate) - Date.parse(licence.issueDate)
  if (Number.isNaN(total) || total <= 0) return 0
  const left = Date.parse(licence.expiryDate) - Date.now()
  return Math.min(1, Math.max(0, left / total))
}

export function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

/** Mirrors FileStorageService: extension allowlist plus the 10 MB image cap. */
export function validateLicenceFile(file: File): 'type' | 'size' | null {
  const ext = extensionOf(file.name)
  if (!LICENCE_ACCEPTED_EXTENSIONS.includes(ext as (typeof LICENCE_ACCEPTED_EXTENSIONS)[number])) {
    return 'type'
  }
  return file.size > MAX_LICENCE_DOC_MB * 1024 * 1024 ? 'size' : null
}

/** Ids sorted so an ACTIVE shop that is about to lapse still surfaces first. */
export function byUrgency(a: Licence, b: Licence): number {
  return a.daysLeft - b.daysLeft
}

export function isLapsed(licence: Licence): boolean {
  return licence.status === 'EXPIRED'
}

export function typeLabelKey(type: LicenceType): string {
  return `licences.type.${type}`
}
