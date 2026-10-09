import type { UseQueryResult } from '@tanstack/react-query'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { fetchCustomer } from '@/api/customers'
import type { CustomerDetail } from '@/api/customers'
import { Button, ErrorState } from '@/components/ui'
import { PlaceholderPage } from '@/components/layout/PlaceholderPage'
import { useLanguage } from '@/i18n'
import { Icon } from '@/utils/icons'
import { POS_CUSTOMER_PARAM, readPosCustomerId } from '@/utils/posPrefill'

export function SalesPage() {
  const [searchParams] = useSearchParams()
  const customerId = readPosCustomerId(searchParams)

  const query = useQuery({
    queryKey: ['customer', customerId],
    queryFn: () => fetchCustomer(customerId as number),
    enabled: customerId !== null,
  })

  return (
    <>
      {customerId !== null && <PrefillBanner query={query} customerId={customerId} />}
      <PlaceholderPage module="Sales & Orders" description="Manage your sales and orders" />
    </>
  )
}

/**
 * Checklist item: "New Sale opens POS with customer set". The POS screen itself is
 * still a placeholder, so until it lands this resolves the customer the URL points
 * at - which proves the hand-off survives navigation and gives the POS a single
 * source of truth to read instead of a snapshot passed along in session storage.
 */
function PrefillBanner({
  query,
  customerId,
}: {
  query: UseQueryResult<CustomerDetail, Error>
  customerId: number
}) {
  const { t } = useLanguage()
  const [, setSearchParams] = useSearchParams()

  if (query.isPending) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 pt-6 sm:px-6">
        <div className="flex items-center gap-2 rounded-card border border-brand-200 bg-brand-50 px-4 py-3 text-sm text-brand-700">
          <Icon name="sale" className="size-4" />
          {t('sales.prefill.loading')}
        </div>
      </div>
    )
  }

  if (query.isError) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 pt-6 sm:px-6">
        <ErrorState
          title={t('sales.prefill.failedTitle')}
          message={t('sales.prefill.failedBody', { id: customerId })}
          onRetry={() => query.refetch()}
          retryLabel={t('common.retry')}
        />
      </div>
    )
  }

  const customer = query.data
  return (
    <div className="mx-auto w-full max-w-7xl px-4 pt-6 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-brand-200 bg-brand-50 px-4 py-3">
        <div className="min-w-0">
          <p className="flex items-center gap-2 text-sm font-semibold text-brand-700">
            <Icon name="sale" className="size-4" />
            {t('sales.prefill.title', { name: customer?.name ?? String(customerId) })}
          </p>
          <p className="mt-0.5 text-xs text-brand-700/80">
            {t('sales.prefill.hint', { param: POS_CUSTOMER_PARAM })}
          </p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => setSearchParams({}, { replace: true })}>
          {t('sales.prefill.clear')}
        </Button>
      </div>
    </div>
  )
}
