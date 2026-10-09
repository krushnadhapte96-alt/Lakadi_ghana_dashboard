import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createLicence,
  deleteLicence,
  renewLicence,
  updateLicence,
  uploadLicenceDocument,
} from '@/api/licences'
import type { Licence, LicenceInput, RenewalInput } from '@/api/licences'

const KEYS = {
  list: ['licences'] as const,
  summary: ['licences', 'summary'] as const,
  renewals: (id: number) => ['licences', id, 'renewals'] as const,
}

/** Invalidates the list, the summary strip, and one licence's renewal history. */
function useInvalidate() {
  const queryClient = useQueryClient()
  return (licenceId?: number) => {
    void queryClient.invalidateQueries({ queryKey: KEYS.list })
    if (licenceId !== undefined) {
      void queryClient.invalidateQueries({ queryKey: KEYS.renewals(licenceId) })
    }
  }
}

export function useLicenceMutations() {
  const invalidate = useInvalidate()

  const create = useMutation({
    mutationFn: ({ input, file }: { input: LicenceInput; file: File | null }) =>
      createLicence(input, file),
    onSuccess: () => invalidate(),
  })

  const update = useMutation({
    mutationFn: ({ id, input }: { id: number; input: LicenceInput }) => updateLicence(id, input),
    onSuccess: (licence: Licence) => invalidate(licence.id),
  })

  const remove = useMutation({
    mutationFn: (id: number) => deleteLicence(id),
    onSuccess: () => invalidate(),
  })

  const uploadDocument = useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) => uploadLicenceDocument(id, file),
    onSuccess: (licence: Licence) => invalidate(licence.id),
  })

  const renew = useMutation({
    mutationFn: ({ id, input, file }: { id: number; input: RenewalInput; file: File | null }) =>
      renewLicence(id, input, file),
    // Renewing resets the reminder ladder server-side and appends history,
    // so both the list and the history query are stale.
    onSuccess: (licence: Licence) => invalidate(licence.id),
  })

  return { create, update, remove, uploadDocument, renew }
}
