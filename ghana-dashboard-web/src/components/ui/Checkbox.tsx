import { forwardRef, useState } from 'react'
import type { InputHTMLAttributes } from 'react'
import { cn } from '@/utils/cn'


export interface CheckboxProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  hint?: string
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  ({ className, label, error, hint, id, ...props }, ref) => {
    const generatedId = `checkbox-${id ?? label?.toLowerCase().replace(/\s+/g, '-') ?? Math.random().toString(36).slice(2)}`
    const [checked, setChecked] = useState(!!props.checked)

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      setChecked(e.target.checked)
      props.onChange?.(e)
    }

    return (
      <div className="flex w-full flex-col gap-1.5">
        <div className="flex items-center gap-3">
          <input
            ref={ref}
            id={generatedId}
            type="checkbox"
            checked={checked}
            onChange={handleChange}
            className={cn(
              'focus-ring appearance-none size-5 rounded border-2 border-line transition-colors',
              'checked:bg-brand-600 checked:border-brand-600 checked:hover:bg-brand-500',
              'hover:border-line-strong disabled:cursor-not-allowed disabled:opacity-50',
              error && 'border-crimson-500',
              className,
            )}
            style={{
              WebkitAppearance: 'none',
              appearance: 'none',
              backgroundImage: checked
                ? `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='white' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M5 12l5 5L20 7'/%3E%3C/svg%3E")`
                : 'none',
              backgroundSize: 'contain',
              backgroundPosition: 'center',
              backgroundRepeat: 'no-repeat',
            }}
            {...props}
          />
          {label && (
            <label
              htmlFor={generatedId}
              className="text-sm font-medium text-ink cursor-pointer"
            >
              {label}
            </label>
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

Checkbox.displayName = 'Checkbox'

