export type RangePreset = 'thisMonth' | 'lastMonth' | 'last30' | 'custom'

export interface DateRange {
  from: string
  to: string
}

export function toISODate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function todayISO(): string {
  return toISODate(new Date())
}

export function presetRange(preset: Exclude<RangePreset, 'custom'>): DateRange {
  const now = new Date()
  if (preset === 'thisMonth') {
    return { from: toISODate(new Date(now.getFullYear(), now.getMonth(), 1)), to: toISODate(now) }
  }
  if (preset === 'lastMonth') {
    return {
      from: toISODate(new Date(now.getFullYear(), now.getMonth() - 1, 1)),
      to: toISODate(new Date(now.getFullYear(), now.getMonth(), 0)),
    }
  }
  const start = new Date(now)
  start.setDate(start.getDate() - 29)
  return { from: toISODate(start), to: toISODate(now) }
}

export function daysBetween(range: DateRange): number {
  const ms = new Date(range.to).getTime() - new Date(range.from).getTime()
  return Math.round(ms / 86_400_000) + 1
}

export function isValidRange(range: DateRange): boolean {
  return Boolean(range.from) && Boolean(range.to) && range.from <= range.to
}

export interface DateRangeValue {
  preset: RangePreset
  range: DateRange
}

export function initialRange(): DateRangeValue {
  return { preset: 'thisMonth', range: presetRange('thisMonth') }
}

