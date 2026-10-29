import type { AttendanceState, ResultMetric, TrainingSessionLabel } from '../../api/memberTrainingApi'
import type { BadgeTone } from '../../components/ui'
import { bookingDate, bookingTime } from './bookingPresentation'

/** Same date and time wording as My Bookings, so a session reads the same on every member screen. */
export function sessionWhen(session: Pick<TrainingSessionLabel, 'sessionDate' | 'startTime' | 'endTime'>): string {
  const start = bookingTime(session.startTime)
  const end = session.endTime ? bookingTime(session.endTime) : null
  return `${bookingDate(session.sessionDate)}, ${end ? `${start} - ${end}` : start}`
}

export function sessionTitle(session: Pick<TrainingSessionLabel, 'className' | 'sportName'>): string {
  return session.className ?? session.sportName ?? 'Training session'
}

export const ATTENDANCE_LABEL: Record<AttendanceState, string> = {
  ATTENDED: 'Attended',
  ABSENT: 'Absent',
  UNRECORDED: 'Not recorded yet',
}

export const ATTENDANCE_TONE: Record<AttendanceState, BadgeTone> = {
  ATTENDED: 'success',
  ABSENT: 'danger',
  UNRECORDED: 'neutral',
}

/** A recorded 0 stays "0"; a missing value is worded as not recorded, never as zero. */
export function metricValue(metric: Pick<ResultMetric, 'value' | 'unit'>): string {
  if (metric.value === null) return 'Not recorded'
  return metric.unit ? `${metric.value} ${metric.unit}` : String(metric.value)
}
