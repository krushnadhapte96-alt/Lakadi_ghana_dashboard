import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { searchAll, GROUP_LABELS } from '@/api/search'
import type { SearchItem } from '@/api/search'
import { entryHref } from '@/utils/entryPoint'
import { Icon } from '@/utils/icons'
import type { IconName } from '@/utils/icons'
import { cn } from '@/utils/cn'
import { useLanguage } from '@/i18n'

const RECENTS_KEY = 'ghanadash.recent-searches'
const MAX_RECENTS = 6

interface Recent extends SearchItem {
  group: string
}

interface QuickAction {
  labelKey: string
  link: string
  icon: IconName
}

const QUICK_ACTIONS: QuickAction[] = [
  { labelKey: 'home.quick.sale', link: entryHref('/sales', 'sale'), icon: 'sale' },
  { labelKey: 'home.quick.order', link: entryHref('/sales', 'order'), icon: 'truck' },
  { labelKey: 'home.quick.batch', link: entryHref('/production', 'batch'), icon: 'production' },
  { labelKey: 'home.quick.purchase', link: entryHref('/beej', 'purchase'), icon: 'beej' },
  { labelKey: 'home.quick.credit', link: entryHref('/khata', 'credit'), icon: 'khata' },
  { labelKey: 'home.quick.expense', link: entryHref('/expenses', 'expense'), icon: 'expenses' },
  { labelKey: 'palette.quick.dashboard', link: '/', icon: 'home' },
  { labelKey: 'palette.quick.licences', link: '/licences', icon: 'licences' },
]

function readRecents(): Recent[] {
  try {
    const raw = localStorage.getItem(RECENTS_KEY)
    return raw ? (JSON.parse(raw) as Recent[]) : []
  } catch {
    return []
  }
}

function rememberRecent(item: SearchItem, group: string) {
  try {
    const existing = readRecents().filter((recent) => recent.link !== item.link)
    const next = [{ ...item, group }, ...existing].slice(0, MAX_RECENTS)
    localStorage.setItem(RECENTS_KEY, JSON.stringify(next))
  } catch {
    // Private mode: recents simply do not persist.
  }
}

/**
 * The Ctrl+K box. What is searchable matches the screens exactly - bills, products, orders,
 * customers, khata, batches, suppliers - and every row jumps straight to the screen that owns it.
 *
 * <p>With a blank box the palette is not empty; it shows recents and the quick actions, because in
 * a shop the two things someone does most are find a thing they found before and start a sale.
 */
export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useLanguage()
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const [recents, setRecents] = useState<Recent[]>(() => readRecents())
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()

  const debounced = useDebouncedValue(query.trim(), 250)
  const result = useQuery({
    queryKey: ['search', debounced],
    queryFn: () => searchAll(debounced),
    enabled: debounced.length > 0,
    staleTime: 30_000,
  })

  // Flat in render order, so arrow keys never skip a group boundary they could not see.
  const rows = useMemo(() => {
    const list: Array<{ item: SearchItem; group: string }> = []
    if (debounced.length === 0) {
      for (const recent of recents) {
        list.push({ item: recent, group: recent.group })
      }
      return list
    }
    for (const group of result.data?.groups ?? []) {
      for (const item of group.items) {
        list.push({ item, group: group.group })
      }
    }
    return list
  }, [debounced, recents, result.data])

  // Each row's flat position is derived from its object identity, which avoids walking a shared
  // counter while rendering nested groups.
  const indexByItem = useMemo(() => new Map(rows.map((row, index) => [row.item, index])), [rows])

  const close = useCallback(() => {
    setQuery('')
    setActiveIndex(0)
    onClose()
  }, [onClose])

  useEffect(() => {
    if (open) {
      window.setTimeout(() => inputRef.current?.focus(), 30)
    }
  }, [open])

  const choose = useCallback(
    (item: SearchItem, group: string) => {
      rememberRecent(item, group)
      setRecents(readRecents())
      navigate(item.link)
      close()
    },
    [navigate, close],
  )

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((index) => Math.min(index + 1, rows.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((index) => Math.max(index - 1, 0))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      const row = rows[activeIndex]
      if (row) {
        choose(row.item, row.group)
      }
    } else if (event.key === 'Escape') {
      close()
    }
  }

  if (!open) {
    return null
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 p-4 pt-[12vh]" onClick={close}>
      <div
        className="w-full max-w-xl overflow-hidden rounded-card border border-line bg-surface-2 shadow-card"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center gap-3 border-b border-line px-4">
          <Icon name="search" className="size-4 text-ink-soft" />
          <input
            ref={inputRef}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setActiveIndex(0)
            }}
            onKeyDown={onKeyDown}
            placeholder={t('palette.placeholder')}
            className="h-12 flex-1 bg-transparent text-sm text-ink outline-none placeholder:text-ink-soft"
          />
          <kbd className="rounded border border-line px-1.5 py-0.5 text-[10px] text-ink-soft">ESC</kbd>
        </div>

        <div className="max-h-[24rem] overflow-y-auto p-1">
          {debounced.length === 0 ? (
            <>
              {recents.length > 0 && (
                <>
                  <SectionLabel>{t('palette.recents')}</SectionLabel>
                  {recents.map((recent) => {
                    const index = indexByItem.get(recent) ?? -1
                    return (
                      <Row
                        key={`recent-${recent.link}`}
                        item={recent}
                        icon="search"
                        active={activeIndex === index}
                        group={GROUP_LABELS[recent.group] ?? recent.group}
                        onChoose={() => choose(recent, recent.group)}
                        onHover={() => setActiveIndex(index)}
                      />
                    )
                  })}
                </>
              )}
              <SectionLabel>{t('palette.quickActions')}</SectionLabel>
              {QUICK_ACTIONS.map((action) => (
                <button
                  key={action.link}
                  type="button"
                  onClick={() => {
                    navigate(action.link)
                    close()
                  }}
                  className="flex w-full items-center gap-3 rounded-card px-3 py-2 text-sm text-ink hover:bg-surface-3"
                >
                  <Icon name={action.icon} className="size-4 text-ink-soft" />
                  {t(action.labelKey)}
                </button>
              ))}
            </>
          ) : result.isFetching && !result.data ? (
            <p className="px-3 py-8 text-center text-sm text-ink-soft">{t('common.loading')}</p>
          ) : result.data && result.data.total > 0 ? (
            <>
              {result.data.groups.map((group) => (
                <div key={group.group}>
                  <SectionLabel>
                    {GROUP_LABELS[group.group] ?? group.group}
                    {group.hasMore ? ` · ${t('palette.more')}` : ''}
                  </SectionLabel>
                  {group.items.map((item) => {
                    const index = indexByItem.get(item) ?? -1
                    return (
                      <Row
                        key={`${group.group}-${item.id}`}
                        item={item}
                        icon={groupIcon(group.group)}
                        active={activeIndex === index}
                        onChoose={() => choose(item, group.group)}
                        onHover={() => setActiveIndex(index)}
                      />
                    )
                  })}
                </div>
              ))}
            </>
          ) : (
            <p className="px-3 py-8 text-center text-sm text-ink-soft">{t('palette.noResults')}</p>
          )}
        </div>

        <div className="flex items-center justify-between border-t border-line px-4 py-2 text-[11px] text-ink-soft">
          <span>{t('palette.hint.navigate')}</span>
          <span>{t('palette.hint.open')}</span>
        </div>
      </div>
    </div>
  )
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-ink-soft">{children}</p>
  )
}

function Row({
  item,
  icon,
  active,
  group,
  onChoose,
  onHover,
}: {
  item: SearchItem
  icon: IconName
  active: boolean
  group?: string
  onChoose: () => void
  onHover: () => void
}) {
  return (
    <button
      type="button"
      onClick={onChoose}
      onMouseEnter={onHover}
      className={cn(
        'flex w-full items-center gap-3 rounded-card px-3 py-2 text-left',
        active ? 'bg-surface-3' : '',
      )}
    >
      <Icon name={icon} className="size-4 text-ink-soft" />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium text-ink">{item.title}</span>
        <span className="block truncate text-xs text-ink-soft">{item.subtitle}</span>
      </span>
      {group && <span className="text-[11px] text-ink-soft">{group}</span>}
    </button>
  )
}

function groupIcon(group: string): IconName {
  switch (group) {
    case 'BILL_NUMBER':
      return 'note'
    case 'PRODUCTS':
      return 'stock'
    case 'SALES':
      return 'sale'
    case 'ORDERS':
      return 'truck'
    case 'CUSTOMERS':
      return 'customers'
    case 'KHATA_PARTIES':
      return 'khata'
    case 'BATCHES':
      return 'beej'
    case 'SUPPLIERS':
      return 'truck'
    default:
      return 'search'
  }
}

function useDebouncedValue<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(id)
  }, [value, delay])
  return debounced
}

