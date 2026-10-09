import { useState, useRef, useEffect } from 'react'
import type { ReactNode } from 'react'
import { cn } from '@/utils/cn'

export interface DropdownMenuItem {
  id: string
  label: string
  icon?: ReactNode
  onClick?: () => void
  disabled?: boolean
  destructive?: boolean
}

export interface DropdownMenuProps {
  trigger: ReactNode
  items: DropdownMenuItem[]
  position?: 'bottom-start' | 'bottom-end' | 'top-start' | 'top-end'

}

export function DropdownMenu({
  trigger,
  items,
  position = 'bottom-end',

}: DropdownMenuProps) {
  const [open, setOpen] = useState(false)
  const triggerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (triggerRef.current && !triggerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }

    if (open) {
      document.addEventListener('mousedown', handleClickOutside)
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [open])

  const positionClasses = {
    'bottom-start': 'top-full left-0',
    'bottom-end': 'top-full right-0',
    'top-start': 'bottom-full left-0',
    'top-end': 'bottom-full right-0',
  }

  return (
    <div ref={triggerRef} className="relative inline-block">
      <div
        role="button"
        tabIndex={0}
        onClick={() => setOpen(!open)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            setOpen(!open)
          }
        }}
        className="cursor-pointer outline-none"
      >
        {trigger}
      </div>

      {open && (
        <div
          className={cn(
            'absolute z-50 mt-1 min-w-48 rounded-card border border-line bg-surface-2 shadow-card p-1',
            positionClasses[position],
          )}
          role="menu"
        >
          {items.map((item) => (
            <button
              key={item.id}
              type="button"
              role="menuitem"
              onClick={() => {
                item.onClick?.()
                setOpen(false)
              }}
              disabled={item.disabled}
              className={cn(
                'focus-ring flex w-full items-center gap-3 rounded-pill px-3 py-2 text-sm transition-colors',
                'disabled:cursor-not-allowed disabled:text-ink-soft',
                item.destructive
                  ? 'text-crimson-600 hover:bg-crimson-50'
                  : 'text-ink hover:bg-surface-3',
              )}
            >
              {item.icon && <span className="text-ink-soft">{item.icon}</span>}
              <span>{item.label}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

