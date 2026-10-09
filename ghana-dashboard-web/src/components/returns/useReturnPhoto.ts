import { useEffect, useState } from 'react'
import { returnPhotoUrl } from '@/api/returns'

interface PhotoState {
  photoId: number
  url: string | null
  failed: boolean
}

const PENDING: PhotoState = { photoId: -1, url: null, failed: false }

/**
 * Resolves a protected photo to an object URL. The shared cache in
 * `@/api/returns` means the same photo is fetched once even when it shows in a
 * card thumbnail and again in the detail drawer. Results are tagged with the
 * photo id so a prop change can never show the previous photo's bytes.
 */
export function useReturnPhoto(returnId: number, photoId: number): {
  url: string | null
  failed: boolean
} {
  const [state, setState] = useState<PhotoState>(PENDING)

  useEffect(() => {
    let active = true
    returnPhotoUrl(returnId, photoId).then(
      (url) => {
        if (active) setState({ photoId, url, failed: false })
      },
      () => {
        if (active) setState({ photoId, url: null, failed: true })
      },
    )
    return () => {
      active = false
    }
  }, [returnId, photoId])

  if (state.photoId !== photoId) return { url: null, failed: false }
  return { url: state.url, failed: state.failed }
}

