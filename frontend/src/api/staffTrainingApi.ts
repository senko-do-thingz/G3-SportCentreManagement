import { api } from './client'
import { parseSessionId, type SessionId } from './sessionIdentity'
import type { TrainingPlan, TrainingSession, TrainingStep } from './memberTrainingApi'

export interface TrainingClass { id: number; name: string; sportId: number }
export interface BookingCandidate {
  bookingId: number; memberId: number; memberName: string; sessionDate: string
}
export interface StaffPlan extends TrainingPlan { classId: number; memberId: number; memberName: string }
export interface StaffSession extends TrainingSession { classId: number | null; sportId: number; trainingType: string; attendanceDateOpen?: boolean; status?: string; bookedCount?: number; markedCount?: number; attendanceState?: 'UPCOMING' | 'OPEN' | 'CLOSED' }
export interface SessionMember {
  bookingId: number; memberId: number; memberName: string; attendanceId: number | null; status: string | null; notes: string | null; resultId?: number | null
}
export interface TrainingMetric { id: number; name: string; maxScore: number }
export interface CreatePlan {
  bookingId: number; classId: number; memberId: number; title: string; description: string | null
  steps: { title: string; durationMinutes: number; instructions: string | null }[]
}
export interface CreateFeedback { rating: number; comments: string; strengths: string | null; areasForImprovement: string | null }
export interface CreateResult { sessionId: number; memberId: number; notes: string | null; scores: { metricId: number; score: number }[] }
export type AttendanceStatus = 'PRESENT' | 'LATE' | 'ABSENT' | 'EXCUSED'
export interface AttendanceInput { status: AttendanceStatus; notes: string | null }
export interface DrillDraft { title: string; goal: string; durationMinutes: number; instructions: string; reason: string; level: string }
export interface DrillSuggestions {
  /** RULE_BASED: curated catalog. AI_GENERATED: Gemini drafts (ruleVersion holds the model id); both need Coach review. */
  mode: 'RULE_BASED' | 'AI_GENERATED'; ruleVersion: string; requiresCoachReview: true; persisted: false
  bookingId: number; memberId: number; classId: number; sportName: string; level: string; drafts: DrillDraft[]
}
/** Whole-class preview: drafts built from class counts and averages only; nothing is saved. */
export interface ClassDrillSuggestions {
  mode: 'RULE_BASED' | 'AI_GENERATED'; ruleVersion: string; requiresCoachReview: true; persisted: false
  classId: number; sportName: string; level: string; memberCount: number; drafts: DrillDraft[]
}
/** Optional Coach inputs for a drill preview; omitted values are not sent. */
export interface DrillOptions { focus?: string; sessionMinutes?: number }
export interface CreateClassPlan {
  classId: number; title: string; description: string | null
  steps: { title: string; durationMinutes: number; instructions: string | null }[]
}

const drillQuery = (options: DrillOptions = {}) => ({ focus: options.focus || undefined, sessionMinutes: options.sessionMinutes })
const coach = '/coach/training'
export const staffTrainingApi = {
  coachClasses: (signal: AbortSignal) => api.get<TrainingClass[]>(`${coach}/classes`, { signal }),
  bookingCandidates: (classId: string, signal: AbortSignal) => api.get<BookingCandidate[]>(`${coach}/classes/${classId}/members`, { signal }),
  drillSuggestions: (bookingId: number, options?: DrillOptions) => api.get<DrillSuggestions>(`${coach}/bookings/${bookingId}/drill-suggestions`, { query: drillQuery(options) }),
  classDrillSuggestions: (classId: number, options?: DrillOptions) => api.get<ClassDrillSuggestions>(`${coach}/classes/${classId}/drill-suggestions`, { query: drillQuery(options) }),
  createClassPlan: (body: CreateClassPlan, operationKey: string) => api.post<{ plan: { id: number } }>(`${coach}/class-plans`, body, { headers: { 'Idempotency-Key': operationKey } }),
  plans: (signal: AbortSignal) => api.get<StaffPlan[]>(`${coach}/plans`, { signal }),
  plan: (id: string, signal: AbortSignal) => api.get<{ plan: StaffPlan; steps: TrainingStep[] }>(`${coach}/plans/${id}`, { signal }),
  createPlan: (body: CreatePlan) => api.post<{ id: number }>(`${coach}/plans`, body),
  feedback: (id: number, body: CreateFeedback) => api.post<{ id: number }>(`${coach}/plans/${id}/feedback`, body),
  coachSessions: (signal: AbortSignal) => api.get<StaffSession[]>(`${coach}/sessions`, { signal }),
  coachRoster: (id: string, signal: AbortSignal) => api.get<SessionMember[]>(`${coach}/sessions/${/^\d+$/.test(id) ? id : `by-code/${parseSessionId(id)}`}/members`, { signal }),
  metrics: (sportId: number, signal: AbortSignal) => api.get<TrainingMetric[]>(`${coach}/metrics`, { query: { sportId }, signal }),
  result: (body: CreateResult) => api.post<{ id: number }>(`${coach}/results`, body),
  resultByCode: (sessionId: SessionId, body: Omit<CreateResult, 'sessionId'>) => api.post<{ id: number }>(`${coach}/sessions/by-code/${sessionId}/results`, body),
  coachAttendance: (bookingId: number, body: AttendanceInput) => api.post<{ id: number }>(`${coach}/attendance`, { bookingId, ...body }),
  coachCorrectAttendance: (id: number, body: AttendanceInput & { reason: string }) => api.put<{ id: number }>(`${coach}/attendance/${id}`, body),
}
