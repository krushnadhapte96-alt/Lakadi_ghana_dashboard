import { useLanguage } from '@/i18n'
import type { TranslationKey } from '@/i18n'
import { cn } from '@/utils/cn'
import { isValidRange, presetRange, todayISO } from '@/utils/dateRange'
import type { DateRange, DateRangeValue, RangePreset } from '@/utils/dateRange'

const PRESETS: Exclude<RangePreset, 'custom'>[] = ['thisMonth', 'lastMonth', 'last30']

export function DateRangeControl({
  value,
  onChange,
}: {
  value: DateRangeValue
  onChange: (value: DateRangeValue) => void
}) {
  const { t } = useLanguage()
  const invalid = !isValidRange(value.range)

  function pill(active: boolean) {
    return cn(
      'focus-ring rounded-pill border px-3.5 py-1.5 text-sm font-medium transition-colors',
      active
        ? 'border-brand-600 bg-brand-600 text-white'
        : 'border-line bg-surface-2 text-ink-soft hover:bg-surface-3 hover:text-ink',
    )
  }

  function setDate(field: keyof DateRange, date: string) {
    onChange({ preset: 'custom', range: { ...value.range, [field]: date } })
  }

  return (
    <div className="flex flex-col gap-3" role="group" aria-label={t('range.label')}>
      <div className="flex flex-wrap gap-2">
        {PRESETS.map((preset) => (
          <button
            key={preset}
            type="button"
            aria-pressed={value.preset === preset}
            onClick={() => onChange({ preset, range: presetRange(preset) })}
            className={pill(value.preset === preset)}
          >
            {t(`range.${preset}` as TranslationKey)}
          </button>
        ))}
        <button
          type="button"
          aria-pressed={value.preset === 'custom'}
          onClick={() => onChange({ preset: 'custom', range: value.range })}
          className={pill(value.preset === 'custom')}
        >
          {t('range.custom')}
        </button>
      </div>

      {value.preset === 'custom' && (
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm font-medium text-ink">
            {t('range.from')}
            <input
              type="date"
              value={value.range.from}
              max={todayISO()}
              onChange={(e) => setDate('from', e.target.value)}
              className="focus-ring rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm font-medium text-ink">
            {t('range.to')}
            <input
              type="date"
              value={value.range.to}
              max={todayISO()}
              onChange={(e) => setDate('to', e.target.value)}
              className="focus-ring rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
          {invalid && (
            <p role="alert" className="text-xs text-crimson-600">
              {t('range.invalid')}
            </p>
          )}
        </div>
      )}
    </div>
  )
}

