import { api } from './client'
import { ApiError } from './errors'
import { isManagerPreviewSession } from './managerPreviewSession'
import type { ManagerPackage, PackageCreateRequest, PackageUpdateDraft } from '../types/manager'

export const PACKAGE_DEPENDENCY = 'Package management is waiting for the BE-03 manager API contract. Try again after the service is available.'

export const managerPackagesApi = {
  list(query: { sportId?: string; format?: string; active?: string }, signal?: AbortSignal): Promise<ManagerPackage[]> {
    if (!isManagerPreviewSession()) return Promise.reject(new ApiError(503, PACKAGE_DEPENDENCY))
    return api.get('/manager/packages', { query, signal })
  },
  create(request: PackageCreateRequest): Promise<ManagerPackage> {
    return api.post('/packages', request)
  },
  update(id: number, request: PackageUpdateDraft): Promise<ManagerPackage> {
    if (!isManagerPreviewSession()) return Promise.reject(new ApiError(503, PACKAGE_DEPENDENCY))
    return api.put(`/packages/${id}`, request)
  },
}
