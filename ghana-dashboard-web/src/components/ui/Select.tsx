import { forwardRef, useState } from 'react'
import type { HTMLAttributes } from 'react'
import { cn } from '@/utils/cn'
import { Icon } from '@/utils/icons'

export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

export interface SelectProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onChange'> {
  label?: string
  options: SelectOption[]
  value?: string
  onChange?: (value: string) => void
  error?: string
  hint?: string
  placeholder?: string
  disabled?: boolean
}

export const Select = forwardRef<HTMLDivElement, SelectProps>(
  (
    {
      className,
      label,
      options,
      value,
      onChange,
      error,
      hint,
      placeholder = 'Select an option',
      disabled = false,
      ...props
    },
    ref,
  ) => {
    const [open, setOpen] = useState(false)

    const selectedOption = options.find((o) => o.value === value)

    return (
      <div ref={ref} className={cn('flex w-full flex-col gap-1.5', className)} {...props}>
        {label && (
          <label className="text-sm font-medium text-ink">{label}</label>
        )}
        <div className="relative">
          <button
            type="button"
            role="combobox"
            disabled={disabled}
            onClick={() => !disabled && setOpen(!open)}
            className={cn(
              'focus-ring flex w-full items-center justify-between rounded-card border bg-surface px-3.5 py-2.5 text-sm text-ink transition-colors',
              'border-line hover:border-line-strong',
              error && 'border-crimson-500',
              disabled && 'cursor-not-allowed opacity-50',
            )}
          >
            <span className={!selectedOption ? 'text-ink-soft/60' : ''}>
              {selectedOption?.label || placeholder}
            </span>
            <Icon name="chevron-down" className="size-4 text-ink-soft" />
          </button>

          {open && !disabled && (
            <div className="absolute z-50 mt-1 w-full rounded-card border border-line bg-surface-2 shadow-card">
              {options.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="option"
                  disabled={option.disabled}
                  onClick={() => {
                    onChange?.(option.value)
                    setOpen(false)
                  }}
                  className={cn(
                    'focus-ring w-full px-3.5 py-2.5 text-left text-sm transition-colors',
                    'hover:bg-surface-3 disabled:cursor-not-allowed disabled:text-ink-soft',
                    selectedOption?.value === option.value && 'bg-brand-50 text-brand-600',
                  )}
                >
                  {option.label}
                </button>
              ))}
            </div>
          )}
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

Select.displayName = 'Select'

