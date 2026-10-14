import { api } from './client'
import { ApiError } from './errors'
import { isManagerPreviewSession } from './managerPreviewSession'
import type { CardTierUpdateDraft, ManagerCardTier } from '../types/manager'

export const CARD_DEPENDENCY = 'Card management is waiting for the BE-03 manager API contract. Try again after the service is available.'

export const managerCardTiersApi = {
  list(signal?: AbortSignal): Promise<ManagerCardTier[]> {
    if (!isManagerPreviewSession()) return Promise.reject(new ApiError(503, CARD_DEPENDENCY))
    return api.get('/manager/card-tiers', { signal })
  },
  update(id: number, request: CardTierUpdateDraft): Promise<ManagerCardTier> {
    if (!isManagerPreviewSession()) return Promise.reject(new ApiError(503, CARD_DEPENDENCY))
    return api.put(`/membership-cards/tiers/${id}`, request)
  },
}
