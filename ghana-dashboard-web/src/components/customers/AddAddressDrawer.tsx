import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { createCustomerAddress } from '@/api/customers'
import type { AddressType, CustomerAddressInput } from '@/api/customers'
import { normalizeError } from '@/api/axios'
import { Button, Input, Select, Sheet } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'

interface AddAddressDrawerProps {
  open: boolean
  onClose: () => void
  customerId: number
  customerName: string
}

interface FormState {
  addressType: AddressType
  addressLine1: string
  addressLine2: string
  city: string
  state: string
  pincode: string
  landmark: string
  isDefault: boolean
}

const EMPTY: FormState = {
  addressType: 'HOME',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  pincode: '',
  landmark: '',
  isDefault: false,
}

export function AddAddressDrawer({
  open,
  onClose,
  customerId,
  customerName,
}: AddAddressDrawerProps) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(EMPTY)
  const [touched, setTouched] = useState(false)

  // Clear the draft on every open so a cancelled address never leaks into the next one.
  const [lastOpen, setLastOpen] = useState(open)
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) {
      setForm(EMPTY)
      setTouched(false)
    }
  }

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  // CustomerAddressRequest validates these server-side with @NotBlank, so mirror it here.
  const errors = {
    addressLine1: form.addressLine1.trim() ? '' : t('customers.address.errors.line1'),
    city: form.city.trim() ? '' : t('customers.address.errors.city'),
    state: form.state.trim() ? '' : t('customers.address.errors.state'),
    pincode:
      form.pincode.trim().length >= 6 && form.pincode.trim().length <= 10
        ? ''
        : t('customers.address.errors.pincode'),
  }
  const valid = Object.values(errors).every((message) => !message)

  const mutation = useMutation({
    mutationFn: (payload: CustomerAddressInput) => createCustomerAddress(customerId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] })
      toast.success(t('customers.address.saved'))
      setForm(EMPTY)
      setTouched(false)
      onClose()
    },
    onError: (error) =>
      toast.error(normalizeError(error).message || t('customers.address.saveFailed')),
  })

  const submit = () => {
    setTouched(true)
    if (!valid) return
    mutation.mutate({
      customerId,
      addressType: form.addressType,
      addressLine1: form.addressLine1.trim(),
      addressLine2: form.addressLine2.trim() || null,
      city: form.city.trim(),
      state: form.state.trim(),
      pincode: form.pincode.trim(),
      landmark: form.landmark.trim() || null,
      isDefault: form.isDefault,
    })
  }

  const error = (key: keyof typeof errors) => (touched ? errors[key] : undefined)

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={t('customers.address.title')}
      subtitle={customerName}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={mutation.isPending}>
            {mutation.isPending ? t('customers.address.saving') : t('customers.address.submit')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 p-5">
        <Select
          label={t('customers.address.type')}
          value={form.addressType}
          onChange={(value) => set('addressType', value as AddressType)}
          options={[
            { value: 'HOME', label: t('customers.address.type.HOME') },
            { value: 'OFFICE', label: t('customers.address.type.OFFICE') },
            { value: 'OTHER', label: t('customers.address.type.OTHER') },
          ]}
        />

        <Input
          label={t('customers.address.line1')}
          value={form.addressLine1}
          onChange={(e) => set('addressLine1', e.target.value)}
          error={error('addressLine1')}
          maxLength={200}
        />
        <Input
          label={t('customers.address.line2')}
          value={form.addressLine2}
          onChange={(e) => set('addressLine2', e.target.value)}
          maxLength={200}
        />

        <div className="grid grid-cols-2 gap-3">
          <Input
            label={t('customers.address.city')}
            value={form.city}
            onChange={(e) => set('city', e.target.value)}
            error={error('city')}
            maxLength={50}
          />
          <Input
            label={t('customers.address.state')}
            value={form.state}
            onChange={(e) => set('state', e.target.value)}
            error={error('state')}
            maxLength={50}
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Input
            label={t('customers.address.pincode')}
            value={form.pincode}
            onChange={(e) => set('pincode', e.target.value.replace(/\D/g, '').slice(0, 10))}
            error={error('pincode')}
            inputMode="numeric"
            placeholder="380015"
          />
          <Input
            label={t('customers.address.landmark')}
            value={form.landmark}
            onChange={(e) => set('landmark', e.target.value)}
            maxLength={100}
          />
        </div>

        <button
          type="button"
          onClick={() => set('isDefault', !form.isDefault)}
          className="flex items-center gap-3 rounded-card border border-line bg-surface px-3.5 py-3 text-left"
        >
          <span
            className={cn(
              'flex size-5 shrink-0 items-center justify-center rounded border transition-colors',
              form.isDefault ? 'border-brand-600 bg-brand-600' : 'border-line-strong',
            )}
          >
            {form.isDefault && (
              <svg viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" className="size-3">
                <path d="M20 6 9 17l-5-5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            )}
          </span>
          <span className="text-sm font-medium text-ink">{t('customers.address.isDefault')}</span>
        </button>
      </div>
    </Sheet>
  )
}
