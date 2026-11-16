import { useEffect, useState } from 'react'
import { avatarApi } from '../../api/avatarApi'
import { avatarSrc } from '../../api/avatarUrl'

/**
 * Resolves an avatar URL into an image src the browser can render.
 * For authenticated /users/me/avatar endpoints, it downloads the image as a Blob
 * with credentials attached and creates a temporary object URL.
 */
export function useAvatarSrc(url: string | null | undefined): string | null {
  const trimmed = url?.trim()
  const isDirect = Boolean(
    trimmed && (trimmed.startsWith('http://') || trimmed.startsWith('https://') || trimmed.startsWith('/images/')),
  )
  const isMeAvatar = Boolean(trimmed?.includes('/users/me/avatar'))

  const [blobUrl, setBlobUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!isMeAvatar) {
      return
    }

    let active = true
    let currentObjectUrl: string | null = null

    avatarApi
      .getAvatarBlob()
      .then((blob) => {
        if (!active) return
        currentObjectUrl = URL.createObjectURL(blob)
        setBlobUrl(currentObjectUrl)
      })
      .catch(() => {
        if (!active) return
        setBlobUrl(null)
      })

    return () => {
      active = false
      if (currentObjectUrl) {
        URL.revokeObjectURL(currentObjectUrl)
      }
    }
  }, [trimmed, isMeAvatar])

  if (!trimmed) {
    return null
  }
  if (isDirect) {
    return trimmed
  }
  if (isMeAvatar) {
    return blobUrl
  }
  return avatarSrc(trimmed)
}
