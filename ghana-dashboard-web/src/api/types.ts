export interface ApiErrors {
  [field: string]: string[]
}

export interface ApiResponse<T = unknown> {
  success: boolean
  message: string
  data: T
  errors?: ApiErrors | null
  status?: number
  timestamp?: string
}

export interface AuthUser {
  id: number
  shopId: number
  shopName: string | null
  name: string
  phone: string
  email: string | null
  role: 'OWNER' | 'STAFF'
}

export interface TokenResponse {
  accessToken: string
  expiresInSeconds: number
  user: AuthUser
}

export interface UserSummary {
  id: number
  name: string
  phone: string
  email: string | null
  role: 'OWNER' | 'STAFF'
  active: boolean
  lastLoginAt: string | null
}
