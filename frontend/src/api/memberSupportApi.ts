import { api } from './client'
import type { SupportStatus } from './receptionSupportApi'

export const memberSupportTopics = [
  { value: 'GENERAL', label: 'General question' },
  { value: 'MEMBERSHIP', label: 'Package & membership' },
  { value: 'PAYMENT', label: 'Payment & pricing' },
  { value: 'BOOKING', label: 'Schedule & booking' },
  { value: 'FACILITY', label: 'Facility' },
] as const

export interface MemberSupportIntake {
  category: string; subject: string; description: string; bookingId?: number; assistantExcerpt?: string
}
export interface MemberSupportReply { message: string; createdAt: string }
export interface MemberSupportTicket {
  id: number; code: string; category: string; subject: string; description: string; status: SupportStatus
  sessionCode: string | null; submittedByMe: boolean; createdAt: string; updatedAt: string
  unreadReplies: number; replies: MemberSupportReply[]
}

/** Member side of Flow 6 support: the Member's own requests only. */
export const memberSupportApi = {
  list: (signal: AbortSignal) => api.get<MemberSupportTicket[]>('/member/support-requests', { signal }),
  detail: (id: number, signal: AbortSignal) => api.get<MemberSupportTicket>(`/member/support-requests/${id}`, { signal }),
  create: (body: MemberSupportIntake, key: string) => api.post<MemberSupportTicket>('/member/support-requests', body, { headers: { 'Idempotency-Key': key } }),
  markRead: (id: number) => api.put<void>(`/member/support-requests/${id}/read`, {}),
}
