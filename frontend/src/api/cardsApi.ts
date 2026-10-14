import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE, type MemberCard, type RequestSignal } from './memberApi'

/**
 * Membership card endpoints of MembershipCardController (FE-04, Figma F1-11, F1-12).
 * Types mirror the backend DTOs on main; BigDecimal values arrive as numbers.
 */

/** Mirrors com.sportify.catalog.dto.CardTierResponse (GET /membership-cards/tiers, public, active tiers only). */
export interface CardTier {
  id: number
  code: string
  name: string
  price: number | string
  /** 0 or null for the free, permanent Standard tier. */
  durationMonths: number | null
  discountPercentage: number | null
  description: string | null
  isActive: boolean | null
}

/**
 * Body of POST /membership-cards/purchase. For a MEMBER the backend ignores memberId, uses the
 * signed in member, creates a PENDING_PAYMENT card and a PENDING payment (BE-17).
 * paymentMethod is optional; the backend default is BANK_TRANSFER.
 */
export interface CardPurchaseRequest {
  tierId: number
}

function asList<T>(value: unknown): T[] {
  if (!Array.isArray(value)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as T[]
}

function asCard(value: unknown): MemberCard {
  if (!value || typeof value !== 'object' || typeof (value as MemberCard).id !== 'number') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as MemberCard
}

export const cardsApi = {
  tiers(options: RequestSignal = {}): Promise<CardTier[]> {
    return api.get<unknown>('/membership-cards/tiers', options).then(asList<CardTier>)
  },

  /** 201 with the new card. 400 (BusinessRuleException) when a request already waits for payment. */
  purchase(request: CardPurchaseRequest): Promise<MemberCard> {
    return api.post<unknown>('/membership-cards/purchase', request).then(asCard)
  },

  /** Cancels a PENDING_PAYMENT request. 400 for any other status, 404 for a card of another member. */
  cancel(cardId: number): Promise<MemberCard> {
    return api.put<unknown>(`/membership-cards/${cardId}/cancel`).then(asCard)
  },
}
