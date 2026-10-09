import { useQuery } from '@tanstack/react-query'
import { fetchSupplierLedger } from '@/api/beej'
import { EmptyState, ErrorState, Modal, Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatDate } from '@/utils/format'
import { Icon } from '@/utils/icons'

export function SupplierLedgerModal({
  open,
  supplierId,
  supplierName,
  onClose,
}: {
  open: boolean
  supplierId: number | null
  supplierName: string
  onClose: () => void
}) {
  const { t, language } = useLanguage()

  const ledger = useQuery({
    queryKey: ['suppliers', supplierId, 'ledger'],
    queryFn: () => fetchSupplierLedger(supplierId as number),
    enabled: open && supplierId !== null,
  })

  return (
    <Modal isOpen={open} onClose={onClose} title={t('beej.ledger.title', { name: supplierName })} size="lg">
      {ledger.isError ? (
        <ErrorState
          title={t('beej.ledger.error.title')}
          retryLabel={t('common.retry')}
          onRetry={() => void ledger.refetch()}
        />
      ) : !ledger.data ? (
        <Skeleton className="h-52" />
      ) : (
        <div>
          <div className="mb-4 grid grid-cols-3 gap-3 text-center">
            <div className="rounded-card border border-line bg-surface-3 p-3">
              <p className="text-xs text-ink-soft">{t('beej.ledger.totalPurchases')}</p>
              <p className="text-lg font-bold tabular-nums text-ink">
                {formatCurrency(ledger.data.totalPurchases, { decimals: 2 })}
              </p>
            </div>
            <div className="rounded-card border border-line bg-surface-3 p-3">
              <p className="text-xs text-ink-soft">{t('beej.ledger.totalPaid')}</p>
              <p className="text-lg font-bold tabular-nums text-ink">
                {formatCurrency(ledger.data.totalPaid, { decimals: 2 })}
              </p>
            </div>
            <div className="rounded-card border border-line bg-surface-3 p-3">
              <p className="text-xs text-ink-soft">{t('beej.ledger.balance')}</p>
              <p className="text-lg font-bold tabular-nums text-ink">
                {formatCurrency(ledger.data.balance, { decimals: 2 })}
              </p>
            </div>
          </div>

          {ledger.data.entries.length === 0 ? (
            <EmptyState icon={<Icon name="wallet" className="size-7" />} title={t('beej.ledger.empty')} />
          ) : (
            <div className="max-h-96 overflow-y-auto">
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-surface-2">
                  <tr className="text-left text-xs text-ink-soft">
                    <th scope="col" className="pb-2 font-medium">{t('beej.ledger.columns.date')}</th>
                    <th scope="col" className="pb-2 font-medium">{t('beej.ledger.columns.description')}</th>
                    <th scope="col" className="pb-2 text-right font-medium">{t('beej.ledger.columns.debit')}</th>
                    <th scope="col" className="pb-2 text-right font-medium">{t('beej.ledger.columns.credit')}</th>
                    <th scope="col" className="pb-2 text-right font-medium">{t('beej.ledger.columns.balance')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {ledger.data.entries.map((entry, i) => (
                    <tr key={i}>
                      <td className="py-2 text-ink-soft">{formatDate(entry.date, language)}</td>
                      <td className="py-2 text-ink">{entry.description}</td>
                      <td className="py-2 text-right tabular-nums text-ink">
                        {entry.debit > 0 ? formatCurrency(entry.debit, { decimals: 2 }) : '-'}
                      </td>
                      <td className="py-2 text-right tabular-nums text-ink">
                        {entry.credit > 0 ? formatCurrency(entry.credit, { decimals: 2 }) : '-'}
                      </td>
                      <td className="py-2 text-right font-semibold tabular-nums text-ink">
                        {formatCurrency(entry.runningBalance, { decimals: 2 })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

