import { api } from './client'

export const supportStatuses = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'] as const
export type SupportStatus = typeof supportStatuses[number]
export const supportCategories = ['GENERAL', 'FACILITY', 'BOOKING', 'MEMBERSHIP', 'PAYMENT'] as const
export interface SupportIntake { memberId: number; category: string; subject: string; description: string }
export interface SupportTicket extends SupportIntake {
  id: number; code: string; memberCode: string | null; memberName: string; status: SupportStatus
  resolutionNotes: string | null; sessionCode: string | null; createdAt: string; updatedAt: string
}
export const supportTransitions: Record<SupportStatus, SupportStatus[]> = {
  OPEN: ['IN_PROGRESS', 'CLOSED'], IN_PROGRESS: ['RESOLVED', 'CLOSED'], RESOLVED: ['CLOSED'], CLOSED: [],
}
export const receptionSupportApi = {
  list: (memberId: string, status: string, signal: AbortSignal) => api.get<SupportTicket[]>('/reception/support-requests', { signal, query: { memberId: memberId || undefined, status: status || undefined } }),
  detail: (id: number, signal: AbortSignal) => api.get<SupportTicket>(`/reception/support-requests/${id}`, { signal }),
  create: (body: SupportIntake, key: string) => api.post<SupportTicket>('/reception/support-requests', body, { headers: { 'Idempotency-Key': key } }),
  update: (id: number, status: SupportStatus, resolutionNotes: string) => api.put<SupportTicket>(`/reception/support-requests/${id}/status`, { status, resolutionNotes: resolutionNotes.trim() || null }),
}
