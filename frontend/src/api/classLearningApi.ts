import { api } from './client'
export interface LearningClass { id: number; name: string; sportName: string; coachName: string | null; level: string | null; capacity: number; active: boolean }
export interface ClassParticipation { bookingId: number; sessionId: number; date: string; startTime: string; endTime: string; bookingStatus: string; attendanceStatus: string | null; notes: string | null; resultId: number | null; sessionStatus: string; trainingFormat: string; capacity: number; freeSeats: number }
export interface ClassDetail { learningClass: LearningClass; sessions: ClassParticipation[] }
export interface ClassQuestion { id: number; classId: number; className: string; memberName: string; subject: string; description: string; status: string; createdAt: string }
export interface Discussion { question: ClassQuestion; messages: { id: number; senderName: string; body: string; createdAt: string }[] }
export interface Guidance { source: 'CATALOG_SEARCH'; message: string; classes: LearningClass[] }
const root = (coach: boolean) => coach ? '/coach/training' : '/member/training'
export const classLearningApi = {
  classes: (signal: AbortSignal) => api.get<LearningClass[]>('/member/training/classes', { signal }),
  detail: (id: string, signal: AbortSignal) => api.get<ClassDetail>(`/member/training/classes/${encodeURIComponent(id)}`, { signal }),
  questions: (coach: boolean, signal: AbortSignal) => api.get<ClassQuestion[]>(`${root(coach)}/questions`, { signal }),
  discussion: (coach: boolean, id: string, signal: AbortSignal) => api.get<Discussion>(`${root(coach)}/questions/${encodeURIComponent(id)}`, { signal }),
  ask: (body: { classId: number; subject: string; body: string }, operationId: string) => api.post<{ id: number }>('/member/training/questions', body, { headers: { 'Idempotency-Key': operationId } }),
  reply: (coach: boolean, id: string, body: string, operationId: string) => api.post<{ id: number }>(`${root(coach)}/questions/${encodeURIComponent(id)}/messages`, { body }, { headers: { 'Idempotency-Key': operationId } }),
  guidance: (query: string, signal: AbortSignal) => api.get<Guidance>('/member/training/guidance', { query: { query }, signal }),
}
