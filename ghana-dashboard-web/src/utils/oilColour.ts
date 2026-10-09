/**
 * Oil-type palette for the dashboard donut. `oil_types.colour_tag` only carries a
 * family name, so the actual hues are pinned here to match the brand palette
 * (mustard/gold/leaf) rather than being invented per chart.
 */
const OIL_COLOURS: Record<string, string> = {
  mustard: '#D9A521',
  gold: '#E5C14F',
  white: '#EDE7D6',
  red: '#BF5B45',
  brand: '#3E8E5A',
}

const FALLBACK = '#A5A5A5'

export function oilColour(tag: string | null | undefined): string {
  return (tag && OIL_COLOURS[tag]) || FALLBACK
}
