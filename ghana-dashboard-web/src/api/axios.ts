import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, TokenResponse } from '@/api/types'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    Accept: 'application/json',
  },
})

// A retry marker so a single request never loops on 401s.
interface RetryableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

let refreshPromise: Promise<boolean> | null = null

async function performRefresh(): Promise<boolean> {
  try {
    const { data } = await axios.post<ApiResponse<TokenResponse>>(
      `${API_BASE_URL}/auth/refresh`,
      null,
      { withCredentials: true },
    )
    if (!data.success || !data.data?.accessToken) return false
    useAuthStore.getState().setSession(data.data.accessToken, data.data.user)
    return true
  } catch {
    useAuthStore.getState().clearSession()
    return false
  }
}

/**
 * Single-flight refresh: parallel 401s share one in-flight call, so the refresh
 * cookie is rotated exactly once. Falls back to `/login` if the session is gone.
 */
export function refreshSession(): Promise<boolean> {
  refreshPromise ??= performRefresh().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

export function bootstrapSession(): Promise<boolean> {
  return refreshSession()
}

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const config = error.config as RetryableConfig | undefined
    const status = error.response?.status
    const isAuthCall = config?.url?.startsWith('/auth/')

    if (status === 401 && config && !config._retried && !isAuthCall) {
      config._retried = true
      const ok = await refreshSession()
      if (ok) {
        const token = useAuthStore.getState().accessToken
        if (token) config.headers.Authorization = `Bearer ${token}`
        return api(config)
      }
      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }

    return Promise.reject(error)
  },
)

export function normalizeError(error: unknown): ApiResponse<null> {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data
    if (data && typeof data === 'object' && 'message' in data && typeof data.message === 'string') {
      return {
        success: false,
        message: data.message,
        data: null,
        errors: null,
        status: error.response?.status,
      }
    }
    return {
      success: false,
      message: error.message || 'Network error',
      data: null,
      errors: null,
      status: error.response?.status,
    }
  }
  if (error instanceof Error) {
    return { success: false, message: error.message, data: null, errors: null }
  }
  return { success: false, message: 'Unknown error', data: null, errors: null }
}
