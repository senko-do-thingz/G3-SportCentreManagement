import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { trainingGuidanceApi, type PackageCheck, type SessionAvailability } from '../../api/trainingGuidanceApi'
import { buttonClassName, Card, EmptyState, PageHeader } from '../../components/ui'
import { formatCalendarDate } from '../../utils/date'
import { RecommendationTabs } from './RecommendationTabs'
import { TrainingData } from './TrainingData'
import './training.css'

const AVAILABILITY: Record<SessionAvailability, string> = {
  AVAILABLE: 'Available', FULL: 'Full', BOOKED: 'Already booked', OVERLAP: 'Overlaps one of your bookings', AGE: 'Outside the age group',
}
const PACKAGE: Record<PackageCheck, string> = {
  ELIGIBLE: 'Eligible', NO_PACKAGE: 'No matching package', NO_SESSIONS_LEFT: 'No sessions left', OUTSIDE_VALIDITY: 'Package not valid on this date',
}

/** Flow 5 F5-04 Suitable Sessions: upcoming sessions of one recommended class. Booking checks happen again on confirmation. */
export function SuitableSessionsPage() {
  const classId = Number(useParams().classId)
  const load = useCallback((signal: AbortSignal) => trainingGuidanceApi.suitableSessions(classId, signal), [classId])
  if (!Number.isSafeInteger(classId) || classId <= 0) return <EmptyState title="Class not found" />
  return <div className="training-page">
    <RecommendationTabs />
    <PageHeader title="Suitable sessions" description="Choose a session; final booking checks happen before confirmation." />
    <TrainingData load={load} refreshLabel="Refresh sessions">{data => <Card title={`${data.className}${data.coachName ? ` / ${data.coachName}` : ''}`}>
      <p>{data.sportName}{data.level ? ` - ${data.level}` : ''}{data.ageGroup ? ` - age group ${data.ageGroup}` : ''}</p>
      {data.sessions.length === 0 ? <EmptyState title="No upcoming sessions" description="This class has no published sessions in the next 30 days." />
        : <table className="table"><caption>Upcoming sessions for {data.className}</caption>
          <thead><tr><th>Date / time</th><th>Facility</th><th>Spots</th><th>Package check</th><th>Action</th></tr></thead>
          <tbody>{data.sessions.map(session => <tr key={session.sessionId}>
            <td>{formatCalendarDate(session.date)} / {session.startTime.slice(0, 5)}-{session.endTime.slice(0, 5)}</td>
            <td>{session.facilityName ?? '-'}</td>
            <td>{session.freeSeats === 0 ? 'Full' : `${session.freeSeats} left`}</td>
            <td>{PACKAGE[session.packageCheck]}</td>
            <td>{session.availability === 'AVAILABLE'
              ? <Link className={buttonClassName('secondary', 'sm')} to={`/member/sessions/${session.sessionCode ?? session.sessionId}`}>Book session {formatCalendarDate(session.date)} {session.startTime.slice(0, 5)}</Link>
              : AVAILABILITY[session.availability]}</td>
          </tr>)}</tbody></table>}
      {data.packages.length > 0
        ? <ul className="training-list">{data.packages.map((item, index) => <li key={index}>{item.packageName} ({item.trainingFormat === 'COACH_LED' ? 'Coach-led' : 'Self-training'}): {item.remainingSessions} of {item.totalSessions} sessions available, valid until {formatCalendarDate(item.endDate)}</li>)}</ul>
        : <p>You have no active {data.sportName} package.</p>}
      <p>A compatible package is required to book. Saving a suggestion does not hold a seat, and viewing this list books nothing.</p>
      <Link className={buttonClassName('secondary')} to="/member/packages">Check other package options</Link>
    </Card>}</TrainingData>
  </div>
}
