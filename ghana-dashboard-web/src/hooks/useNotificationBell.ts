import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchUnreadCount, openNotificationStream } from '@/api/notifications'
import { playNewOrderSound, soundEnabled } from '@/utils/sound'

export const NOTIFICATIONS_QUERY_KEY = ['notifications']

/**
 * What the bell shows.
 *
 * <p>The stream is a push channel for two things only: it tells the app something changed (and a
 * React Query invalidation updates the badge), and a brand-new order rings the shop's little
 * chime. The number itself always comes from the server's unread-count query, because push-driven
 * counters drift as soon as someone marks things read. A 30-second poll is the floor for days the
 * stream will not hold open.
 */
export function useNotificationBell() {
  const queryClient = useQueryClient()

  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: fetchUnreadCount,
    refetchInterval: 30_000,
  })

  useEffect(() => {
    let stop: (() => void) | undefined
    let cancelled = false
    void openNotificationStream(
      (item) => {
        if (item.type === 'NEW_ORDER' && soundEnabled()) {
          playNewOrderSound()
        }
        void queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY })
      },
      () => {
        void queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
      },
    ).then((dispose) => {
      if (cancelled) dispose()
      else stop = dispose
    })
    return () => {
      cancelled = true
      stop?.()
    }
  }, [queryClient])

  return { unreadCount: unread.data ?? 0 }
}

