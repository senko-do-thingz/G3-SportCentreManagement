import { api } from './client'
import { ApiError } from './errors'

export interface SessionResource { id: number; name: string }
export interface SessionClass extends SessionResource { code: string }
export type TrainingType = 'SELF_TRAINING' | 'COACH_LED'
export type SessionStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED'
export interface ManagerSession {
  id: number
  date: string
  startTime: string
  endTime: string
  class: SessionClass | null
  coach: SessionResource | null
  facility: SessionResource | null
  capacity: number
  bookedCount: number
  trainingType: TrainingType
  status: SessionStatus
  cancelReason: string | null
  cancelledAt: string | null
  cancelledByUserId: number | null
}
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'
export interface SessionUpdate {
  date: string
  startTime: string
  endTime: string
  facilityId: number
  coachId?: number | null
  capacity: number
}
export type SessionCreate = Omit<SessionUpdate, 'date'> & {
  trainingType: TrainingType
} & ({ date: string; daysOfWeek?: never; fromDate?: never; toDate?: never } | {
  date?: never; daysOfWeek: DayOfWeek[]; fromDate: string; toDate: string
})
export interface SessionFilters {
  from: string
  to: string
  classId?: number
  coachId?: number
  facilityId?: number
}
export interface ScheduleConflict {
  sessionId: number
  requestedDate: string
  date: string
  startTime: string
  endTime: string
  class: SessionClass | null
  coach: SessionResource | null
  facility: SessionResource | null
  conflictType: 'COACH' | 'FACILITY' | 'BOTH'
}

const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const resource = (value: unknown): SessionResource | null => record(value) && typeof value.id === 'number' && typeof value.name === 'string'
  ? { id: value.id, name: value.name } : null

/** A generic 409 is not necessarily a scheduling conflict. Keep the original API error otherwise. */
export function scheduleConflicts(error: unknown): ScheduleConflict[] {
  if (!(error instanceof ApiError) || error.status !== 409 || !record(error.details) || !Array.isArray(error.details.conflicts)) return []
  return error.details.conflicts.flatMap((item: unknown): ScheduleConflict[] => {
    if (!record(item) || typeof item.sessionId !== 'number' || !Number.isSafeInteger(item.sessionId) || item.sessionId <= 0
      || typeof item.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(item.date)
      || typeof item.startTime !== 'string' || !/^\d{2}:\d{2}(:\d{2})?$/.test(item.startTime)
      || typeof item.endTime !== 'string' || !/^\d{2}:\d{2}(:\d{2})?$/.test(item.endTime)
      || !['COACH', 'FACILITY', 'BOTH'].includes(String(item.conflictType))) return []
    const sessionClass = resource(item.class)
    return [{
      sessionId: item.sessionId, requestedDate: typeof item.requestedDate === 'string' ? item.requestedDate : item.date,
      date: item.date, startTime: item.startTime, endTime: item.endTime,
      class: sessionClass ? { ...sessionClass, code: record(item.class) && typeof item.class.code === 'string' ? item.class.code : '' } : null,
      coach: resource(item.coach), facility: resource(item.facility),
      conflictType: item.conflictType as ScheduleConflict['conflictType'],
    }]
  })
}

export const managerSessionsApi = {
  list: (filters: SessionFilters, signal?: AbortSignal) => api.get<ManagerSession[]>('/manager/sessions', { query: { ...filters }, signal }),
  create: (classId: number, body: SessionCreate) => api.post<ManagerSession[]>(`/manager/classes/${classId}/sessions`, body),
  update: (id: number, body: SessionUpdate) => api.put<ManagerSession>(`/manager/sessions/${id}`, body),
  cancel: (id: number, reason: string) => api.put<ManagerSession>(`/manager/sessions/${id}/cancel`, { reason: reason.trim() }),
}
