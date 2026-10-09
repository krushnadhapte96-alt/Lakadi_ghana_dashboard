import { forwardRef, useId, useState } from 'react'
import type { InputHTMLAttributes } from 'react'
import { cn } from '@/utils/cn'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  hint?: string
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, hint, id, type = 'text', ...props }, ref) => {
    const autoId = useId()
    const generatedId = `input-${id ?? (label ? label.toLowerCase().replace(/\s+/g, '-') : autoId)}`
    const [visible, setVisible] = useState(false)
    const isPassword = type === 'password'
    const inputType = type === 'password' && visible ? 'text' : type

    return (
      <div className="flex w-full flex-col gap-1.5">
        {label && <label htmlFor={generatedId} className="text-sm font-medium text-ink">{label}</label>}
        <div className="relative">
          <input
            ref={ref}
            id={generatedId}
            type={inputType}
            className={cn(
              'focus-ring w-full rounded-card border bg-surface px-3.5 py-2.5 text-sm text-ink placeholder:text-ink-soft/60 transition-colors',
              'border-line hover:border-line-strong',
              error && 'border-crimson-500 focus:border-crimson-500',
              isPassword && 'pr-11',
              className,
            )}
            aria-invalid={error ? true : undefined}
            {...props}
          />
          {isPassword && (
            <button
              type="button"
              tabIndex={-1}
              aria-label={visible ? 'Hide password' : 'Show password'}
              onClick={() => setVisible((v) => !v)}
              className="focus-ring absolute right-2 top-1/2 -translate-y-1/2 rounded-pill p-1.5 text-ink-soft transition-colors hover:text-ink"
            >
              {visible ? <EyeOffIcon /> : <EyeIcon />}
            </button>
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

Input.displayName = 'Input'

function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="size-4" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="size-4" aria-hidden="true">
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
      <path d="M1 1l22 22" />
    </svg>
  )
}
