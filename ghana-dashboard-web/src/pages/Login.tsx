import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { login } from '@/api/auth'
import { AuthShell } from '@/components/auth/AuthShell'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { useLanguage } from '@/i18n'
import { normalizeError } from '@/api/axios'

const PHONE_MEMORY_KEY = 'ghana.rememberedPhone'

export function LoginPage() {
  const { t } = useLanguage()
  const navigate = useNavigate()
  const location = useLocation()

  const [identifier, setIdentifier] = useState(() => {
    const params = new URLSearchParams(window.location.search)
    return params.get('phone') ?? window.localStorage.getItem(PHONE_MEMORY_KEY) ?? ''
  })
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(() => Boolean(window.localStorage.getItem(PHONE_MEMORY_KEY)))
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const from = (location.state as { from?: string } | null)?.from ?? '/'

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!identifier || !password) {
      setError(t('auth.errors.required'))
      return
    }
    setLoading(true)
    setError(null)
    try {
      await login(identifier, password)
      if (remember) {
        window.localStorage.setItem(PHONE_MEMORY_KEY, identifier)
      } else {
        window.localStorage.removeItem(PHONE_MEMORY_KEY)
      }
      toast.success(t('auth.loggedIn'))
      navigate(from, { replace: true })
    } catch (err) {
      setError(normalizeError(err).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell
      title={t('auth.loginTitle')}
      subtitle={t('auth.loginSubtitle')}
      footer={
        <>
          {t('auth.noAccount')}{' '}
          <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-500">
            {t('auth.createOne')}
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <Input
          label={t('auth.phoneOrEmail')}
          placeholder={t('auth.phoneOrEmailPh')}
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          autoComplete="username"
        />
        <Input
          label={t('auth.password')}
          type="password"
          placeholder={t('auth.passwordPh')}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />

        <label className="flex cursor-pointer items-center gap-2 text-sm text-ink-soft">
          <input
            type="checkbox"
            checked={remember}
            onChange={(e) => setRemember(e.target.checked)}
            className="size-4 accent-brand-600"
          />
          {t('auth.rememberPhone')}
        </label>

        {error && <p className="rounded-card bg-crimson-50 px-3 py-2 text-sm text-crimson-700 dark:bg-crimson-500/10">{error}</p>}

        <Button type="submit" size="lg" disabled={loading} className="mt-1">
          {loading ? t('auth.signingIn') : t('auth.signIn')}
        </Button>
      </form>
    </AuthShell>
  )
}
