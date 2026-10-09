import { useEffect, useState } from 'react'
import { fetchShopLogoBlob } from '@/api/settings'
import type { BillingSettings, ShopSettings } from '@/api/settings'
import { Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'

interface BillPreviewProps {
  shop: ShopSettings | undefined
  billing: BillingSettings | undefined
  loading?: boolean
}

const SAMPLE_ROWS = [
  { name: 'Groundnut Oil 5L', batch: 'B-2409-01', qty: '2', rate: '1,250.00', amount: '2,500.00' },
  { name: 'Mustard Oil 1L', batch: 'B-2409-02', qty: '4', rate: '320.00', amount: '1,280.00' },
  { name: 'Sesame Oil 5L', batch: 'B-2408-07', qty: '1', rate: '1,480.00', amount: '1,480.00' },
]

interface ResolvedLogo {
  /** Whether the logo was requested for, so a stale url is never shown for "no logo". */
  wanted: boolean
  url: string | null
  failed: boolean
}

/**
 * A scaled-down rendering of what the bill will actually look like, driven by the
 * live settings. It is an approximation of the A5 PDF, not a second renderer - the
 * point is to answer "did my logo, prefix, QR and switches land?" at a glance.
 */
export function BillPreview({ shop, billing, loading }: BillPreviewProps) {
  const { t } = useLanguage()
  const [logo, setLogo] = useState<ResolvedLogo>({ wanted: false, url: null, failed: false })

  const hasLogo = shop?.hasLogo ?? false

  // The logo endpoint sits behind the JWT filter, so it has to be fetched as a blob
  // rather than referenced directly from an <img src>. Only async callbacks touch
  // state here; the "no logo" case is derived below rather than reset in the effect.
  useEffect(() => {
    if (!hasLogo) return
    let objectUrl: string | null = null
    let cancelled = false
    fetchShopLogoBlob()
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setLogo({ wanted: true, url: objectUrl, failed: false })
      })
      .catch(() => {
        if (!cancelled) setLogo({ wanted: true, url: null, failed: true })
      })
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [hasLogo])

  const logoUrl = hasLogo && logo.wanted && !logo.failed ? logo.url : null
  const logoFailed = hasLogo && logo.failed

  if (loading || !shop || !billing) {
    return (
      <div className="mx-auto w-full max-w-sm">
        <Skeleton className="h-[32rem] w-full" />
      </div>
    )
  }

  const prefix = billing.billPrefix || 'S'
  const billNo = `${prefix}-202610-0001`
  const subtotal = 5260
  const total = 5260
  const fssai = shop.fssaiNumber
  const gstin = shop.gstin

  return (
    <div className="mx-auto w-full max-w-sm">
      <div className="rounded-card border border-line bg-white p-5 text-[11px] leading-snug text-neutral-800 shadow-card">
        {/* header */}
        <div className="flex flex-col items-center text-center">
          {hasLogo && !logoFailed && (
            <div className="mb-2 h-14 w-14 overflow-hidden rounded-pill border border-neutral-200 bg-neutral-50">
              {logoUrl && <img src={logoUrl} alt="" className="h-full w-full object-contain" />}
            </div>
          )}
          {hasLogo && logoFailed && (
            <div className="mb-2 flex h-14 w-14 items-center justify-center rounded-pill border border-dashed border-neutral-300 text-center text-[9px] text-neutral-400">
              {t('settings.billing.preview.logoFailed')}
            </div>
          )}
          <p className="text-lg font-bold leading-tight">{shop.name}</p>
          {shop.address && <p className="mt-1 max-w-[90%]">{shop.address}</p>}
          {shop.phone && <p>Ph: {shop.phone}</p>}
          {(fssai || gstin) && (
            <p className="mt-1 font-semibold">
              {fssai ? `FSSAI No: ${fssai}` : ''}
              {fssai && gstin ? '   |   ' : ''}
              {gstin ? `GSTIN: ${gstin}` : ''}
            </p>
          )}
        </div>

        <p className="my-3 text-center text-base font-bold">SALE BILL</p>

        <div className="grid grid-cols-2 gap-y-0.5 border-y border-neutral-200 py-1.5">
          <span className="font-semibold">Bill No:</span>
          <span>{billNo}</span>
          <span className="font-semibold">Date:</span>
          <span>02 Oct 2026 07:35 pm</span>
        </div>

        {/* items */}
        <table className="mt-2 w-full border-collapse">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50 text-left">
              <th className="py-1 pr-1 font-semibold">Item</th>
              {billing.showBatchNumber && <th className="py-1 pr-1 font-semibold">Batch</th>}
              <th className="py-1 pr-1 text-right font-semibold">Qty</th>
              <th className="py-1 pr-1 text-right font-semibold">Rate</th>
              <th className="py-1 text-right font-semibold">Amount</th>
            </tr>
          </thead>
          <tbody>
            {SAMPLE_ROWS.map((row) => (
              <tr key={row.name} className="border-b border-neutral-100">
                <td className="py-1 pr-1">{row.name}</td>
                {billing.showBatchNumber && <td className="py-1 pr-1">{row.batch}</td>}
                <td className="py-1 pr-1 text-right tabular-nums">{row.qty}</td>
                <td className="py-1 pr-1 text-right tabular-nums">Rs. {row.rate}</td>
                <td className="py-1 text-right tabular-nums">Rs. {row.amount}</td>
              </tr>
            ))}
          </tbody>
        </table>

        {/* totals */}
        <div className="mt-2 ml-auto w-1/2 space-y-0.5">
          <Row label={t('settings.billing.preview.subtotal')} value={`Rs. ${subtotal.toLocaleString('en-IN')}.00`} />
          <Row label={t('settings.billing.preview.discount')} value="Rs. 0.00" />
          <Row label={t('settings.billing.preview.total')} value={`Rs. ${total.toLocaleString('en-IN')}.00`} strong />
        </div>

        {/* payment block */}
        {(billing.showUpiQr && shop.upiId) || (billing.showBankDetails && shop.bankDetailsOnFile) ? (
          <div className="mt-3 flex items-start gap-2 border-t border-neutral-200 pt-2">
            {billing.showUpiQr && shop.upiId && (
              <>
                <div
                  className={cn(
                    'flex h-16 w-16 shrink-0 items-center justify-center rounded-[4px]',
                    // A stand-in pattern: the real QR is generated server-side into the PDF.
                    'bg-[repeating-linear-gradient(45deg,#000_0_2px,transparent_2px_4px)]',
                    'ring-1 ring-neutral-300',
                  )}
                  aria-hidden
                />
                <div className="min-w-0 text-[10px]">
                  <p className="font-bold">{t('settings.billing.preview.scanToPay')}</p>
                  <p className="break-all">{shop.upiId}</p>
                </div>
              </>
            )}
            {billing.showBankDetails && shop.bankDetailsOnFile && shop.bankDetailsMasked && (
              <div className="min-w-0 flex-1 text-[10px]">
                <p className="font-bold">{t('settings.billing.preview.bankDetails')}</p>
                <p className="break-words">{shop.bankDetailsMasked}</p>
              </div>
            )}
          </div>
        ) : null}

        {/* footer */}
        <div className="mt-3 border-t border-neutral-200 pt-2 text-center">
          <p>{t('settings.billing.preview.thankYou')}</p>
          <p>{t('settings.billing.preview.payment')}: UPI</p>
          {billing.footerMessage && (
            <p className="mt-1 whitespace-pre-line text-[10px] text-neutral-600">{billing.footerMessage}</p>
          )}
        </div>
      </div>

      <p className="mt-3 text-center text-xs text-ink-soft">{t('settings.billing.preview.disclaimer')}</p>
    </div>
  )
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex justify-between">
      <span className={strong ? 'font-bold' : ''}>{label}:</span>
      <span className={cn('tabular-nums', strong && 'font-bold')}>{value}</span>
    </div>
  )
}
