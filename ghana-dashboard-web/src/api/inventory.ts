import { api } from '@/api/axios'
import type { ApiResponse } from '@/api/types'

export type ProductUnit = 'L' | 'ML' | 'KG'
export type ProductContainer = 'glass' | 'plastic' | 'tin' | 'bag'
export type PackagingType = 'bottle' | 'cap' | 'label' | 'tin' | 'bag' | 'carton'

export const PRODUCT_UNITS: ProductUnit[] = ['L', 'ML', 'KG']
export const PRODUCT_CONTAINERS: ProductContainer[] = ['glass', 'plastic', 'tin', 'bag']
export const PACKAGING_TYPES: PackagingType[] = ['bottle', 'cap', 'label', 'tin', 'bag', 'carton']

export interface OilType {
  id: number
  nameEn: string
  nameHi: string
  density: number
  bestBeforeDays: number
  colourTag: string
  active: boolean
}

export interface Product {
  id: number
  shopId: number
  oilTypeId: number | null
  oilTypeName: string | null
  name: string
  sku: string
  packSize: number
  unit: ProductUnit
  container: ProductContainer
  sellingPrice: number
  mrp: number
  costPrice: number
  lowStockThreshold: number
  active: boolean
  isKhali: boolean
}

export interface InventoryItem {
  productId: number
  name: string
  sku: string
  oilTypeName: string | null
  packSize: number
  unit: string
  container: string
  isKhali: boolean
  qtyAvailable: number
  qtyReserved: number
  sellingPrice: number
  costPrice: number
  lowStockThreshold: number
  lowStock: boolean
  active: boolean
}

export interface AggregatedInventory {
  oilProducts: InventoryItem[]
  khaliProducts: InventoryItem[]
  packagingItems: PackagingItem[]
}

export interface PackagingItem {
  id: number
  shopId: number
  name: string
  type: PackagingType
  size: string
  stockQty: number
  costPerUnit: number
  lowThreshold: number
  active: boolean
  lowStock: boolean
}

export interface StockMovement {
  id: number
  productId: number
  productName: string
  batchId: string | null
  changeQty: number
  type: string
  unitPrice: number | null
  totalValue: number | null
  reason: string | null
  note: string | null
  referenceType: string | null
  referenceId: number | null
  userName: string | null
  createdAt: string
}

export interface PackagingMovement {
  id: number
  itemId: number
  itemName: string
  itemType: string
  changeQty: number
  type: string
  unitCost: number | null
  totalCost: number | null
  reason: string | null
  note: string | null
  referenceType: string | null
  referenceId: number | null
  userName: string | null
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  empty: boolean
}

export interface ProductInput {
  oilTypeId: number | null
  name: string
  packSize: number
  unit: ProductUnit
  container: ProductContainer
  sellingPrice: number
  mrp: number
  costPrice: number
  lowStockThreshold: number
  isKhali: boolean
}

export interface PackagingItemInput {
  name: string
  type: PackagingType
  size: string
  stockQty: number
  costPerUnit: number
  lowThreshold: number
}

function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success) throw new Error(res.message)
  return res.data
}

export async function fetchOilTypes(): Promise<OilType[]> {
  const { data } = await api.get<ApiResponse<OilType[]>>('/oil-types')
  return unwrap(data)
}

export async function fetchProducts(): Promise<Product[]> {
  const { data } = await api.get<ApiResponse<Product[]>>('/products')
  return unwrap(data)
}

export async function fetchAggregatedInventory(): Promise<AggregatedInventory> {
  const { data } = await api.get<ApiResponse<AggregatedInventory>>('/inventory')
  return unwrap(data)
}

export async function fetchStockMovements(page = 0, size = 50): Promise<Page<StockMovement>> {
  const { data } = await api.get<ApiResponse<Page<StockMovement>>>('/inventory/movements', {
    params: { page, size },
  })
  return unwrap(data)
}

export async function fetchPackagingItems(): Promise<PackagingItem[]> {
  const { data } = await api.get<ApiResponse<PackagingItem[]>>('/packaging-items')
  return unwrap(data)
}

export async function fetchPackagingMovements(itemId: number): Promise<PackagingMovement[]> {
  const { data } = await api.get<ApiResponse<PackagingMovement[]>>(`/packaging-items/${itemId}/movements`)
  return unwrap(data)
}

export async function createProduct(input: ProductInput): Promise<Product> {
  const { data } = await api.post<ApiResponse<Product>>('/products', input)
  return unwrap(data)
}

export async function updateProduct(id: number, input: ProductInput): Promise<Product> {
  const { data } = await api.put<ApiResponse<Product>>(`/products/${id}`, input)
  return unwrap(data)
}

export async function deleteProduct(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`/products/${id}`)
  if (!data.success) throw new Error(data.message)
}

export async function updateProductPrice(id: number, sellingPrice: number, mrp?: number): Promise<Product> {
  const { data } = await api.patch<ApiResponse<Product>>(`/products/${id}/price`, {
    sellingPrice,
    mrp: mrp ?? sellingPrice,
  })
  return unwrap(data)
}

export async function updateProductThreshold(id: number, lowStockThreshold: number): Promise<Product> {
  const { data } = await api.patch<ApiResponse<Product>>(`/products/${id}/threshold`, { lowStockThreshold })
  return unwrap(data)
}

export async function addOpeningStock(
  id: number,
  input: { qty: number; costPrice: number; receivedDate: string; expiryDate: string | null },
): Promise<void> {
  const { data } = await api.post<ApiResponse<null>>(`/inventory/${id}/opening-stock`, input)
  if (!data.success) throw new Error(data.message)
}

export async function adjustStock(id: number, input: { qty: number; reason: string; note: string }): Promise<void> {
  const { data } = await api.post<ApiResponse<null>>(`/inventory/${id}/adjust`, input)
  if (!data.success) throw new Error(data.message)
}

export async function createPackagingItem(input: PackagingItemInput): Promise<PackagingItem> {
  const { data } = await api.post<ApiResponse<PackagingItem>>('/packaging-items', input)
  return unwrap(data)
}

export async function updatePackagingItem(id: number, input: PackagingItemInput): Promise<PackagingItem> {
  const { data } = await api.put<ApiResponse<PackagingItem>>(`/packaging-items/${id}`, input)
  return unwrap(data)
}

export async function deletePackagingItem(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`/packaging-items/${id}`)
  if (!data.success) throw new Error(data.message)
}

export async function recordPackagingPurchase(
  id: number,
  input: { qty: number; unitCost: number; purchaseDate: string; note: string },
): Promise<void> {
  const { data } = await api.post<ApiResponse<null>>(`/packaging-items/${id}/purchase`, input)
  if (!data.success) throw new Error(data.message)
}

export async function adjustPackagingStock(
  id: number,
  input: { qty: number; reason: string; note: string },
): Promise<void> {
  const { data } = await api.post<ApiResponse<null>>(`/packaging-items/${id}/adjust`, input)
  if (!data.success) throw new Error(data.message)
}