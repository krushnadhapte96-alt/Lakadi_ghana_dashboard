export const OIL_DENSITY_KG_PER_LITRE = 0.91

export function litreToKg(litres: number, density = OIL_DENSITY_KG_PER_LITRE): number {
  return litres * density
}

export function kgToLitre(kg: number, density = OIL_DENSITY_KG_PER_LITRE): number {
  return kg / density
}
