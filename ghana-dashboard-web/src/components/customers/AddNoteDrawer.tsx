import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, normalizeError } from '@/api/axios'
import type { ApiResponse } from '@/api/types'
import type { CustomerNote } from '@/api/customers'
import { Button, Sheet, Textarea } from '@/components/ui'
import { useLanguage } from '@/i18n'

interface AddNoteDrawerProps {
  open: boolean
  onClose: () => void
  customerId: number
  customerName: string
}

/** Notes are append-only: CustomerInsightService.addNote has no update or delete. */
export function AddNoteDrawer({ open, onClose, customerId, customerName }: AddNoteDrawerProps) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')

  const mutation = useMutation({
    mutationFn: (body: string) =>
      api
        .post<ApiResponse<CustomerNote>>(`/customers/${customerId}/notes`, { note: body })
        .then((res) => res.data.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] })
      toast.success(t('customers.note.saved'))
      setNote('')
      onClose()
    },
    onError: (error) => toast.error(normalizeError(error).message || t('customers.note.saveFailed')),
  })

  const trimmed = note.trim()
  const tooLong = trimmed.length > 1000
  const canSubmit = trimmed.length > 0 && !tooLong && !mutation.isPending

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={t('customers.note.title')}
      subtitle={customerName}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button
            onClick={() => mutation.mutate(trimmed)}
            disabled={!canSubmit}
          >
            {mutation.isPending ? t('customers.note.saving') : t('customers.note.submit')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 p-5">
        <Textarea
          label={t('customers.note.label')}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={5}
          maxLength={1000}
          placeholder={t('customers.note.placeholder')}
          error={tooLong ? t('customers.note.tooLong') : undefined}
          hint={t('customers.note.hint', { count: trimmed.length })}
          autoFocus
        />
      </div>
    </Sheet>
  )
}
