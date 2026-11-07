import { api } from './client'

export const supportStatuses = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'] as const
export type SupportStatus = typeof supportStatuses[number]
export const supportCategories = ['GENERAL', 'FACILITY', 'BOOKING', 'MEMBERSHIP', 'PAYMENT'] as const
export interface SupportIntake { memberId: number; category: string; subject: string; description: string }
export interface SupportReply { id: number; message: string; staffName: string; createdAt: string }
export interface SupportTicket extends SupportIntake {
  id: number; code: string; memberCode: string | null; memberName: string; status: SupportStatus
  /** Internal staff note; never shown to the Member. */
  resolutionNotes: string | null; sessionCode: string | null; createdAt: string; updatedAt: string
  memberSubmitted: boolean; replyCount: number; replies: SupportReply[]
}
export interface SupportSummary { open: number; inProgress: number; resolved: number; closed: number }
export interface SupportVerification {
  memberCode: string | null; memberName: string; memberStatus: string; cardTier: string | null; cardDiscount: number | null
  cardEndDate: string | null; activePackages: { name: string; sportName: string; remainingSessions: number; totalSessions: number; endDate: string }[]
}
export const supportTransitions: Record<SupportStatus, SupportStatus[]> = {
  OPEN: ['IN_PROGRESS', 'CLOSED'], IN_PROGRESS: ['RESOLVED', 'CLOSED'], RESOLVED: ['CLOSED'], CLOSED: [],
}
export const receptionSupportApi = {
  list: (memberId: string, status: string, signal: AbortSignal, q = '') => api.get<SupportTicket[]>('/reception/support-requests', { signal, query: { memberId: memberId || undefined, status: status || undefined, q: q.trim() || undefined } }),
  summary: (signal: AbortSignal) => api.get<SupportSummary>('/reception/support-requests/summary', { signal }),
  detail: (id: number, signal: AbortSignal) => api.get<SupportTicket>(`/reception/support-requests/${id}`, { signal }),
  verification: (id: number, signal: AbortSignal) => api.get<SupportVerification>(`/reception/support-requests/${id}/verification`, { signal }),
  create: (body: SupportIntake, key: string) => api.post<SupportTicket>('/reception/support-requests', body, { headers: { 'Idempotency-Key': key } }),
  reply: (id: number, message: string) => api.post<SupportTicket>(`/reception/support-requests/${id}/replies`, { message: message.trim() }),
  update: (id: number, status: SupportStatus, resolutionNotes: string) => api.put<SupportTicket>(`/reception/support-requests/${id}/status`, { status, resolutionNotes: resolutionNotes.trim() || null }),
}
