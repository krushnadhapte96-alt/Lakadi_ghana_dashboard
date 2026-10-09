import { forwardRef, useState } from 'react'
import type { InputHTMLAttributes } from 'react'
import { cn } from '@/utils/cn'

export interface SwitchProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  hint?: string
}

export const Switch = forwardRef<HTMLInputElement, SwitchProps>(
  ({ className, label, error, hint, id, ...props }, ref) => {
    const generatedId = `switch-${id ?? label?.toLowerCase().replace(/\s+/g, '-') ?? Math.random().toString(36).slice(2)}`
    const [inner, setInner] = useState(!!props.checked)
    const checked = props.checked ?? inner

    return (
      <div className="flex w-full flex-col gap-1.5">
        <div className="flex items-center gap-3">
          <button
            type="button"
            role="switch"
            aria-checked={checked}
            onClick={() => {
              setInner(!checked)
              // Simulate onChange for uncontrolled usage
              const event = { target: { checked: !checked } } as React.ChangeEvent<HTMLInputElement>
              props.onChange?.(event)
            }}
            className={cn(
              'focus-ring relative inline-flex h-7 w-12 items-center rounded-full transition-colors',
              checked
                ? 'bg-brand-600 hover:bg-brand-500'
                : 'bg-surface-3 hover:bg-surface-2',
              error && 'border border-crimson-500',
              className,
            )}
            disabled={props.disabled}
          >
            <span
              className={cn(
                'absolute left-1 top-1 size-5 rounded-full bg-white transition-transform shadow-soft',
                checked ? 'translate-x-5' : 'translate-x-0',
              )}
            />
          </button>
          <input type="hidden" ref={ref} id={generatedId} checked={checked} {...props} />
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

Switch.displayName = 'Switch'

