import { api } from './client'
import type { SessionId } from './sessionIdentity'
import type { AttendanceStatus } from './staffTrainingApi'

export interface BackfillBooking {
  sessionCode?: SessionId; coachName?: string | null; facilityName?: string | null
  bookingId: number; sessionId: number; memberName: string; className: string
  date: string; startTime: string; endTime: string
}
export interface BackfillWorkspace { enabled: boolean; window: string | null; message: string; bookings: BackfillBooking[] }
const path = '/manager/training/attendance/backfill'
export const attendanceBackfillApi = {
  workspace: (signal: AbortSignal) => api.get<BackfillWorkspace>(path, { signal }),
  save: (body: { bookingId: number; status: AttendanceStatus; reason: string; notes: string | null }) => api.post<{ id: number }>(path, body),
}
