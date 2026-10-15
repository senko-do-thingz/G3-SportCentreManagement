import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE } from './memberApi'
import type { ManagerPackage, PackageCreateRequest, PackageUpdateDraft } from '../types/manager'

export const managerPackagesApi = {
  list(query: { sportId?: string; format?: string; active?: string }, signal?: AbortSignal): Promise<ManagerPackage[]> {
    return api.get<ManagerPackage[]>('/manager/packages', { query, signal }).then((items) => {
      if (!Array.isArray(items)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
      return items
    })
  },
  create(request: PackageCreateRequest): Promise<ManagerPackage> {
    return api.post('/packages', request)
  },
  update(id: number, request: PackageUpdateDraft): Promise<ManagerPackage> {
    return api.put(`/packages/${id}`, request)
  },
}
