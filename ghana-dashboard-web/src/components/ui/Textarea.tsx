import { forwardRef } from 'react'
import type { TextareaHTMLAttributes } from 'react'
import { cn } from '@/utils/cn'

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string
  error?: string
  hint?: string
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, label, error, hint, id, rows = 3, ...props }, ref) => {
    const generatedId = `textarea-${id ?? label?.toLowerCase().replace(/\s+/g, '-') ?? Math.random().toString(36).slice(2)}`

    return (
      <div className="flex w-full flex-col gap-1.5">
        {label && (
          <label htmlFor={generatedId} className="text-sm font-medium text-ink">
            {label}
          </label>
        )}
        <textarea
          ref={ref}
          id={generatedId}
          rows={rows}
          className={cn(
            'focus-ring w-full rounded-card border bg-surface px-3.5 py-2.5 text-sm text-ink placeholder:text-ink-soft/60 transition-colors',
            'border-line hover:border-line-strong',
            'resize-vertical',
            error && 'border-crimson-500 focus:border-crimson-500',
            className,
          )}
          aria-invalid={error ? true : undefined}
          {...props}
        />
        {error ? (
          <p className="text-xs text-crimson-600">{error}</p>
        ) : hint ? (
          <p className="text-xs text-ink-soft">{hint}</p>
        ) : null}
      </div>
    )
  },
)

Textarea.displayName = 'Textarea'

