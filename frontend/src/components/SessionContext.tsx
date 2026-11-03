import { sessionIdText } from '../api/sessionIdentity'
import { formatCalendarDate, formatClockTime } from '../utils/date'
import './sessionContext.css'

export interface SessionContextProps {
  sessionId?: string | null
  className?: string | null
  sportName?: string | null
  date: string
  startTime?: string | null
  endTime?: string | null
  coachName?: string | null
  facilityName?: string | null
  trainingType?: string | null
}

export function SessionContext(session: SessionContextProps) {
  return <span className="session-context">
    <span className="session-identity">Session ID <code>{sessionIdText(session.sessionId)}</code></span>
    <strong>{session.className || session.sportName || 'Class session'}</strong>
    {session.sportName && <span>{session.sportName}</span>}
    <span>{formatCalendarDate(session.date)} {formatClockTime(session.startTime)} - {formatClockTime(session.endTime)}</span>
    <span>Facility: {session.facilityName || 'Facility not assigned'}</span>
    <span>{session.trainingType === 'SELF_TRAINING' ? 'Self-training - No assigned coach' : session.coachName ? `Coach: ${session.coachName}` : 'Coach not assigned'}</span>
  </span>
}
