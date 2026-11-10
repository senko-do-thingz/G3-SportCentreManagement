import { formatDateTime } from '../../utils/format'
import '../../styles/coachUi.css'
import { parseSessionReference, sessionRouteReference } from '../../api/sessionIdentity'
import { SessionContext } from '../../components/SessionContext'
import { sessionIdText } from '../../api/sessionIdentity'
import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { coachScheduleApi, type CoachRosterRecord, type CoachSessionRecord } from '../../api/coachScheduleApi'
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, PageLoader, Select, StatusBadge } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import { browseAddDays, browseDate, browseDay, browseMonday, browseToday, validBrowseDate } from '../member/memberScheduleDates'

function displayTime(value: string): string {
  return value.slice(0, 5)
}

function vietnamTime(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(now)
  const value = (type: string) => parts.find((part) => part.type === type)!.value
  return `${value('hour')}:${value('minute')}`
}

function sessionTitle(session: CoachSessionRecord): string {
  return session.className || `${session.sportName} session`
}

function sessionWeek(params: URLSearchParams): string {
  const value = params.get('week') ?? browseToday()
  return browseMonday(validBrowseDate(value) ? value : browseToday())
}

function changeWeekParams(params: URLSearchParams, week: string): URLSearchParams {
  const next = new URLSearchParams(params)
  next.set('week', week)
  next.delete('sessionId')
  return next
}

function weekMetricLabel(start: string, end: string): string {
  const dateParts = (value: string) => {
    const [year, month, day] = value.split('-').map(Number)
    const date = new Date(Date.UTC(year, month - 1, day, 12))
    return {
      day: String(day).padStart(2, '0'),
      month: new Intl.DateTimeFormat('en-US', { month: 'short', timeZone: 'UTC' }).format(date).toUpperCase(),
    }
  }
  const first = dateParts(start)
  const last = dateParts(end)
  return first.month === last.month
    ? `${first.day} - ${last.day} ${last.month}`
    : `${first.day} ${first.month} - ${last.day} ${last.month}`
}

function rosterHref(session: CoachSessionRecord, week: string): string {
  return `/coach/roster?sessionId=${encodeURIComponent(sessionRouteReference(session))}&week=${encodeURIComponent(week)}`
}

export function CoachTeachingSchedulePage() {
  const [params, setParams] = useSearchParams()
  const [selection, setSelection] = useState<{ week: string; sessionId: string } | null>(null)
  const week = sessionWeek(params)
  const weekEnd = browseAddDays(week, 6)
  const loader = useCallback((signal: AbortSignal) => coachScheduleApi.list(week, weekEnd, signal), [week, weekEnd])
  const [resource, reload] = useResource(loader)
  const sessions = resource.status === 'ready' ? resource.data : []
  const today = browseToday()
  const currentTime = vietnamTime()
  const nextSession = sessions
    .filter((session) => session.status === 'PUBLISHED'
      && (session.sessionDate > today || (session.sessionDate === today && displayTime(session.startTime) >= currentTime)))
    .sort((a, b) => a.sessionDate.localeCompare(b.sessionDate) || a.startTime.localeCompare(b.startTime) || a.id - b.id)[0] ?? null
  const defaultSession = nextSession ?? [...sessions].sort((a, b) => a.sessionDate.localeCompare(b.sessionDate) || a.startTime.localeCompare(b.startTime) || a.id - b.id)[0] ?? null
  const selectedSession = (selection?.week === week
    ? sessions.find((session) => sessionRouteReference(session) === selection.sessionId)
    : undefined) ?? defaultSession
  const classCount = new Set(sessions.flatMap((session) => session.classId ? [session.classId] : [])).size
  const dates = Array.from({ length: 7 }, (_, index) => browseAddDays(week, index))

  return <div className="page coach-schedule-page coach-ui">
    <PageHeader eyebrow="Your workspace" title="Teaching Schedule" description="Review assigned sessions across the teaching week." />
    <section className="coach-schedule-hero" aria-label="Coach schedule introduction">
      <div className="coach-schedule-hero__copy">
        <p className="coach-schedule-hero__eyebrow">Sportify coach experience</p>
        <h2>Your week.<br /><span>Clearly organized.</span></h2>
        <p>Simple workflows. Better coaching.</p>
      </div>
      {selectedSession && <Link className="btn btn--accent coach-schedule-hero__action" to={rosterHref(selectedSession, week)}>View class roster</Link>}
    </section>
    {resource.status === 'loading' ? <PageLoader label="Loading your teaching schedule" /> : resource.status === 'error' ? <ErrorState title="We could not load your teaching schedule" error={resource.error} onRetry={reload} /> : <>
      <section className="coach-schedule-metrics" aria-label="Schedule summary">
        <div className="coach-schedule-metric"><span className="coach-schedule-metric__mark" aria-hidden="true" /><div><p>{sessions.length}</p><span>Sessions this week</span></div></div>
        <div className="coach-schedule-metric"><span className="coach-schedule-metric__mark" aria-hidden="true" /><div><p>{classCount}</p><span>Classes assigned</span></div></div>
        <div className="coach-schedule-metric"><span className="coach-schedule-metric__mark" aria-hidden="true" /><div><p>{nextSession?.bookedCount ?? 0}</p><span>Members in next session</span></div></div>
        <div className="coach-schedule-metric"><span className="coach-schedule-metric__mark" aria-hidden="true" /><div><p>{weekMetricLabel(week, weekEnd)}</p><span>Week in view</span></div></div>
      </section>
      <div className="coach-schedule-layout">
        <Card className="coach-schedule-weekly" title="Weekly schedule" actions={<Button variant="secondary" size="sm" onClick={() => setParams((current) => changeWeekParams(current, browseMonday(browseToday())))}>This week</Button>}>
          <div className="coach-schedule-week-nav" aria-label="Teaching week navigation">
            <Button variant="secondary" size="sm" onClick={() => setParams((current) => changeWeekParams(current, browseAddDays(week, -7)))}>Previous week</Button>
            <strong>{browseDate(week)} - {browseDate(weekEnd)}</strong>
            <Button variant="secondary" size="sm" onClick={() => setParams((current) => changeWeekParams(current, browseAddDays(week, 7)))}>Next week</Button>
          </div>
          {sessions.length === 0 ? <div className="coach-schedule-empty"><EmptyState title="No assigned sessions this week" description="Your schedule will show sessions assigned to your coach account." /></div> : <>
            <div className="coach-schedule-week-scroll" role="region" aria-label={`Sessions from ${browseDate(week)} to ${browseDate(weekEnd)}`} tabIndex={0}>
              <div className="coach-schedule-week-grid">
                {dates.map((date) => {
                  const dailySessions = sessions.filter((session) => session.sessionDate === date).sort((a, b) => a.startTime.localeCompare(b.startTime) || a.id - b.id)
                  return <section className="coach-schedule-day" aria-labelledby={`coach-schedule-day-${date}`} key={date}>
                    <h3 id={`coach-schedule-day-${date}`}><span>{browseDay(date)}</span><time dateTime={date}>{browseDate(date)}</time></h3>
                    {dailySessions.length === 0 ? <p className="coach-schedule-day__empty">No sessions</p> : <div className="coach-schedule-day__sessions">{dailySessions.map((session) => {
                      const sessionKey = sessionRouteReference(session)
                      const isSelected = selectedSession !== null && sessionRouteReference(selectedSession) === sessionKey
                      return <button className={`coach-schedule-session${isSelected ? ' is-selected' : ''}`} type="button" key={session.id} aria-pressed={isSelected} onClick={() => setSelection({ week, sessionId: sessionKey })}>
                        <span className="coach-schedule-session__time">{displayTime(session.startTime)} - {displayTime(session.endTime)}</span>
                        <span className="coach-schedule-session__title">{sessionTitle(session)}</span>
                        <span className="coach-schedule-session__id">{sessionIdText(session.sessionId)}</span>
                      </button>
                    })}</div>}
                  </section>
                })}
              </div>
            </div>
            <p className="coach-schedule-hint">Select a session to see its status, coach, facility and booking details.</p>
          </>}
        </Card>
        <Card className="coach-schedule-overview" title="Session overview">
          {selectedSession ? <>
            <div className="coach-schedule-overview__feature">
              <p>{selectedSession === nextSession ? 'Next visible session' : 'Selected session'}</p>
              <h3>{sessionTitle(selectedSession)}</h3>
              <span>Session {sessionIdText(selectedSession.sessionId)}</span>
              <span>{browseDate(selectedSession.sessionDate)} | {displayTime(selectedSession.startTime)} - {displayTime(selectedSession.endTime)}</span>
              <span>{selectedSession.facilityName || 'Facility not provided'}</span>
            </div>
            <dl className="coach-schedule-overview__facts">
              <div><dt>Session status</dt><dd><StatusBadge status={selectedSession.status} /></dd></div>
              <div><dt>Members booked</dt><dd>{selectedSession.bookedCount}{selectedSession.capacity ? ` / ${selectedSession.capacity}` : ''}</dd></div>
              <div><dt>Coach</dt><dd>{selectedSession.coachName || 'Not assigned'}</dd></div>
              <div><dt>Facility</dt><dd>{selectedSession.facilityName || 'Not provided'}</dd></div>
              {selectedSession.trainingType && <div><dt>Training type</dt><dd>{selectedSession.trainingType === 'SELF_TRAINING' ? 'Self-training' : 'Coach-led'}</dd></div>}
            </dl>
            <Link className="btn btn--primary coach-schedule-overview__action" to={rosterHref(selectedSession, week)}>View roster</Link>
          </> : <div className="coach-schedule-overview__empty"><p>No session to review</p><span>Choose another week to view assigned sessions and rosters.</span></div>}
        </Card>
      </div>
    </>}
  </div>
}

function rosterBookedAt(value: string | null): string {
  return formatDateTime(value)
}

function RosterTable({ rows }: { rows: CoachRosterRecord[] }) {
  return <div className="coach-roster-table-wrap">
    <table className="coach-roster-table" aria-label="Confirmed member bookings">
      <thead><tr><th scope="col">Member code</th><th scope="col">Member name</th><th scope="col">Package</th><th scope="col">Booked at</th><th scope="col">Status</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={row.bookingId}><td>{row.memberCode || 'Not provided'}</td><td>{row.memberName || 'Not provided'}</td><td>{row.packageName || 'Not provided'}</td><td>{rosterBookedAt(row.bookedAt)}</td><td><Badge tone="success">Confirmed</Badge></td></tr>)}</tbody>
    </table>
  </div>
}

export function CoachRosterPage() {
  const [params, setParams] = useSearchParams()
  const week = sessionWeek(params)
  const weekEnd = browseAddDays(week, 6)
  const selectedSessionId = parseSessionReference(params.get('sessionId'))
  const loadSessions = useCallback((signal: AbortSignal) => coachScheduleApi.list(week, weekEnd, signal), [week, weekEnd])
  const [sessionsResource, reloadSessions] = useResource(loadSessions)
  const sessions = sessionsResource.status === 'ready' ? sessionsResource.data : []
  const selectedSession = sessions.find((session) => (session.id === selectedSessionId || session.sessionId === selectedSessionId)) ?? null
  const isOwnSession = selectedSession !== null
  const rosterLoader = useCallback((signal: AbortSignal) => selectedSessionId && isOwnSession
    ? typeof selectedSessionId === 'number' ? coachScheduleApi.roster(selectedSessionId, signal) : coachScheduleApi.rosterByCode(selectedSessionId, signal) : Promise.resolve([]), [selectedSessionId, isOwnSession])
  const [roster, reloadRoster] = useResource(rosterLoader)
  const options = sessions.map((session) => ({ value: sessionRouteReference(session), label: `${sessionIdText(session.sessionId)} - ${browseDate(session.sessionDate)} ${displayTime(session.startTime)} - ${sessionTitle(session)}` }))

  function selectSession(value: string) {
    setParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set('sessionId', value)
      else next.delete('sessionId')
      next.set('week', week)
      return next
    })
  }

  const rosterCount = selectedSession && roster.status === 'ready' ? roster.data.length : '--'

  return <div className="page coach-schedule-page coach-roster-page coach-ui">
    <PageHeader eyebrow="Your workspace" title="Class Roster" description="Select an assigned session to review its confirmed bookings." actions={<Button variant="secondary" disabled={!selectedSession || roster.status !== 'ready'} onClick={() => window.print()}>Print roster</Button>} />
    <section className="coach-schedule-hero" aria-label="Class roster introduction">
      <div className="coach-schedule-hero__copy">
        <p className="coach-schedule-hero__eyebrow">Sportify coach experience</p>
        <h2>Know your class.<br /><span>Prepare to coach.</span></h2>
        <p>Review confirmed bookings and session details in one place.</p>
      </div>
      <a className="btn btn--accent coach-schedule-hero__action" href="#coach-roster-picker">Choose session</a>
    </section>
    <section className="coach-roster-metrics" aria-label="Roster summary">
      <div className="coach-schedule-metric"><span className="coach-schedule-metric__mark" aria-hidden="true" /><div><p>{selectedSession ? '1' : '--'}</p><span>Selected session</span></div></div>
      <div className="coach-schedule-metric"><span className="coach-schedule-metric__mark" aria-hidden="true" /><div><p>{selectedSession?.bookedCount ?? '--'}</p><span>Confirmed bookings</span></div></div>
      <div className="coach-schedule-metric"><span className="coach-schedule-metric__mark" aria-hidden="true" /><div><p>{rosterCount}</p><span>Members displayed</span></div></div>
      <div className="coach-schedule-metric"><span className="coach-schedule-metric__mark" aria-hidden="true" /><div><p>{selectedSession?.capacity ?? '--'}</p><span>Roster capacity</span></div></div>
    </section>
    <div className="coach-roster-layout">
      <Card className="coach-roster-picker" title="Choose a session" id="coach-roster-picker">
        <div className="coach-roster-picker__body">
          <p className="coach-roster-field-label">Schedule week</p>
          <div className="coach-roster-week-nav" aria-label="Roster week navigation">
            <strong>{browseDate(week)} - {browseDate(weekEnd)}</strong>
            <Button variant="secondary" size="sm" onClick={() => setParams(changeWeekParams(params, browseAddDays(week, -7)))}>Previous</Button>
            <Button variant="secondary" size="sm" onClick={() => setParams(changeWeekParams(params, browseMonday(browseToday())))}>This week</Button>
            <Button variant="secondary" size="sm" onClick={() => setParams(changeWeekParams(params, browseAddDays(week, 7)))}>Next</Button>
          </div>
          {sessionsResource.status === 'error' ? <ErrorState title="We could not load your sessions" error={sessionsResource.error} onRetry={reloadSessions} /> : sessionsResource.status === 'loading' ? <PageLoader label="Loading your sessions" /> : sessions.length === 0 ? <EmptyState title="No assigned sessions this week" description="Change the week to find one of your sessions." /> : <Select label="Session" placeholder="Choose an assigned session" value={selectedSession ? sessionRouteReference(selectedSession) : ''} options={options} onChange={(event) => selectSession(event.target.value)} />}
          <p className="coach-roster-picker__note">Only sessions assigned to your coach account are available. The roster contains confirmed bookings only.</p>
        </div>
      </Card>
      <Card className={`coach-print coach-roster-content${selectedSession ? ' has-session' : ''}`} title={selectedSession ? sessionTitle(selectedSession) : 'Member roster'} actions={selectedSession ? <StatusBadge status={selectedSession.status} /> : undefined}>
        {selectedSession ? <>
          <SessionContext {...selectedSession} date={selectedSession.sessionDate} />
          <dl className="browse-facts coach-roster-facts"><div><dt>Capacity</dt><dd>{selectedSession.capacity ?? 'Not provided'}</dd></div><div><dt>Confirmed bookings</dt><dd>{selectedSession.bookedCount}</dd></div></dl>
          <p className="notice notice--info coach-privacy-note">Personal contact details are restricted. This roster shows member names and package details only.</p>
          {roster.status === 'loading' ? <PageLoader label="Loading confirmed roster" /> : roster.status === 'error' ? <ErrorState title="We could not load this class roster" error={roster.error} onRetry={reloadRoster} /> : roster.data.length === 0 ? <EmptyState title="No confirmed bookings" description="There are no confirmed members on this session roster." /> : <RosterTable rows={roster.data} />}
        </> : sessionsResource.status === 'error' ? <EmptyState title="Roster unavailable" description="Retry loading your sessions to choose a class roster." /> : sessionsResource.status === 'loading' ? <PageLoader label="Loading roster options" /> : selectedSessionId ? <ErrorState title="Session is not in this week's schedule" message="Choose a session from your schedule, or change the week and select it again." /> : sessions.length === 0 ? <EmptyState title="No session to review" description="Assigned sessions with confirmed bookings will appear here." /> : <EmptyState title="Choose a session to view its roster" description="Select one of your assigned sessions. Confirmed bookings will appear here." />}
      </Card>
    </div>
    {sessionsResource.status === 'ready' && selectedSession && roster.status === 'ready' && roster.data.length > 0 && <div className="coach-print-hidden coach-roster-links"><Link className="btn btn--secondary" to={`/coach?week=${week}`}>Back to teaching schedule</Link>{selectedSession.classId && <Link className="btn btn--secondary" to={`/coach/training/progress?classId=${selectedSession.classId}`}>View class member progress</Link>}</div>}
  </div>
}
