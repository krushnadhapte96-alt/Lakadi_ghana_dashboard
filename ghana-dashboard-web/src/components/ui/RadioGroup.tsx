import { forwardRef } from 'react'
import type { InputHTMLAttributes } from 'react'
import { cn } from '@/utils/cn'

export interface RadioGroupOption {
  value: string
  label: string
  disabled?: boolean
}

export interface RadioGroupProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  options: RadioGroupOption[]
  error?: string
  hint?: string
  direction?: 'horizontal' | 'vertical'
}

export const RadioGroup = forwardRef<HTMLInputElement, RadioGroupProps>(
  (
    {
      className,
      label,
      options,
      error,
      hint,
      direction = 'vertical',
      id,
      ...props
    },
    ref,
  ) => {
    const generatedId = `radiogroup-${id ?? label?.toLowerCase().replace(/\s+/g, '-') ?? Math.random().toString(36).slice(2)}`

    return (
      <div className={cn('flex w-full flex-col gap-1.5', className)}>
        {label && (
          <legend className="text-sm font-medium text-ink">{label}</legend>
        )}
        <div
          role="radiogroup"
          className={cn(
            'flex gap-4',
            direction === 'horizontal' ? 'flex-row' : 'flex-col',
          )}
        >
          {options.map((option) => (
            <label
              key={option.value}
              className={cn(
                'focus-ring flex cursor-pointer items-center gap-2 rounded-card p-3 transition-colors',
                'hover:bg-surface-2 disabled:cursor-not-allowed disabled:opacity-50',
                props.value === option.value ? 'bg-brand-50 ring-2 ring-brand-500' : '',
                option.disabled && 'opacity-50 cursor-not-allowed',
              )}
            >
              <input
                ref={ref}
                type="radio"
                id={`${generatedId}-${option.value}`}
                name={generatedId}
                value={option.value}
                checked={props.value === option.value}
                disabled={option.disabled || props.disabled}
                className="sr-only"
                {...props}
              />
              <span
                className={cn(
                  'flex size-5 items-center justify-center rounded-full border-2 border-line transition-colors',
                  props.value === option.value
                    ? 'border-brand-600 bg-brand-600'
                    : 'border-line',
                )}
              >
                {props.value === option.value && (
                  <span className="size-2.5 rounded-full bg-white" />
                )}
              </span>
              <span className="text-sm text-ink">{option.label}</span>
            </label>
          ))}
        </div>
        {error ? (
          <p className="text-xs text-crimson-600">{error}</p>
        ) : hint ? (
          <p className="text-xs text-ink-soft">{hint}</p>
        ) : null}
      </div>
    )
  },
)

RadioGroup.displayName = 'RadioGroup'

