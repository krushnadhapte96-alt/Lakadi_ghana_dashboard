import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { changePassword } from '@/api/auth'
import { AuthShell } from '@/components/auth/AuthShell'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { useLanguage } from '@/i18n'
import { normalizeError } from '@/api/axios'

export function ChangePasswordPage() {
  const { t } = useLanguage()
  const navigate = useNavigate()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!currentPassword || !newPassword) {
      setError(t('auth.errors.required'))
      return
    }
    if (newPassword.length < 8) {
      setError(t('auth.errors.weakPassword'))
      return
    }
    if (newPassword !== confirm) {
      setError(t('auth.errors.passwordMismatch'))
      return
    }
    setLoading(true)
    setError(null)
    try {
      await changePassword(currentPassword, newPassword)
      toast.success(t('auth.passwordChanged'))
      navigate('/')
    } catch (err) {
      setError(normalizeError(err).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell
      title={t('auth.changePasswordTitle')}
      subtitle={t('auth.changePasswordSubtitle')}
      footer={null}
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <Input
          label={t('auth.currentPassword')}
          type="password"
          placeholder={t('auth.currentPasswordPh')}
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          autoComplete="current-password"
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label={t('auth.newPassword')}
            type="password"
            placeholder={t('auth.newPasswordPh')}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            autoComplete="new-password"
          />
          <Input
            label={t('auth.confirmPassword')}
            type="password"
            placeholder={t('auth.confirmPasswordPh')}
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
          />
        </div>

        {error && <p className="rounded-card bg-crimson-50 px-3 py-2 text-sm text-crimson-700 dark:bg-crimson-500/10">{error}</p>}

        <div className="flex gap-3 mt-1">
          <Button type="button" variant="outline" disabled={loading} onClick={() => navigate(-1)}>
            {t('auth.cancel')}
          </Button>
          <Button type="submit" size="lg" disabled={loading} className="flex-1">
            {loading ? t('auth.changingPassword') : t('auth.changePassword')}
          </Button>
        </div>
      </form>
    </AuthShell>
  )
}

