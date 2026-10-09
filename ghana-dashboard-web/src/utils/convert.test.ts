import { describe, expect, it } from 'vitest'
import { kgToLitre, litreToKg, OIL_DENSITY_KG_PER_LITRE } from '@/utils/convert'

describe('oil volume/weight conversions', () => {
  it('converts litres to kg at the default density', () => {
    expect(litreToKg(100)).toBeCloseTo(91)
  })

  it('is its own inverse for a round trip', () => {
    expect(kgToLitre(litreToKg(55.5))).toBeCloseTo(55.5)
  })

  it('honours a custom density', () => {
    expect(litreToKg(100, 0.8)).toBeCloseTo(80)
    expect(kgToLitre(80, 0.8)).toBeCloseTo(100)
  })

  it('uses mustard oil density of 0.91 kg/L by default', () => {
    expect(OIL_DENSITY_KG_PER_LITRE).toBe(0.91)
  })
})


