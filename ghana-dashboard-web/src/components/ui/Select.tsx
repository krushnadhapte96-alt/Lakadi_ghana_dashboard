import { forwardRef, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { HTMLAttributes } from 'react'
import { createPortal } from 'react-dom'
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

const MENU_MAX_HEIGHT = 256

/**
 * Dropdown list is rendered in a portal so it is never clipped by a scroll
 * container (`Sheet`), an `overflow-hidden` card, or a stacking context created
 * by an ancestor's backdrop-blur/transform.
 */
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
    const [anchor, setAnchor] = useState<{ top: number; left: number; width: number; up: boolean } | null>(
      null,
    )
    const triggerRef = useRef<HTMLButtonElement>(null)
    const listRef = useRef<HTMLDivElement>(null)

    const selectedOption = options.find((o) => o.value === value)

    const place = useCallback(() => {
      const el = triggerRef.current
      if (!el) return
      const rect = el.getBoundingClientRect()
      const below = window.innerHeight - rect.bottom
      const up = below < MENU_MAX_HEIGHT + 16 && rect.top > below
      setAnchor({
        top: up ? rect.top - 4 : rect.bottom + 4,
        left: rect.left,
        width: rect.width,
        up,
      })
    }, [])

    useLayoutEffect(() => {
      if (open) place()
    }, [open, place])

    useEffect(() => {
      if (!open) return
      const onScrollOrResize = () => place()
      const onPointerDown = (event: MouseEvent) => {
        const target = event.target as Node
        if (triggerRef.current?.contains(target) || listRef.current?.contains(target)) return
        setOpen(false)
      }
      const onKeyDown = (event: KeyboardEvent) => {
        if (event.key === 'Escape') setOpen(false)
      }
      window.addEventListener('scroll', onScrollOrResize, true)
      window.addEventListener('resize', onScrollOrResize)
      document.addEventListener('mousedown', onPointerDown)
      document.addEventListener('keydown', onKeyDown)
      return () => {
        window.removeEventListener('scroll', onScrollOrResize, true)
        window.removeEventListener('resize', onScrollOrResize)
        document.removeEventListener('mousedown', onPointerDown)
        document.removeEventListener('keydown', onKeyDown)
      }
    }, [open, place])

    return (
      <div ref={ref} className={cn('flex w-full flex-col gap-1.5', className)} {...props}>
        {label && <label className="text-sm font-medium text-ink">{label}</label>}
        <div className="relative">
          <button
            ref={triggerRef}
            type="button"
            role="combobox"
            aria-expanded={open}
            aria-haspopup="listbox"
            disabled={disabled}
            onClick={() => !disabled && setOpen((v) => !v)}
            className={cn(
              'focus-ring flex w-full items-center justify-between gap-2 rounded-card border bg-surface px-3.5 py-2.5 text-sm text-ink transition-colors',
              'border-line hover:border-line-strong',
              error && 'border-crimson-500',
              disabled && 'cursor-not-allowed opacity-50',
            )}
          >
            <span className="truncate text-left">
              {selectedOption?.label || <span className="text-ink-soft/60">{placeholder}</span>}
            </span>
            <Icon
              name="chevron-down"
              className={cn('size-4 shrink-0 text-ink-soft transition-transform', open && 'rotate-180')}
            />
          </button>
        </div>

        {error ? (
          <p className="text-xs text-crimson-600">{error}</p>
        ) : hint ? (
          <p className="text-xs text-ink-soft">{hint}</p>
        ) : null}

        {open &&
          anchor &&
          typeof document !== 'undefined' &&
          createPortal(
            <div
              ref={listRef}
              role="listbox"
              style={{
                position: 'fixed',
                left: anchor.left,
                width: anchor.width,
                maxHeight: MENU_MAX_HEIGHT,
                ...(anchor.up
                  ? { bottom: window.innerHeight - anchor.top }
                  : { top: anchor.top }),
              }}
              className="z-[90] overflow-y-auto rounded-card border border-line bg-surface-2 p-1 shadow-card"
            >
              {options.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={selectedOption?.value === option.value}
                  disabled={option.disabled}
                  onClick={() => {
                    onChange?.(option.value)
                    setOpen(false)
                  }}
                  className={cn(
                    'focus-ring w-full rounded-[0.75rem] px-3 py-2.5 text-left text-sm transition-colors',
                    'hover:bg-surface-3 disabled:cursor-not-allowed disabled:text-ink-soft',
                    selectedOption?.value === option.value && 'bg-brand-50 font-medium text-brand-600',
                  )}
                >
                  {option.label}
                </button>
              ))}
            </div>,
            document.body,
          )}
      </div>
    )
  },
)

Select.displayName = 'Select'
