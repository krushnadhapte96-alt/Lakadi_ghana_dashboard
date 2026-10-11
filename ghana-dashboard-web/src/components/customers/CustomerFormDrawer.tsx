import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { updateCustomer } from '@/api/customers'
import type { CustomerDetail, CustomerProfileInput } from '@/api/customers'
import { normalizeError } from '@/api/axios'
import { Button, Input, Select, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'

interface CustomerFormDrawerProps {
  open: boolean
  onClose: () => void
  customer: CustomerDetail
}

interface FormState {
  name: string
  phone: string
  email: string
  gstNo: string
  tags: string
  preferredLanguage: 'en' | 'hi' | 'mr'
}

function seed(customer: CustomerDetail): FormState {
  return {
    name: customer.name,
    phone: customer.phone ?? '',
    email: customer.email ?? '',
    gstNo: customer.gstNo ?? '',
    tags: customer.tags.join(', '),
    preferredLanguage: customer.preferredLanguage,
  }
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function CustomerFormDrawer({ open, onClose, customer }: CustomerFormDrawerProps) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(() => seed(customer))
  const [touched, setTouched] = useState(false)

  // Reseed on the open edge so the drawer never shows the previous customer.
  const [lastOpen, setLastOpen] = useState(open)
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) {
      setForm(seed(customer))
      setTouched(false)
    }
  }

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const errors = {
    name: form.name.trim() ? '' : t('customers.errors.name'),
    // ProfileRequest marks phone @NotBlank, unlike the nullable list DTO.
    phone: /^\d{6,15}$/.test(form.phone.replace(/\D/g, ''))
      ? ''
      : t('customers.errors.phone'),
    email: !form.email.trim() || EMAIL_RE.test(form.email.trim()) ? '' : t('customers.errors.email'),
  }
  const valid = Object.values(errors).every((message) => !message)

  const mutation = useMutation({
    mutationFn: (payload: CustomerProfileInput) => updateCustomer(customer.id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customer.id] })
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      toast.success(t('customers.form.saved'))
      setTouched(false)
      onClose()
    },
    onError: (error) =>
      toast.error(normalizeError(error).message || t('customers.form.saveFailed')),
  })

  const submit = () => {
    setTouched(true)
    if (!valid) return
    const tags = form.tags
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean)
    mutation.mutate({
      name: form.name.trim(),
      phone: form.phone.trim(),
      email: form.email.trim() || null,
      gstNo: form.gstNo.trim() || null,
      tags,
      preferredLanguage: form.preferredLanguage,
    })
  }

  const error = (key: keyof typeof errors) => (touched ? errors[key] : undefined)

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={t('customers.form.title')}
      subtitle={customer.name}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={mutation.isPending}>
            {mutation.isPending ? t('customers.form.saving') : t('customers.form.submit')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 p-5">
        <Input
          label={t('customers.form.name')}
          value={form.name}
          onChange={(e) => set('name', e.target.value)}
          error={error('name')}
          maxLength={150}
        />
        <Input
          label={t('customers.form.phone')}
          value={form.phone}
          onChange={(e) => set('phone', e.target.value)}
          error={error('phone')}
          inputMode="tel"
          maxLength={20}
          hint={t('customers.form.phoneHint')}
        />
        <Input
          label={t('customers.form.email')}
          type="email"
          value={form.email}
          onChange={(e) => set('email', e.target.value)}
          error={error('email')}
          maxLength={100}
        />
        <Input
          label={t('customers.form.gst')}
          value={form.gstNo}
          onChange={(e) => set('gstNo', e.target.value.toUpperCase())}
          maxLength={50}
        />

        <Textarea
          label={t('customers.form.tags')}
          value={form.tags}
          onChange={(e) => set('tags', e.target.value)}
          rows={2}
          hint={t('customers.form.tagsHint')}
        />

        <Select
          label={t('customers.form.language')}
          value={form.preferredLanguage}
          onChange={(value) => set('preferredLanguage', value as 'en' | 'hi' | 'mr')}
          options={[
            { value: 'en', label: t('customers.form.language.en') },
            { value: 'hi', label: t('customers.form.language.hi') },
            { value: 'mr', label: t('customers.form.language.mr') },
          ]}
          hint={t('customers.form.languageHint')}
        />
      </div>
    </Sheet>
  )
}
