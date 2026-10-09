import { api } from '@/api/axios'

export async function fetchBlob(
  url: string,
  params?: Record<string, string | undefined>,
): Promise<Blob> {
  const { data } = await api.get<Blob>(url, { params, responseType: 'blob' })
  return data
}

export function saveBlob(blob: Blob, filename: string): void {
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}

export function viewBlob(blob: Blob, target: Window | null, fallbackName: string): void {
  if (!target) {
    saveBlob(blob, fallbackName)
    return
  }
  const objectUrl = URL.createObjectURL(blob)
  target.location.href = objectUrl
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000)
}

