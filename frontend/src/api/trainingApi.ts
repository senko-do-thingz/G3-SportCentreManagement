import { api } from './client'

export type TrainingPlanStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED'

export interface TrainingExercise {
  id?: number
  name: string
  sets: number | null
  reps: number | null
  durationMinutes: number | null
  instructions: string | null
}

export interface TrainingPlan {
  id: number
  targetType?: 'MEMBER' | 'CLASS'
  memberId: number | null
  memberName: string | null
  classId?: number | null
  className?: string | null
  title: string
  goal: string
  status: TrainingPlanStatus
  startDate: string
  endDate: string | null
  revision: number
  exercises: TrainingExercise[]
  publishedAt: string | null
}

export interface TrainingResult {
  id: number
  sessionId: number
  sessionDate: string
  sportName: string
  memberId: number
  memberName: string
  planTitle: string | null
  attendance: AttendanceStatus
  summary: string | null
  coachFeedback: string | null
  metrics: { label: string; value: string }[]
  revision?: number
}

export interface CoachTrainingMember {
  id: number
  memberCode: string
  name: string
  activePlanCount: number
}

export interface CoachTrainingClass {
  id: number
  name: string
  sportName: string
}

export interface AttendanceRow {
  attendanceId?: number | null
  bookingId: number
  memberId: number
  memberCode: string
  memberName: string
  status: AttendanceStatus | null
  checkedAt: string | null
  revision?: number
}

export interface SessionAttendance {
  sessionId: number
  sessionDate: string
  startTime: string
  className: string
  facilityName: string | null
  rows: AttendanceRow[]
}

export interface SavePlanInput {
  targetType: 'MEMBER' | 'CLASS'
  memberId: number | null
  classId: number | null
  title: string
  goal: string
  startDate: string
  endDate: string | null
  exercises: TrainingExercise[]
  expectedRevision?: number
}

export interface SaveResultInput {
  sessionId: number
  memberId: number
  attendance: AttendanceStatus
  summary: string
  coachFeedback: string
  metrics: { label: string; value: string }[]
  expectedRevision?: number
  correctionReason?: string
}

function list<T>(value: unknown): T[] {
  if (!Array.isArray(value)) throw new Error('The server returned an unexpected training response.')
  return value as T[]
}

export const trainingApi = {
  coachMembers: async (signal?: AbortSignal) => list<CoachTrainingMember>(await api.get<unknown>('/coach/training/members', { signal })),
  coachClasses: async (signal?: AbortSignal) => list<CoachTrainingClass>(await api.get<unknown>('/coach/training/classes', { signal })),
  coachPlans: async (memberId?: number, signal?: AbortSignal) => list<TrainingPlan>(await api.get<unknown>('/coach/training/plans', { query: { memberId }, signal })),
  savePlan: (input: SavePlanInput, planId?: number) => planId
    ? api.put<TrainingPlan>(`/coach/training/plans/${planId}`, input)
    : api.post<TrainingPlan>('/coach/training/plans', input),
  publishPlan: (planId: number, revision: number) => api.post<TrainingPlan>(`/coach/training/plans/${planId}/publish`, { expectedRevision: revision }),
  coachResults: async (memberId?: number, signal?: AbortSignal) => list<TrainingResult>(await api.get<unknown>('/coach/training/results', { query: { memberId }, signal })),
  saveResult: (input: SaveResultInput) => api.post<TrainingResult>('/coach/training/results', input),
  correctResult: (resultId: number, input: SaveResultInput) => api.put<TrainingResult>(`/coach/training/results/${resultId}`, input),
  sessionAttendance: (sessionId: number, signal?: AbortSignal) => api.get<SessionAttendance>(`/coach/training/sessions/${sessionId}/attendance`, { signal }),
  saveAttendance: (sessionId: number, rows: { bookingId: number; status: AttendanceStatus }[]) =>
    api.put<SessionAttendance>(`/coach/training/sessions/${sessionId}/attendance`, { rows }),
  correctAttendance: (attendanceId: number, status: AttendanceStatus, reason: string, expectedRevision?: number) =>
    api.put<AttendanceRow>(`/coach/training/attendance/${attendanceId}`, { status, reason, expectedRevision }),
  memberPlans: async (signal?: AbortSignal) => list<TrainingPlan>(await api.get<unknown>('/members/me/training/plans', { signal })),
  memberResults: async (signal?: AbortSignal) => list<TrainingResult>(await api.get<unknown>('/members/me/training/results', { signal })),
  memberAttendance: async (signal?: AbortSignal) => list<TrainingResult>(await api.get<unknown>('/members/me/training/attendance', { signal })),
  receptionAttendance: (bookingId: number, status: AttendanceStatus) =>
    api.post<AttendanceRow>('/bookings/self-training/attendance', { bookingId, status }),
}
