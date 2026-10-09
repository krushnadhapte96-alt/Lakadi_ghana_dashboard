import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { PARTY_TYPES, createParty, updateParty } from '@/api/khata'
import type { Party, PartyInput, PartyType } from '@/api/khata'
import { Badge, Button, Input, Select, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { Icon } from '@/utils/icons'

function parseAmount(raw: string): number | null {
  if (raw.trim() === '') return null
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

export function PartyFormDrawer({
  open,
  party,
  onClose,
  onSaved,
}: {
  open: boolean
  /** Present when editing; null means a new party. */
  party: Party | null
  onClose: () => void
  onSaved: (party: Party) => void
}) {
  const { t } = useLanguage()
  const editing = party !== null

  const [name, setName] = useState('')
  const [type, setType] = useState<PartyType>('RETAILER')
  const [phone, setPhone] = useState('')
  const [address, setAddress] = useState('')
  const [creditLimit, setCreditLimit] = useState('')
  const [terms, setTerms] = useState('30')
  const [opening, setOpening] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  // Seed the form from the prop whenever it changes, including the open/close
  // edge, so a closed drawer never shows the previous party's details.
  const [seed, setSeed] = useState<{ open: boolean; id: number | null }>({ open, id: party?.id ?? null })
  if (seed.open !== open || seed.id !== (party?.id ?? null)) {
    setSeed({ open, id: party?.id ?? null })
    if (open) {
      setName(party?.name ?? '')
      setType(party?.type ?? 'RETAILER')
      setPhone(party?.phone ?? '')
      setAddress(party?.address ?? '')
      setCreditLimit(party?.creditLimit === null || party?.creditLimit === undefined ? '' : String(party.creditLimit))
      setTerms(String(party?.paymentTermsDays ?? 30))
      setOpening(String(party?.openingBalance ?? 0))
      setErrors({})
    }
  }

  const save = useMutation({
    mutationFn: (input: PartyInput) =>
      editing ? updateParty(party.id, input) : createParty(input),
    onSuccess: (saved) => {
      toast.success(t(editing ? 'khata.form.saved' : 'khata.form.created'))
      onSaved(saved)
      onClose()
    },
    onError: (error) => {
      const err = normalizeError(error)
      if (err.status === 409) {
        toast.error(err.message || t('khata.form.phoneTaken'))
        return
      }
      toast.error(err.message || t('khata.form.saveFailed'))
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (!name.trim()) found.name = t('khata.errors.name')
    // The API strips formatting but needs at least 7 digits.
    if (phone.replace(/\D/g, '').length < 7) found.phone = t('khata.errors.phone')
    const limit = parseAmount(creditLimit)
    if (creditLimit.trim() !== '' && (limit === null || limit < 0)) {
      found.creditLimit = t('khata.errors.creditLimit')
    }
    const days = Number(terms)
    if (!Number.isInteger(days) || days < 0 || days > 365) {
      found.paymentTermsDays = t('khata.errors.terms')
    }
    const openingBalance = opening.trim() === '' ? 0 : Number(opening)
    if (!Number.isFinite(openingBalance)) {
      found.openingBalance = t('khata.errors.opening')
    }
    setErrors(found)
    if (Object.keys(found).length > 0) return

    save.mutate({
      name: name.trim(),
      type,
      phone: phone.trim(),
      address: address.trim(),
      creditLimit: limit,
      paymentTermsDays: days,
      openingBalance,
    })
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={t(editing ? 'khata.form.editTitle' : 'khata.form.newTitle')}
      subtitle={t(editing ? 'khata.form.editSubtitle' : 'khata.form.newSubtitle')}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={save.isPending} className="sm:flex-1">
            {t('common.cancel')}
          </Button>
          <Button
            type="submit"
            form="khata-party-form"
            disabled={save.isPending}
            className="sm:flex-1"
          >
            <Icon name={save.isPending ? 'loader' : 'check'} className={cn('size-4', save.isPending && 'animate-spin')} />
            {save.isPending ? t('khata.form.saving') : t('khata.form.save')}
          </Button>
        </>
      }
    >
      <form id="khata-party-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        <Input
          label={t('khata.form.name')}
          value={name}
          onChange={(event) => setName(event.target.value)}
          error={errors.name}
          autoComplete="off"
        />

        <Select
          label={t('khata.form.type')}
          options={PARTY_TYPES.map((option) => ({ value: option, label: t(`khata.type.${option}`) }))}
          value={type}
          onChange={(value) => setType(value as PartyType)}
        />

        <Input
          label={t('khata.form.phone')}
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          error={errors.phone}
          inputMode="tel"
          hint={t('khata.form.phoneHint')}
          autoComplete="off"
        />

        <Textarea
          label={t('khata.form.address')}
          value={address}
          onChange={(event) => setAddress(event.target.value)}
          rows={2}
        />

        <div className="grid grid-cols-2 gap-3">
          <Input
            label={t('khata.form.creditLimit')}
            value={creditLimit}
            onChange={(event) => setCreditLimit(event.target.value)}
            error={errors.creditLimit}
            inputMode="decimal"
            hint={t('khata.form.creditLimitHint')}
          />
          <Input
            label={t('khata.form.terms')}
            value={terms}
            onChange={(event) => setTerms(event.target.value)}
            error={errors.paymentTermsDays}
            inputMode="numeric"
            hint={t('khata.form.termsHint')}
          />
        </div>

        <Input
          label={t('khata.form.openingBalance')}
          value={opening}
          onChange={(event) => setOpening(event.target.value)}
          error={errors.openingBalance}
          inputMode="decimal"
          hint={t('khata.form.openingHint')}
        />

        {creditLimit.trim() !== '' && (
          <p className="rounded-card bg-surface-3 px-3 py-2.5 text-xs text-ink-soft">
            <Badge variant="info" size="sm" className="mr-2">
              {t('khata.type.creditLimitOn')}
            </Badge>
            {t('khata.form.creditLimitNote')}
          </p>
        )}
      </form>
    </Sheet>
  )
}
