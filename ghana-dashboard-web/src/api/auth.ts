import { api, normalizeError } from '@/api/axios'
import type { ApiResponse, AuthUser, TokenResponse, UserSummary } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useLanguageStore } from '@/i18n/store'
import { fetchMyAccount } from '@/api/settings'

export interface RegisterShopInput {
  shopName: string
  ownerName: string
  phone: string
  email?: string
  password: string
}

export async function registerShop(input: RegisterShopInput): Promise<AuthUser> {
  const { data } = await api.post<ApiResponse<AuthUser>>('/auth/register-shop', input)
  if (!data.success || !data.data) throw new Error(data.message)
  return data.data
}

export async function login(
  identifier: string,
  password: string,
): Promise<TokenResponse> {
  const { data } = await api.post<ApiResponse<TokenResponse>>('/auth/login', {
    identifier,
    password,
  })
  if (!data.success || !data.data?.accessToken) throw new Error(data.message)
  useAuthStore.getState().setSession(data.data.accessToken, data.data.user)

  void fetchMyAccount()
    .then((account) => {
      const lang = account.preferredLanguage
      if (lang === 'hi' || lang === 'mr') useLanguageStore.getState().setLanguage(lang)
    })
    .catch(() => undefined)

  return data.data
}

export async function logout(): Promise<void> {
  try {
    await api.post('/auth/logout')
  } finally {
    useAuthStore.getState().clearSession()
  }
}

export async function logoutAll(): Promise<void> {
  try {
    await api.post('/auth/logout-all')
  } finally {
    useAuthStore.getState().clearSession()
  }
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const { data } = await api.post<ApiResponse<null>>('/auth/change-password', {
    currentPassword,
    newPassword,
  })
  if (!data.success) throw new Error(data.message)
}

export async function fetchMe(): Promise<AuthUser> {
  const { data } = await api.get<ApiResponse<AuthUser>>('/auth/me')
  if (!data.success || !data.data) throw new Error(data.message)
  return data.data
}

export async function fetchShopUsers(): Promise<UserSummary[]> {
  const { data } = await api.get<ApiResponse<UserSummary[]>>('/auth/users')
  if (!data.success || !data.data) throw new Error(data.message)
  return data.data
}

export { normalizeError }
