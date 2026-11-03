import { api } from './client'
import { TRAINING_API_ROOTS } from './trainingContract'
export interface LearningClass { id: number; name: string; sportName: string; coachName: string | null; level: string | null; capacity: number }
export interface ClassParticipation { bookingId: number; sessionId: number; date: string; startTime: string; endTime: string; bookingStatus: string; attendanceStatus: string | null; notes: string | null; resultId: number | null }
export interface ClassDetail { learningClass: LearningClass; sessions: ClassParticipation[] }
export interface ClassQuestion { id: number; classId: number; className: string; memberName: string; subject: string; description: string; status: string; createdAt: string }
export interface Discussion { question: ClassQuestion; messages: { id: number; senderName: string; body: string; createdAt: string }[] }
export interface Guidance { source: 'CATALOG_SEARCH'; message: string; classes: LearningClass[] }
const root = (coach: boolean) => coach ? TRAINING_API_ROOTS.coach : TRAINING_API_ROOTS.member
export const classLearningApi = {
  classes: (signal: AbortSignal) => api.get<LearningClass[]>(`${TRAINING_API_ROOTS.member}/classes`, { signal }),
  detail: (id: string, signal: AbortSignal) => api.get<ClassDetail>(`${TRAINING_API_ROOTS.member}/classes/${encodeURIComponent(id)}`, { signal }),
  questions: (coach: boolean, signal: AbortSignal) => api.get<ClassQuestion[]>(`${root(coach)}/questions`, { signal }),
  discussion: (coach: boolean, id: string, signal: AbortSignal) => api.get<Discussion>(`${root(coach)}/questions/${encodeURIComponent(id)}`, { signal }),
  ask: (body: { classId: number; subject: string; body: string }) => api.post<{ id: number }>(`${TRAINING_API_ROOTS.member}/questions`, body),
  reply: (coach: boolean, id: string, body: string) => api.post<{ id: number }>(`${root(coach)}/questions/${encodeURIComponent(id)}/messages`, { body }),
  guidance: (query: string, signal: AbortSignal) => api.get<Guidance>(`${TRAINING_API_ROOTS.member}/guidance`, { query: { query }, signal }),
}
