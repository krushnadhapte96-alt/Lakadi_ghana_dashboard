import type { AuthUser } from '@/api/types'

/**
 * Feature permissions granted per role. The backend enforces the real rules
 * (@PreAuthorize); this mirror exists so the UI can hide buttons & nav items.
 */
export type Permission =
  | 'read:pnl'
  | 'read:expenses'
  | 'read:costing'
  | 'read:settings'
  | 'manage:settings'
  | 'manage:users'

const OWNER_PERMISSIONS = new Set<Permission>([
  'read:pnl',
  'read:expenses',
  'read:costing',
  'read:settings',
  'manage:settings',
  'manage:users',
])

const STAFF_PERMISSIONS = new Set<Permission>([])

export function canUse(user: AuthUser | null, permission: Permission): boolean {
  if (!user) return false
  return user.role === 'OWNER'
    ? OWNER_PERMISSIONS.has(permission)
    : STAFF_PERMISSIONS.has(permission)
}

export function isOwner(user: AuthUser | null): boolean {
  return user?.role === 'OWNER'
}
