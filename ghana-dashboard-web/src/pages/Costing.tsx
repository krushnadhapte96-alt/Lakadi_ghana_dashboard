import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { applySuggestedPrice, fetchCostingProducts } from '@/api/costing'
import type { CostingProduct } from '@/api/costing'
import { BatchCostDrawer } from '@/components/costing/BatchCostDrawer'
import { CostDefaultsPanel } from '@/components/costing/CostDefaultsPanel'
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'
import { formatCurrency } from '@/utils/format'
import { Icon } from '@/utils/icons'

function MarginBadge({ product }: { product: CostingProduct }) {
  const { t } = useLanguage()
  return (
    <div className="flex flex-col items-end gap-1">
      <span className="tabular-nums text-sm font-semibold text-ink">{product.marginPct.toFixed(1)}%</span>
      {product.belowTarget && <Badge variant="clay">{t('costing.belowTarget')}</Badge>}
    </div>
  )
}

function ProductRow({
  product,
  onViewBatch,
  onApply,
  applying,
}: {
  product: CostingProduct
  onViewBatch: (batchId: number) => void
  onApply: (productId: number) => void
  applying: boolean
}) {
  const { t } = useLanguage()
  return (
    <tr className="border-b border-line/60">
      <td className="px-3 py-3">
        <div className="font-medium text-ink">{product.productName}</div>
        <div className="text-xs text-ink-soft">{product.sku}</div>
      </td>
      <td className="px-3 py-3 text-right tabular-nums text-ink">{formatCurrency(product.unitCost)}</td>
      <td className="px-3 py-3 text-right tabular-nums text-ink">{formatCurrency(product.sellingPrice)}</td>
      <td className="px-3 py-3 text-right">
        <MarginBadge product={product} />
      </td>
      <td className="px-3 py-3 text-right tabular-nums text-ink-soft">{formatCurrency(product.suggestedPrice)}</td>
      <td className="px-3 py-3">
        <div className="flex items-center justify-end gap-2">
          {product.sourceBatchId !== null && (
            <button
              type="button"
              onClick={() => onViewBatch(product.sourceBatchId as number)}
              className="focus-ring rounded-pill px-2.5 py-1 text-xs font-medium text-brand-600 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/20"
            >
              {product.sourceBatchCode}
            </button>
          )}
          <Button
            variant="secondary"
            size="sm"
            disabled={applying}
            onClick={() => onApply(product.productId)}
          >
            {t('costing.apply')}
          </Button>
        </div>
      </td>
    </tr>
  )
}

export function CostingPage() {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [drawerBatchId, setDrawerBatchId] = useState<number | null>(null)
  const [applyingId, setApplyingId] = useState<number | null>(null)

  const products = useQuery({ queryKey: ['costing', 'products'], queryFn: fetchCostingProducts })

  const apply = useMutation({
    mutationFn: (productId: number) => applySuggestedPrice(productId),
    onMutate: (productId) => setApplyingId(productId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['costing', 'products'] })
      toast.success(t('costing.applySuccess'))
    },
    onError: () => toast.error(t('costing.applyFailed')),
    onSettled: () => setApplyingId(null),
  })

  return (
    <div>
      <PageHeader title={t('costing.title')} subtitle={t('costing.subtitle')} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <Card className="overflow-hidden">
            {products.isError ? (
              <ErrorState
                icon={<Icon name="alert-circle" className="size-7" />}
                title={t('costing.error.title')}
                message={t('costing.error.body')}
                retryLabel={t('common.retry')}
                onRetry={() => void products.refetch()}
                className="p-6"
              />
            ) : !products.data ? (
              <div className="space-y-3 p-5">
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
              </div>
            ) : products.data.length === 0 ? (
              <EmptyState
                icon={<Icon name="costing" className="size-7" />}
                title={t('costing.empty.title')}
                description={t('costing.empty.body')}
                className="p-6"
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[640px] text-sm">
                  <thead>
                    <tr className="border-b border-line text-xs text-ink-soft">
                      <th scope="col" className="px-3 py-2.5 text-left font-medium">{t('costing.table.product')}</th>
                      <th scope="col" className="px-3 py-2.5 text-right font-medium">{t('costing.table.unitCost')}</th>
                      <th scope="col" className="px-3 py-2.5 text-right font-medium">{t('costing.table.price')}</th>
                      <th scope="col" className="px-3 py-2.5 text-right font-medium">{t('costing.table.margin')}</th>
                      <th scope="col" className="px-3 py-2.5 text-right font-medium">{t('costing.table.suggested')}</th>
                      <th scope="col" className={cn('px-3 py-2.5 text-right font-medium')}>{t('costing.table.action')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {products.data.map((product) => (
                      <ProductRow
                        key={product.productId}
                        product={product}
                        onViewBatch={setDrawerBatchId}
                        onApply={(id) => apply.mutate(id)}
                        applying={applyingId === product.productId}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>

        <div>
          <CostDefaultsPanel />
        </div>
      </div>

      <BatchCostDrawer
        open={drawerBatchId !== null}
        batchId={drawerBatchId}
        onClose={() => setDrawerBatchId(null)}
      />
    </div>
  )
}

