import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { LanguageToggle } from '@/components/LanguageToggle'
import { ThemeToggle } from '@/components/ThemeToggle'
import { WoodenPress } from '@/components/illustrations/WoodenPress'
import { OilDrop } from '@/components/illustrations/OilDrop'
import { Seed } from '@/components/illustrations/Seed'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'

const FLOATERS = [
  { className: 'left-[8%] top-[14%]', size: 'size-7', delay: '0s', duration: '7s' },
  { className: 'right-[12%] top-[8%]', size: 'size-9', delay: '1.2s', duration: '9s' },
  { className: 'left-[16%] bottom-[22%]', size: 'size-8', delay: '0.6s', duration: '8s' },
  { className: 'right-[20%] bottom-[16%]', size: 'size-6', delay: '2s', duration: '10s' },
  { className: 'left-[42%] top-[6%]', size: 'size-5', delay: '1.8s', duration: '7.5s' },
  { className: 'right-[6%] top-[46%]', size: 'size-10', delay: '0.2s', duration: '11s' },
]

export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}) {
  const { t } = useLanguage()

  return (
    <main className="grid min-h-screen lg:grid-cols-2">
      {/* Illustrative panel */}
      <div className="relative hidden overflow-hidden bg-gradient-to-br from-gold-200 via-brand-500 to-brand-900 text-white lg:block">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_15%,rgba(255,255,255,0.25),transparent_45%)]" />
        {FLOATERS.map((f, i) => (
          <Seed
            key={i}
            className={cn('animate-float-seed absolute text-white/70 drop-shadow-card', f.className, f.size)}
            style={{ animationDelay: f.delay, animationDuration: f.duration }}
          />
        ))}
        <div className="relative z-10 flex h-full flex-col justify-between p-10">
          <Link to="/" className="flex items-center gap-2 text-lg font-semibold">
            <OilDrop className="size-7" />
            <span>{t('app.title')}</span>
          </Link>
          <div>
            <WoodenPress className="size-40 animate-pulse-soft drop-shadow-card" />
            <p className="font-serif mt-8 max-w-md text-4xl font-semibold leading-tight">
              {t('auth.brand.headline')}
            </p>
            <p className="mt-3 max-w-sm text-white/80">{t('auth.brand.sub')}</p>
          </div>
          <div className="flex items-center gap-3">
            <LanguageToggle />
            <ThemeToggle />
          </div>
        </div>
      </div>

      {/* Form panel */}
      <div className="flex flex-col justify-center px-6 py-10 sm:px-12">
        <div className="mx-auto w-full max-w-md">
          <div className="mb-8 flex items-center justify-between lg:hidden">
            <Link to="/" className="flex items-center gap-2 font-semibold">
              <OilDrop className="size-6 text-brand-600" />
              <span>{t('app.title')}</span>
            </Link>
            <div className="flex items-center gap-2">
              <LanguageToggle />
              <ThemeToggle />
            </div>
          </div>

          <p className="text-xs font-semibold tracking-widest text-brand-600 uppercase">{t('auth.meta')}</p>
          <h1 className="font-serif mt-1 text-3xl font-semibold sm:text-4xl">{title}</h1>
          <p className="mt-2 text-sm text-ink-soft">{subtitle}</p>

          <div className="mt-8">{children}</div>
          <div className="mt-6 text-center text-sm text-ink-soft">{footer}</div>
        </div>
      </div>
    </main>
  )
}
