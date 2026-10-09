import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchStaff } from '@/api/settings'
import type { StaffMember } from '@/api/settings'
import { SaveBar, Section } from '@/components/settings/Section'
import { useSettingsMutations } from '@/components/settings/useSettingsMutations'
import {
  Badge,
  Button,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Skeleton,
} from '@/components/ui'
import { useLanguage } from '@/i18n'
import { Icon } from '@/utils/icons'

type Sheet = { kind: 'create' } | { kind: 'edit'; member: StaffMember } | { kind: 'password'; member: StaffMember } | null

export function StaffTab() {
  const { t } = useLanguage()
  const staff = useQuery({ queryKey: ['settings', 'users'], queryFn: fetchStaff })
  const { addStaff, editStaff, toggleStaff, resetPassword } = useSettingsMutations()
  const [sheet, setSheet] = useState<Sheet>(null)
  const [confirm, setConfirm] = useState<StaffMember | null>(null)

  if (staff.isPending) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    )
  }

  if (staff.isError) {
    return (
      <ErrorState
        title={t('settings.staff.loadFailed')}
        message={t('settings.staff.loadFailedBody')}
        retryLabel={t('common.retry')}
        onRetry={() => void staff.refetch()}
      />
    )
  }

  const members = staff.data ?? []
  const activeCount = members.filter((m) => m.active).length

  return (
    <Section
      title={t('settings.staff.title')}
      description={t('settings.staff.description', { count: activeCount })}
      actions={
        <Button onClick={() => setSheet({ kind: 'create' })}>
          <Icon name="plus" className="size-4" />
          {t('settings.staff.add')}
        </Button>
      }
    >
      {members.length === 0 ? (
        <EmptyState
          title={t('settings.staff.emptyTitle')}
          description={t('settings.staff.emptyBody')}
        />
      ) : (
        <ul className="divide-y divide-line">
          {members.map((member) => (
            <li key={member.id} className="flex flex-wrap items-center gap-3 py-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-pill bg-surface-3 text-sm font-semibold text-ink-soft">
                {member.name.trim().charAt(0).toUpperCase()}
              </div>
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 text-sm font-medium text-ink">
                  {member.name}
                  {member.role === 'OWNER' && <Badge variant="warning">{t('settings.staff.owner')}</Badge>}
                  {!member.active && <Badge variant="secondary">{t('settings.staff.inactive')}</Badge>}
                </p>
                <p className="truncate text-xs text-ink-soft">
                  {member.phone}
                  {member.email ? ` · ${member.email}` : ''}
                </p>
                <p className="text-xs text-ink-soft/70">
                  {member.lastLoginAt
                    ? t('settings.staff.lastLogin', { date: new Date(member.lastLoginAt).toLocaleString('en-IN') })
                    : t('settings.staff.neverLoggedIn')}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setSheet({ kind: 'edit', member })}
                >
                  <Icon name="pencil" className="size-4" />
                  {t('common.edit')}
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setSheet({ kind: 'password', member })}
                >
                  <Icon name="password" className="size-4" />
                  {t('settings.staff.resetPassword')}
                </Button>
                <Button
                  variant={member.active ? 'ghost' : 'secondary'}
                  size="sm"
                  onClick={() =>
                    member.active ? setConfirm(member) : toggleStaff.mutate({ id: member.id, active: true })
                  }
                >
                  {member.active ? t('settings.staff.deactivate') : t('settings.staff.reactivate')}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {sheet?.kind === 'create' && (
        <CreateStaffSheet
          saving={addStaff.isPending}
          onClose={() => setSheet(null)}
          onSubmit={(input) => {
            addStaff.mutate(input, { onSuccess: () => setSheet(null) })
          }}
        />
      )}

      {sheet?.kind === 'edit' && (
        <EditStaffSheet
          member={sheet.member}
          saving={editStaff.isPending}
          onClose={() => setSheet(null)}
          onSubmit={(input) => {
            editStaff.mutate(
              { id: sheet.member.id, input },
              { onSuccess: () => setSheet(null) },
            )
          }}
        />
      )}

      {sheet?.kind === 'password' && (
        <PasswordSheet
          member={sheet.member}
          saving={resetPassword.isPending}
          onClose={() => setSheet(null)}
          onSubmit={(password) => {
            resetPassword.mutate(
              { id: sheet.member.id, password },
              { onSuccess: () => setSheet(null) },
            )
          }}
        />
      )}

      <ConfirmDialog
        isOpen={confirm !== null}
        onClose={() => setConfirm(null)}
        onConfirm={() => {
          if (confirm) toggleStaff.mutate({ id: confirm.id, active: false })
          setConfirm(null)
        }}
        title={t('settings.staff.deactivateTitle')}
        message={t('settings.staff.deactivateMessage', { name: confirm?.name ?? '' })}
        confirmLabel={t('settings.staff.deactivate')}
        confirmVariant="danger"
        loading={toggleStaff.isPending}
      />
    </Section>
  )
}

function CreateStaffSheet({
  saving,
  onClose,
  onSubmit,
}: {
  saving: boolean
  onClose: () => void
  onSubmit: (input: { name: string; phone: string; email: string | null; password: string }) => void
}) {
  const { t } = useLanguage()
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    if (!name.trim()) return setError(t('settings.staff.errors.name'))
    if (!/^[0-9]{7,15}$/.test(phone.trim())) return setError(t('settings.staff.errors.phone'))
    if (password.length < 8) return setError(t('settings.staff.errors.password'))
    setError(null)
    onSubmit({ name: name.trim(), phone: phone.trim(), email: email.trim() || null, password })
  }

  return (
    <Modal isOpen onClose={onClose} title={t('settings.staff.addTitle')}>
      <div className="space-y-4">
        <Input label={t('settings.staff.name')} value={name} onChange={(e) => setName(e.target.value)} />
        <Input
          label={t('settings.staff.phone')}
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          placeholder="9876543210"
          hint={t('settings.staff.phoneHint')}
        />
        <Input
          label={t('settings.staff.email')}
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Input
          label={t('settings.staff.password')}
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          hint={t('settings.staff.passwordHint')}
        />
        <p className="text-xs text-ink-soft">{t('settings.staff.sharePasswordNote')}</p>
        {error && (
          <p role="alert" className="text-sm text-crimson-600">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={saving}>
            {saving ? t('common.saving') : t('settings.staff.add')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

function EditStaffSheet({
  member,
  saving,
  onClose,
  onSubmit,
}: {
  member: StaffMember
  saving: boolean
  onClose: () => void
  onSubmit: (input: { name: string; phone: string; email: string | null }) => void
}) {
  const { t } = useLanguage()
  const [name, setName] = useState(member.name)
  const [phone, setPhone] = useState(member.phone)
  const [email, setEmail] = useState(member.email ?? '')
  const [error, setError] = useState<string | null>(null)

  const dirty = name !== member.name || phone !== member.phone || email !== (member.email ?? '')

  const submit = () => {
    if (!name.trim()) return setError(t('settings.staff.errors.name'))
    if (!/^[0-9]{7,15}$/.test(phone.trim())) return setError(t('settings.staff.errors.phone'))
    setError(null)
    onSubmit({ name: name.trim(), phone: phone.trim(), email: email.trim() || null })
  }

  return (
    <Modal isOpen onClose={onClose} title={t('settings.staff.editTitle', { name: member.name })}>
      <div className="space-y-4">
        <Input label={t('settings.staff.name')} value={name} onChange={(e) => setName(e.target.value)} />
        <Input
          label={t('settings.staff.phone')}
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
        <Input
          label={t('settings.staff.email')}
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        {error && (
          <p role="alert" className="text-sm text-crimson-600">
            {error}
          </p>
        )}
        <SaveBar
          dirty={dirty}
          saving={saving}
          onSave={submit}
          onReset={() => {
            setName(member.name)
            setPhone(member.phone)
            setEmail(member.email ?? '')
          }}
          saveLabel={t('common.save')}
          resetLabel={t('common.reset')}
          savingLabel={t('common.saving')}
        />
      </div>
    </Modal>
  )
}

function PasswordSheet({
  member,
  saving,
  onClose,
  onSubmit,
}: {
  member: StaffMember
  saving: boolean
  onClose: () => void
  onSubmit: (password: string) => void
}) {
  const { t } = useLanguage()
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    if (password.length < 8) return setError(t('settings.staff.errors.password'))
    if (password !== confirmPassword) return setError(t('settings.staff.errors.mismatch'))
    setError(null)
    onSubmit(password)
  }

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={t('settings.staff.passwordTitle', { name: member.name })}
    >
      <div className="space-y-4">
        <p className="text-sm text-ink-soft">{t('settings.staff.passwordBody')}</p>
        <Input
          label={t('settings.staff.newPassword')}
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Input
          label={t('settings.staff.confirmPassword')}
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
        />
        {error && (
          <p role="alert" className="text-sm text-crimson-600">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={saving}>
            {saving ? t('common.saving') : t('settings.staff.resetPassword')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
