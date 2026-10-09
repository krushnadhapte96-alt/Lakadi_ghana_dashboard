import { useLanguage, type Language } from '@/i18n'
import { cn } from '@/utils/cn'

const options: { value: Language; label: string }[] = [
  { value: 'en', label: 'EN' },
  { value: 'hi', label: '\u0939\u093f\u0902\u0926\u0940' },
  { value: 'mr', label: '\u092e\u0930\u093e\u0920\u0940' },
]

export function LanguageToggle() {
  const { language, setLanguage, t } = useLanguage()

  return (
    <div
      role="group"
      aria-label={t('common.language')}
      className="inline-flex items-center gap-0.5 rounded-pill border border-line bg-surface-2 p-0.5"
    >
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={language === option.value}
          onClick={() => setLanguage(option.value)}
          className={cn(
            'focus-ring rounded-pill px-3 py-1 text-xs font-semibold transition-colors',
            language === option.value
              ? 'bg-brand-600 text-white shadow-soft'
              : 'text-ink-soft hover:text-ink',
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

