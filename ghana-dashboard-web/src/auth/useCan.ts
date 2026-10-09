import { useAuthStore } from '@/stores/auth'
import { canUse, isOwner } from '@/auth/permissions'
import type { Permission } from '@/auth/permissions'

export function useCan(permission: Permission): boolean {
  const user = useAuthStore((s) => s.user)
  return canUse(user, permission)
}

export function useIsOwner(): boolean {
  const user = useAuthStore((s) => s.user)
  return isOwner(user)
}
