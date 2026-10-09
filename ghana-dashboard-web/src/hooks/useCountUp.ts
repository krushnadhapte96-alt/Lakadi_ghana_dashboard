import { useEffect, useRef, useState } from 'react'

export function useCountUp(target: number, durationMs = 600): number {
  const [value, setValue] = useState(0)
  const current = useRef(0)

  useEffect(() => {
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const duration = reduced ? 0 : durationMs
    const begin = current.current
    const start = performance.now()
    let frame = 0

    const tick = (now: number) => {
      const progress = duration === 0 ? 1 : Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - progress, 3)
      current.current = begin + (target - begin) * eased
      setValue(current.current)
      if (progress < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, durationMs])

  return value
}

