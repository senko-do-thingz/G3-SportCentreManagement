import { api } from './client'
import type { UserProfile } from './types'

export const avatarApi = {
  /** Uploads or replaces the current user's avatar. */
  async uploadAvatar(file: File): Promise<UserProfile> {
    const formData = new FormData()
    formData.append('file', file)
    return api.post<UserProfile>('/users/me/avatar', formData)
  },

  /** Retrieves the current user's avatar as a Blob. */
  async getAvatarBlob(): Promise<Blob> {
    const download = await api.getDownload('/users/me/avatar')
    return download.blob
  },

  /** Removes the current user's avatar. */
  async deleteAvatar(): Promise<void> {
    return api.delete<void>('/users/me/avatar')
  },
}
