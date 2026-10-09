import { api, API_BASE_URL } from '@/api/axios'
import type { ApiResponse } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

export interface NotificationItem {
  id: number
  type: string
  severity: 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL'
  title: string
  message: string | null
  link: string | null
  createdAt: string
  read: boolean
}

export interface NotificationList {
  items: NotificationItem[]
  total: number
  page: number
  size: number
  unreadCount: number
}

export interface UnreadCount {
  count: number
}

export async function fetchNotifications(params?: {
  type?: string
  unreadOnly?: boolean
  page?: number
  size?: number
}): Promise<NotificationList> {
  const { data } = await api.get<ApiResponse<NotificationList>>('/notifications', { params })
  if (!data.success) throw new Error(data.message)
  return data.data
}

export async function fetchUnreadCount(): Promise<number> {
  const { data } = await api.get<ApiResponse<UnreadCount>>('/notifications/unread-count')
  if (!data.success) throw new Error(data.message)
  return data.data.count
}

export async function markNotificationRead(id: number): Promise<void> {
  const { data } = await api.post<ApiResponse<unknown>>(`/notifications/${id}/read`)
  if (!data.success) throw new Error(data.message)
}

export async function markAllNotificationsRead(): Promise<void> {
  const { data } = await api.post<ApiResponse<unknown>>('/notifications/read-all')
  if (!data.success) throw new Error(data.message)
}

/**
 * Live push from the bell's stream. EventSource cannot carry the Authorization header this API
 * expects, so the stream is read with fetch instead; if it ever breaks, the bell falls back to
 * polling and nothing is lost.
 */
export async function openNotificationStream(
  onNotification: (item: NotificationItem & { type: string }) => void,
  onServerCount: (count: number) => void,
): Promise<() => void> {
  const controller = new AbortController()
  let cancelled = false

  ;(async () => {
    try {
      const token = useAuthStore.getState().accessToken
      const response = await fetch(`${API_BASE_URL}/notifications/stream`, {
        headers: {
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        credentials: 'include',
        signal: controller.signal,
      })
      if (!response.ok || !response.body) return

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done || cancelled) return
        buffer += decoder.decode(value, { stream: true })
        // SSE frames are separated by a blank line.
        let boundary = buffer.indexOf('\n\n')
        while (boundary !== -1) {
          const frame = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          handleFrame(frame)
          boundary = buffer.indexOf('\n\n')
        }
      }
    } catch {
      // Aborted or the server dropped us; the polling fallback has the unread count covered.
    }
  })()

  function handleFrame(frame: string) {
    let event = 'message'
    let payload = ''
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) payload += line.slice(5).trim()
    }
    try {
      const parsed = JSON.parse(payload)
      if (event === 'connected' && typeof parsed.unreadCount === 'number') {
        onServerCount(parsed.unreadCount)
      } else if (event === 'notification') {
        onNotification(parsed)
      }
    } catch {
      // A frame we cannot parse must not kill the stream.
    }
  }

  return () => {
    cancelled = true
    controller.abort()
  }
}

