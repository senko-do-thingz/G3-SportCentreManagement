import { api } from './client'
export interface AttendanceSession {
  id: number; sessionCode: string; date: string; startTime: string; className: string
  classId: number | null; coachName: string | null; coachId: number | null; status: string
  confirmed: number; present: number; late: number; absent: number; excused: number; unmarked: number; attendanceRate: number | null
}
export interface AttendanceOverview { from: string; to: string; sessions: AttendanceSession[]; truncated: boolean }
export interface AttendanceDetail {
  session: AttendanceSession
  members: { bookingId: number; memberCode: string | null; memberName: string; bookingStatus: string; attendanceStatus: string | null; markedBy: string | null; markedAt: string | null; notes: string | null }[]
}
export const managerAttendanceApi = {
  overview: (from: string, to: string, classId: string, coachId: string, signal: AbortSignal) => api.get<AttendanceOverview>('/manager/training/attendance/overview', { signal, query: { from, to, classId: classId || undefined, coachId: coachId || undefined } }),
  detail: (code: string, signal: AbortSignal) => api.get<AttendanceDetail>(`/manager/training/attendance/sessions/${encodeURIComponent(code)}`, { signal }),
}
