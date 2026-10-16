import { api } from './client'
import { normalizePage } from './page'
import type { PageResponse, SpringPage } from './types'
import type { AccountStatus, ManagerUser, StaffAccountRequest, UsersQuery } from '../types/manager'

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
}
