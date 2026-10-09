import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { downloadExport, fetchExportManifest } from '@/api/settings'
import { Section } from '@/components/settings/Section'
import { Button, ErrorState, Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { toast } from 'sonner'
import { normalizeError } from '@/api/axios'
import { Icon } from '@/utils/icons'

export function DataExportTab() {
  const { t } = useLanguage()
  const manifest = useQuery({ queryKey: ['settings', 'export'], queryFn: fetchExportManifest })
  const [downloading, setDownloading] = useState(false)

  const download = async () => {
    setDownloading(true)
    try {
      await downloadExport()
      toast.success(t('settings.export.downloaded'))
    } catch (error) {
      toast.error(normalizeError(error).message || t('settings.export.downloadFailed'))
    } finally {
      setDownloading(false)
    }
  }

  if (manifest.isPending) {
    return <Skeleton className="h-80 w-full" />
  }

  if (manifest.isError) {
    return (
      <ErrorState
        title={t('settings.export.loadFailed')}
        message={t('settings.export.loadFailedBody')}
        retryLabel={t('common.retry')}
        onRetry={() => void manifest.refetch()}
      />
    )
  }

  const entries = manifest.data?.entries ?? []
  const totalRows = entries.reduce((sum, entry) => sum + entry.rows, 0)

  return (
    <Section
      title={t('settings.export.title')}
      description={t('settings.export.description')}
      actions={
        <Button onClick={() => void download()} disabled={downloading}>
          <Icon name="download" className="size-4" />
          {downloading ? t('settings.export.downloading') : t('settings.export.download')}
        </Button>
      }
      note={
        <p className="rounded-card border border-line bg-surface-3 px-3 py-2 text-xs text-ink-soft">
          {t('settings.export.warning')}
        </p>
      }
    >
      <p className="mb-3 text-sm text-ink-soft">
        {t('settings.export.summary', {
          datasets: entries.length,
          rows: totalRows,
          date: manifest.data?.generatedAt
            ? new Date(manifest.data.generatedAt).toLocaleString('en-IN')
            : t('common.unknown'),
        })}
      </p>
      <div className="-mx-4 overflow-x-auto sm:-mx-5">
        <table className="w-full min-w-[26rem] border-collapse text-sm">
          <thead>
            <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-soft">
              <th className="px-4 py-2 font-medium sm:px-5">{t('settings.export.dataset')}</th>
              <th className="px-4 py-2 text-right font-medium sm:px-5">{t('settings.export.rows')}</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.key} className="border-b border-line/60 last:border-0">
                <td className="px-4 py-2.5 text-ink sm:px-5">{entry.label}</td>
                <td className="px-4 py-2.5 text-right tabular-nums text-ink-soft sm:px-5">
                  {entry.rows}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-3 text-xs text-ink-soft">{t('settings.export.emptyNote')}</p>
    </Section>
  )
}
