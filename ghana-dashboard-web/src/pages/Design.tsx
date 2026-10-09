import { useState } from 'react'
import type { ComponentType, ReactNode, SVGProps } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { LanguageToggle } from '@/components/LanguageToggle'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { EmptyBox } from '@/components/illustrations/EmptyBox'
import { WoodenPress } from '@/components/illustrations/WoodenPress'
import { Leaf } from '@/components/illustrations/Leaf'
import { OilDrop } from '@/components/illustrations/OilDrop'
import { Seed } from '@/components/illustrations/Seed'
import { api, API_BASE_URL, normalizeError } from '@/api/axios'
import type { ApiResponse } from '@/api/types'
import { useLanguage } from '@/i18n'
import { formatCurrency, formatDate, formatQty } from '@/utils/format'
import { litreToKg } from '@/utils/convert'

const BRAND_STOPS = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950']
const GOLD_STOPS = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900']
const CRIMSON_STOPS = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900']

const ILLUSTRATIONS: { name: string; Component: ComponentType<SVGProps<SVGSVGElement>> }[] = [
  { name: 'Leaf', Component: Leaf },
  { name: 'Seed', Component: Seed },
  { name: 'OilDrop', Component: OilDrop },
  { name: 'WoodenPress', Component: WoodenPress },
  { name: 'EmptyBox', Component: EmptyBox },
]

type PingState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: ApiResponse<Record<string, string>> }
  | { status: 'error'; message: string }

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mt-14">
      <h2 className="text-xl font-semibold sm:text-2xl">{title}</h2>
      <div className="mt-5">{children}</div>
    </section>
  )
}

function Swatch({ token, variable }: { token: string; variable: string }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div
        className="h-14 w-full rounded-card border border-line"
        style={{ backgroundColor: `var(${variable})` }}
      />
      <span className="text-xs text-ink-soft">{token}</span>
    </div>
  )
}

function SwatchRow({
  label,
  prefix,
  stops,
}: {
  label: string
  prefix: 'brand' | 'gold' | 'crimson'
  stops: string[]
}) {
  return (
    <div>
      <p className="mb-3 text-sm font-semibold text-ink-soft">{label}</p>
      <div className="grid grid-cols-6 gap-3 sm:grid-cols-11">
        {stops.map((stop) => (
          <Swatch
            key={stop}
            token={`${prefix}-${stop}`}
            variable={`--color-${prefix}-${stop}`}
          />
        ))}
      </div>
    </div>
  )
}

export function DesignPage() {
  const { language, t } = useLanguage()
  const [ping, setPing] = useState<PingState>({ status: 'idle' })

  async function handlePing() {
    setPing({ status: 'loading' })
    try {
      const response = await api.get<ApiResponse<Record<string, string>>>('/health')
      setPing({ status: 'success', data: response.data })
      toast.success(t('ping.ok'))
    } catch (error) {
      const normalized = normalizeError(error)
      const suffix = normalized.status ? ` (HTTP ${normalized.status})` : ''
      setPing({ status: 'error', message: `${normalized.message}${suffix}` })
      toast.error(t('ping.fail'))
    }
  }

  return (
    <main className="mx-auto min-h-screen max-w-5xl px-6 py-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold tracking-widest text-brand-600 uppercase">
            {t('design.meta')}
          </p>
          <h1 className="mt-1 text-3xl sm:text-4xl">{t('design.meta')}</h1>
        </div>
        <div className="flex items-center gap-3">
          <Link to="/" className="text-sm font-medium text-ink-soft transition-colors hover:text-ink">
            {t('nav.home')}
          </Link>
          <LanguageToggle />
          <ThemeToggle />
        </div>
      </header>
      <p className="mt-3 max-w-2xl text-ink-soft">{t('design.subtitle')}</p>

      <Section title={t('design.colors')}>
        <div className="flex flex-col gap-8">
          <SwatchRow label="Brand - forest green" prefix="brand" stops={BRAND_STOPS} />
          <SwatchRow label="Gold · oil &amp; harvest" prefix="gold" stops={GOLD_STOPS} />
          <SwatchRow label="Crimson - brand red" prefix="crimson" stops={CRIMSON_STOPS} />
        </div>
      </Section>

      <Section title={t('design.typography')}>
        <div className="grid gap-4 sm:grid-cols-3">
          <Card className="p-6">
            <p className="font-serif text-3xl font-semibold">Lakadi Ghana</p>
            <p className="mt-2 text-sm text-ink-soft">{t('fonts.display')}</p>
          </Card>
          <Card className="p-6">
            <p className="text-2xl font-semibold">Oil trades, logged daily.</p>
            <p className="mt-2 text-sm text-ink-soft">{t('fonts.body')}</p>
          </Card>
          <Card className="p-6">
            <p className="font-hindi text-2xl font-semibold">{'\u0939\u093f\u0902\u0926\u0940 \u0928\u092e\u0942\u0928\u093e'}</p>
            <p className="mt-2 text-sm text-ink-soft">{t('fonts.hindi')}</p>
          </Card>
        </div>
      </Section>

      <Section title={t('design.components')}>
        <div className="grid gap-4 lg:grid-cols-2">
          <Card className="flex flex-col gap-6 p-6">
            <div>
              <p className="mb-3 text-sm font-semibold text-ink-soft">{t('design.buttons')}</p>
              <div className="flex flex-wrap items-center gap-3">
                <Button>{t('buttons.primary')}</Button>
                <Button variant="secondary">{t('buttons.secondary')}</Button>
                <Button variant="ghost">{t('buttons.ghost')}</Button>
                <Button variant="danger">{t('buttons.danger')}</Button>
                <Button disabled>{t('buttons.disabled')}</Button>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <Button size="sm">{t('buttons.primary')}</Button>
              <Button size="md">{t('buttons.primary')}</Button>
              <Button size="lg">{t('buttons.primary')}</Button>
            </div>
          </Card>

          <div className="flex flex-col gap-4">
            <Card className="flex items-start justify-between gap-4 p-6">
              <div>
                <p className="text-sm text-ink-soft">{t('cards.revenue')}</p>
                <p className="mt-2 text-2xl font-semibold">{formatCurrency(3_450_000.5)}</p>
                <p className="mt-1 text-xs text-ink-soft">
                  {t('cards.updated', { date: formatDate(new Date(), language) })}
                </p>
              </div>
              <OilDrop className="size-10" />
            </Card>

            <Card className="flex items-start justify-between gap-4 p-6">
              <div>
                <p className="text-sm text-ink-soft">{t('cards.oilStock')}</p>
                <p className="mt-2 text-2xl font-semibold">
                  {formatQty(3250)} <span className="text-base text-ink-soft">L</span>
                </p>
                <p className="mt-1 text-xs text-ink-soft">
                  {t('cards.conversion', { kg: formatQty(litreToKg(3250)) })}
                </p>
              </div>
              <Seed className="size-10" />
            </Card>

            <Card className="flex items-center justify-between gap-4 p-6">
              <div>
                <p className="font-semibold">{t('cards.sample')}</p>
                <p className="mt-1 text-sm text-ink-soft">{t('cards.sampleBody')}</p>
              </div>
              <Button variant="ghost" size="sm">
                {t('cards.action')}
              </Button>
            </Card>
          </div>
        </div>
      </Section>

      <Section title={t('design.illustrations')}>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-5">
          {ILLUSTRATIONS.map(({ name, Component }) => (
            <Card key={name} className="flex flex-col items-center gap-3 p-6">
              <Component className="size-16 text-ink" />
              <span className="text-xs text-ink-soft">{name}</span>
            </Card>
          ))}
        </div>
      </Section>

      <Section title={t('design.backend')}>
        <Card className="p-6">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="font-semibold">{t('ping.instruction')}</p>
              <code className="mt-1 inline-block rounded-card bg-surface px-2 py-1 text-xs text-ink-soft">
                GET {API_BASE_URL}/health
              </code>
            </div>
            <Button onClick={handlePing} disabled={ping.status === 'loading'}>
              {ping.status === 'loading' ? t('ping.checking') : t('ping.button')}
            </Button>
          </div>

          {ping.status === 'loading' && (
            <div className="shimmer-surface mt-6 h-28 animate-shimmer rounded-card border border-line" />
          )}

          {ping.status === 'success' && (
            <div className="mt-6 rounded-card border border-line bg-surface p-4">
              <p className="flex items-center gap-2 text-sm font-semibold text-brand-600">
                <span className="inline-block size-2 rounded-full bg-brand-500" />
                {t('ping.ok')} · {ping.data.message}
              </p>
              <pre className="mt-3 overflow-x-auto text-xs leading-relaxed text-ink-soft">
                {JSON.stringify(ping.data, null, 2)}
              </pre>
            </div>
          )}

          {ping.status === 'error' && (
            <div className="mt-6 rounded-card border border-line bg-surface p-4">
              <p className="flex items-center gap-2 text-sm font-semibold text-crimson-600">
                <span className="inline-block size-2 rounded-full bg-crimson-500" />
                {t('ping.fail')}
              </p>
              <pre className="mt-3 overflow-x-auto text-xs leading-relaxed text-ink-soft">
                {ping.message}
              </pre>
            </div>
          )}
        </Card>
      </Section>
    </main>
  )
}
