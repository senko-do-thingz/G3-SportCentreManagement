import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE } from './memberApi'
import type { CardTierUpdateDraft, ManagerCardTier } from '../types/manager'

export const managerCardTiersApi = {
  list(signal?: AbortSignal): Promise<ManagerCardTier[]> {
    return api.get<ManagerCardTier[]>('/manager/card-tiers', { signal }).then((items) => {
      if (!Array.isArray(items)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
      return items
    })
  },
  update(id: number, request: CardTierUpdateDraft): Promise<ManagerCardTier> {
    return api.put(`/membership-cards/tiers/${id}`, request)
  },
}
