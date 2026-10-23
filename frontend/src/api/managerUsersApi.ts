import { api } from './client'
import { normalizePage } from './page'
import type { PageResponse, SpringPage } from './types'
import type { AccountStatus, CoachProfileRequest, ManagerCoachProfile, ManagerUser, StaffAccountRequest, UsersQuery } from '../types/manager'

export const managerUsersApi = {
  async list(query: UsersQuery, signal?: AbortSignal): Promise<PageResponse<ManagerUser>> {
    const response = await api.get<PageResponse<ManagerUser> | SpringPage<ManagerUser>>('/manager/users', {
      query: { ...query }, signal,
    })
    if (!response || !Array.isArray(response.content)) throw new Error('The user service returned an unexpected response.')
    return normalizePage(response)
  },
  create(request: StaffAccountRequest): Promise<ManagerUser> {
    return api.post('/manager/users', request)
  },
  changeStatus(id: number, status: AccountStatus): Promise<ManagerUser> {
    return api.patch(`/manager/users/${id}/status`, { status })
  },
  async coachProfile(id: number, signal?: AbortSignal): Promise<ManagerCoachProfile> {
    const response = await api.get<ManagerCoachProfile>(`/manager/users/${id}/coach-profile`, { signal })
    if (!response || response.userId !== id || typeof response.isPublic !== 'boolean') throw new Error('The coach profile service returned an unexpected response.')
    return response
  },
  updateCoachProfile(id: number, request: CoachProfileRequest): Promise<ManagerCoachProfile> {
    return api.put(`/manager/users/${id}/coach-profile`, request)
  },
  /** Multipart upload of a prepared photo; saved immediately and returns the profile with the new photo link. */
  uploadCoachPhoto(id: number, photo: Blob): Promise<ManagerCoachProfile> {
    const body = new FormData()
    body.append('file', photo, 'photo.jpg')
    return api.post(`/manager/users/${id}/coach-profile/photo`, body)
  },
}
