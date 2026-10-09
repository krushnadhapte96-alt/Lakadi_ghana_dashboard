import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { downloadLicenceDocument, fetchLicenceDocumentBlob } from '@/api/licences'
import type { Licence } from '@/api/licences'
import { Button, Modal, Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { Icon } from '@/utils/icons'

interface DocumentPreviewProps {
  open: boolean
  onClose: () => void
  licence: Licence | null
}

const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

interface Resolved {
  /** Licence the url belongs to, so a stale blob is never shown for a new one. */
  key: string
  url: string | null
  failed: boolean
}

/**
 * Renders the stored scan inline. Files sit behind the JWT filter, so the blob is
 * fetched first and shown from an object URL; the object URL is revoked whenever
 * the preview changes or closes so repeated previews do not leak.
 */
export function DocumentPreview({ open, onClose, licence }: DocumentPreviewProps) {
  const { t } = useLanguage()
  const [resolved, setResolved] = useState<Resolved>({ key: '', url: null, failed: false })

  // Empty key when closed, so the reset is derived rather than set inside the effect.
  const key = open && licence ? String(licence.id) : ''

  useEffect(() => {
    if (!key) return
    let objectUrl: string | null = null
    let cancelled = false

    fetchLicenceDocumentBlob(Number(key))
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setResolved({ key, url: objectUrl, failed: false })
      })
      .catch(() => {
        if (!cancelled) setResolved({ key, url: null, failed: true })
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [key])

  // Anything resolved for a different licence (or from a previous open) is stale.
  const current = resolved.key === key ? resolved : { key, url: null, failed: false }

  const save = async () => {
    if (!licence) return
    try {
      await downloadLicenceDocument(licence.id, licence.documentName ?? 'licence.pdf')
    } catch (error) {
      toast.error(normalizeError(error).message || t('licences.preview.downloadFailed'))
    }
  }

  const isImage = licence?.documentType ? IMAGE_TYPES.includes(licence.documentType) : false

  return (
    <Modal isOpen={open} onClose={onClose} title={licence?.documentName ?? t('licences.preview.title')} size="lg">
      <div className="space-y-4">
        {current.failed ? (
          <p role="alert" className="text-sm text-crimson-600">
            {t('licences.preview.failed')}
          </p>
        ) : current.url ? (
          isImage ? (
            <img
              src={current.url}
              alt={licence?.documentName ?? ''}
              className="max-h-[60vh] w-full rounded-card object-contain"
            />
          ) : (
            <object
              data={current.url}
              type={licence?.documentType ?? 'application/pdf'}
              className="h-[60vh] w-full rounded-card"
            >
              <p className="p-4 text-sm text-ink-soft">{t('licences.preview.noViewer')}</p>
            </object>
          )
        ) : (
          <Skeleton className="h-[50vh] w-full" />
        )}

        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>
            {t('common.close')}
          </Button>
          <Button onClick={() => void save()} disabled={!current.url}>
            <Icon name="download" className="size-4" />
            {t('licences.preview.download')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
