import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { bootstrapSession } from '@/api/axios'
import { useAuthStore } from '@/stores/auth'
import type { Permission } from '@/auth/permissions'
import { useCan } from '@/auth/useCan'
import { cn } from '@/utils/cn'

function FullScreenLoader() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="shimmer-surface h-24 w-24 animate-shimmer rounded-card border border-line" />
    </div>
  )
}

/** Boots the session (refresh cookie -> access token) once on app load. */
export function AuthProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    // refreshSession() already sets/clears the store on success/failure.
    void bootstrapSession()
  }, [])

  return <>{children}</>
}

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const status = useAuthStore((s) => s.status)
  const location = useLocation()

  if (status === 'loading') return <FullScreenLoader />
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <>{children}</>
}

export function RoleGuard({
  permission,
  fallback = null,
  children,
}: {
  permission: Permission
  fallback?: ReactNode
  children: ReactNode
}) {
  const allowed = useCan(permission)
  return allowed ? <>{children}</> : <>{fallback}</>
}

export function RoleBadge({ className }: { className?: string }) {
  const user = useAuthStore((s) => s.user)
  if (!user) return null
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-pill px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide',
        user.role === 'OWNER' ? 'bg-gold-400/20 text-gold-700 dark:text-gold-300' : 'bg-surface-3 text-ink-soft',
        className,
      )}
    >
      {user.role === 'OWNER' ? 'Owner' : 'Staff'}
    </span>
  )
}
