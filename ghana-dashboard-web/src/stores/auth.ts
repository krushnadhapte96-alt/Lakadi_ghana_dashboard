import { create } from 'zustand'
import type { AuthUser } from '@/api/types'

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

interface AuthState {
  user: AuthUser | null
  accessToken: string | null
  status: AuthStatus
  setSession: (accessToken: string, user: AuthUser) => void
  clearSession: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: null,
  status: 'loading',
  setSession: (accessToken, user) => set({ accessToken, user, status: 'authenticated' }),
  clearSession: () => set({ accessToken: null, user: null, status: 'unauthenticated' }),
}))

export function useAuth() {
  const user = useAuthStore((s) => s.user)
  const accessToken = useAuthStore((s) => s.accessToken)
  const status = useAuthStore((s) => s.status)
  return { user, accessToken, status }
}

export function useRole() {
  return useAuthStore((s) => s.user?.role ?? null)
}

export const getAccessToken = () => useAuthStore.getState().accessToken
export const getAuthUser = () => useAuthStore.getState().user
