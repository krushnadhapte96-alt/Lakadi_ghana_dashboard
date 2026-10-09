import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { createSupplier, serverFieldErrors, updateSupplier } from '@/api/beej'
import type { Supplier, SupplierInput } from '@/api/beej'
import { Button, Input, Modal } from '@/components/ui'
import { useLanguage } from '@/i18n'

const initial: SupplierInput = { name: '', phone: '', address: '', gstin: '' }

function toInput(supplier: Supplier | null): SupplierInput {
  if (!supplier) return initial
  return {
    name: supplier.name,
    phone: supplier.phone ?? '',
    address: supplier.address ?? '',
    gstin: supplier.gstin ?? '',
  }
}

export function SupplierModal({
  open,
  supplier,
  onClose,
}: {
  open: boolean
  supplier: Supplier | null
  onClose: () => void
}) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<SupplierInput>(() => toInput(supplier))
  const [errors, setErrors] = useState<Record<string, string>>({})

  // Reset the form when the modal opens or the supplier changes (adjust state during render).
  const [seen, setSeen] = useState({ open, supplier })
  if (seen.open !== open || seen.supplier !== supplier) {
    setSeen({ open, supplier })
    if (open) {
      setForm(toInput(supplier))
      setErrors({})
    }
  }

  const mutation = useMutation({
    mutationFn: () => (supplier ? updateSupplier(supplier.id, form) : createSupplier(form)),
    onSuccess: async () => {
      toast.success(t(supplier ? 'beej.suppliers.updated' : 'beej.suppliers.created'))
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] })
      onClose()
    },
    onError: (error) => {
      setErrors(serverFieldErrors(error))
      toast.error(normalizeError(error).message || t('beej.suppliers.saveFailed'))
    },
  })

  function set<K extends keyof SupplierInput>(key: K, value: SupplierInput[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!form.name.trim()) {
      setErrors({ name: t('beej.suppliers.errors.name') })
      return
    }
    mutation.mutate()
  }

  return (
    <Modal
      isOpen={open}
      onClose={onClose}
      title={t(supplier ? 'beej.suppliers.edit' : 'beej.suppliers.add')}
      size="sm"
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <Input
          label={t('beej.suppliers.form.name')}
          value={form.name}
          error={errors.name}
          onChange={(e) => set('name', e.target.value)}
        />
        <Input
          label={t('beej.suppliers.form.phone')}
          value={form.phone}
          error={errors.phone}
          onChange={(e) => set('phone', e.target.value)}
        />
        <Input
          label={t('beej.suppliers.form.address')}
          value={form.address}
          error={errors.address}
          onChange={(e) => set('address', e.target.value)}
        />
        <Input
          label={t('beej.suppliers.form.gstin')}
          value={form.gstin}
          error={errors.gstin}
          onChange={(e) => set('gstin', e.target.value)}
        />

        <div className="flex justify-end gap-3 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? t('beej.suppliers.form.saving') : t('beej.suppliers.form.save')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

