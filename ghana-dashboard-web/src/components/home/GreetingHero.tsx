import { motion } from 'framer-motion'
import { SunriseField } from '@/components/illustrations/SunriseField'
import { useLanguage } from '@/i18n'

/** Salutation bucket from the shop's own clock - 0-4 morning, 4-12 afternoon, else evening. */
function partOfDay(hour: number): 'morning' | 'afternoon' | 'evening' {
  if (hour < 12) return 'morning'
  if (hour < 17) return 'afternoon'
  return 'evening'
}

/**
 * Landing header: a salutation with the signed-in user's first name, over the animated
 * sunrise/mustard-field illustration, lit by floating 3D orbs.
 *
 * <p>Only the first name is used - "Suprabhat, Ramesh" is how a shopkeeper greets a
 * customer, and the full name can be a long string that wraps badly next to the art.
 */
export function GreetingHero({ name }: { name: string }) {
  const { t, language } = useLanguage()
  const firstName = name.trim().split(/\s+/)[0] ?? ''

  return (
    <motion.section
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, ease: [0.2, 0.8, 0.2, 1] }}
      className="glow-border relative overflow-hidden rounded-[1.75rem] border border-line bg-gradient-to-br from-brand-500/15 via-surface-2 to-gold-400/15 p-6 shadow-card backdrop-blur-xl sm:p-8"
    >
      {/* floating 3D orbs */}
      <div aria-hidden="true" className="perspective-1200 pointer-events-none absolute inset-0">
        <div className="absolute -right-10 -top-12 size-44 animate-float-3d rounded-full bg-gradient-to-br from-brand-300/60 to-brand-600/30 blur-[2px] [box-shadow:inset_-14px_-14px_30px_oklch(0.3_0.08_160/0.5),inset_10px_10px_24px_oklch(1_0_0/0.35)]" />
        <div
          className="absolute right-28 top-10 size-14 animate-float-3d rounded-full bg-gradient-to-br from-gold-300 to-gold-600 [box-shadow:inset_-6px_-6px_14px_oklch(0.4_0.1_75/0.5),inset_4px_4px_10px_oklch(1_0_0/0.5)]"
          style={{ animationDelay: '-2s' }}
        />
        <div
          className="absolute bottom-6 right-1/3 size-8 animate-float-3d rounded-full bg-gradient-to-br from-brand-200 to-brand-500 opacity-80"
          style={{ animationDelay: '-4s' }}
        />
      </div>

      <SunriseField className="pointer-events-none absolute -right-6 -bottom-4 h-28 w-56 opacity-90 sm:h-36 sm:w-72" />

      <div className="relative max-w-xl">
        <h1 className="font-display text-2xl font-bold tracking-tight text-ink sm:text-4xl">
          {t(`home.greeting.${partOfDay(new Date().getHours())}`, { name: firstName })}
        </h1>
        <p className="mt-2 text-sm text-ink-soft sm:text-base">
          {t('home.greeting.subtitle', { date: new Date().toLocaleDateString(language === 'hi' ? 'hi-IN' : 'en-IN', {
            weekday: 'long',
            day: 'numeric',
            month: 'long',
          }) })}
        </p>
      </div>
    </motion.section>
  )
}
