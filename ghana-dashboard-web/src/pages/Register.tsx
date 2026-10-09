import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { registerShop } from '@/api/auth'
import { normalizeError } from '@/api/axios'
import { AuthShell } from '@/components/auth/AuthShell'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { useLanguage } from '@/i18n'

export function RegisterPage() {
  const { t } = useLanguage()
  const navigate = useNavigate()

  const [shopName, setShopName] = useState('')
  const [ownerName, setOwnerName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!shopName || !ownerName || !phone || !password) {
      setError(t('auth.errors.required'))
      return
    }
    if (password.length < 8) {
      setError(t('auth.errors.weakPassword'))
      return
    }
    if (password !== confirm) {
      setError(t('auth.errors.passwordMismatch'))
      return
    }
    setLoading(true)
    setError(null)
    try {
      await registerShop({
        shopName,
        ownerName,
        phone,
        email: email.trim() || undefined,
        password,
      })
      window.localStorage.setItem('ghana.rememberedPhone', phone)
      toast.success(t('auth.registered'))
      navigate(`/login?phone=${encodeURIComponent(phone)}`)
    } catch (err) {
      setError(normalizeError(err).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell
      title={t('auth.registerTitle')}
      subtitle={t('auth.registerSubtitle')}
      footer={
        <>
          {t('auth.haveAccount')}{' '}
          <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-500">
            {t('auth.signInHere')}
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <Input
          label={t('auth.shopName')}
          placeholder={t('auth.shopNamePh')}
          value={shopName}
          onChange={(e) => setShopName(e.target.value)}
          autoComplete="organization"
        />
        <Input
          label={t('auth.ownerName')}
          placeholder={t('auth.ownerNamePh')}
          value={ownerName}
          onChange={(e) => setOwnerName(e.target.value)}
          autoComplete="name"
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label={t('auth.phone')}
            placeholder={t('auth.phonePh')}
            inputMode="numeric"
            value={phone}
            onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
            autoComplete="tel"
          />
          <Input
            label={t('auth.email')}
            placeholder={t('auth.emailPh')}
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
          />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label={t('auth.newPassword')}
            type="password"
            placeholder={t('auth.passwordPh')}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
          />
          <Input
            label={t('auth.confirmPassword')}
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
          />
        </div>

        {error && <p className="rounded-card bg-crimson-50 px-3 py-2 text-sm text-crimson-700 dark:bg-crimson-500/10">{error}</p>}

        <Button type="submit" size="lg" disabled={loading} className="mt-1">
          {loading ? t('auth.creatingShop') : t('auth.createShop')}
        </Button>
      </form>
    </AuthShell>
  )
}
