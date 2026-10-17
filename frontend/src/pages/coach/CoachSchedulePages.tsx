import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { coachScheduleApi, type CoachRosterRecord, type CoachSessionRecord } from '../../api/coachScheduleApi'
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, PageLoader, Select, StatCard, StatusBadge } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import { browseAddDays, browseDate, browseDay, browseMonday, browseToday, validBrowseDate } from '../member/memberScheduleDates'

function positiveId(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

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

export function CoachTeachingSchedulePage() {
  const [params, setParams] = useSearchParams()
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
  const classCount = new Set(sessions.flatMap((session) => session.classId ? [session.classId] : [])).size
  const dates = Array.from({ length: 7 }, (_, index) => browseAddDays(week, index))

  return <div className="page coach-schedule-page">
    <PageHeader eyebrow="Coach" title="My Teaching Schedule" description="Review your assigned sessions and open a roster for class preparation." />
    {resource.status === 'loading' ? <PageLoader label="Loading your teaching schedule" /> : resource.status === 'error' ? <ErrorState title="We could not load your teaching schedule" error={resource.error} onRetry={reload} /> : <>
      <div className="coach-stats">
        <StatCard label="Sessions this week" value={sessions.length} />
        <StatCard label="Classes assigned" value={classCount} />
        <StatCard label="Members in next session" value={nextSession?.bookedCount ?? 0} hint={nextSession ? sessionTitle(nextSession) : 'No upcoming published session'} />
      </div>
      <Card title="Weekly schedule">
        <div className="browse-actions coach-week-nav"><Button variant="secondary" onClick={() => setParams((current) => changeWeekParams(current, browseAddDays(week, -7)))}>Previous week</Button><Button variant="secondary" onClick={() => setParams((current) => changeWeekParams(current, browseMonday(browseToday())))}>This week</Button><Button variant="secondary" onClick={() => setParams((current) => changeWeekParams(current, browseAddDays(week, 7)))}>Next week</Button><strong>{browseDate(week)} - {browseDate(weekEnd)}</strong></div>
        {sessions.length === 0 ? <EmptyState title="No assigned sessions this week" description="Your schedule will show sessions assigned to your coach account." /> : <div className="coach-calendar">
          {dates.map((date) => {
            const dailySessions = sessions.filter((session) => session.sessionDate === date).sort((a, b) => a.startTime.localeCompare(b.startTime) || a.id - b.id)
            return <section className="coach-day" aria-label={`${browseDay(date)} ${browseDate(date)}`} key={date}>
              <h2>{browseDay(date)} {browseDate(date)}</h2>
              {dailySessions.length === 0 ? <p className="browse-muted">No sessions</p> : <div className="coach-day__sessions">{dailySessions.map((session) => <article className="coach-session" key={session.id}>
                <div className="coach-session__heading"><div><p className="booking-history-card__date">{displayTime(session.startTime)} - {displayTime(session.endTime)}</p><h3>{sessionTitle(session)}</h3></div><StatusBadge status={session.status} /></div>
                <p>{session.sportName}{session.className ? ` - ${session.className}` : ''}</p>
                <p>Facility: {session.facilityName || 'Not provided'}</p>
                <p>Members booked: {session.bookedCount}{session.capacity ? ` / ${session.capacity}` : ''}</p>
                <Link className="btn btn--secondary" to={`/coach/roster?sessionId=${session.id}&week=${week}`}>View roster</Link>
              </article>)}</div>}
            </section>
          })}
        </div>}
      </Card>
    </>}
  </div>
}

function rosterBookedAt(value: string | null): string {
  if (!value || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) return 'Not provided'
  const [date, time] = value.split('T')
  return `${browseDate(date)} ${time.slice(0, 5)}`
}

function RosterTable({ rows }: { rows: CoachRosterRecord[] }) {
  return <div className="coach-roster-table-wrap">
    <table className="coach-roster-table">
      <thead><tr><th scope="col">Member code</th><th scope="col">Member name</th><th scope="col">Package</th><th scope="col">Booked at</th><th scope="col">Status</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={row.bookingId}><td>{row.memberCode || 'Not provided'}</td><td>{row.memberName || 'Not provided'}</td><td>{row.packageName || 'Not provided'}</td><td>{rosterBookedAt(row.bookedAt)}</td><td><Badge tone="success">Confirmed</Badge></td></tr>)}</tbody>
    </table>
  </div>
}

export function CoachRosterPage() {
  const [params, setParams] = useSearchParams()
  const week = sessionWeek(params)
  const weekEnd = browseAddDays(week, 6)
  const selectedSessionId = positiveId(params.get('sessionId'))
  const loadSessions = useCallback((signal: AbortSignal) => coachScheduleApi.list(week, weekEnd, signal), [week, weekEnd])
  const [sessionsResource, reloadSessions] = useResource(loadSessions)
  const sessions = sessionsResource.status === 'ready' ? sessionsResource.data : []
  const selectedSession = sessions.find((session) => session.id === selectedSessionId) ?? null
  const isOwnSession = selectedSession !== null
  const rosterLoader = useCallback((signal: AbortSignal) => selectedSessionId && isOwnSession
    ? coachScheduleApi.roster(selectedSessionId, signal) : Promise.resolve([]), [selectedSessionId, isOwnSession])
  const [roster, reloadRoster] = useResource(rosterLoader)
  const options = sessions.map((session) => ({ value: String(session.id), label: `${browseDate(session.sessionDate)} ${displayTime(session.startTime)} - ${sessionTitle(session)}` }))

  function selectSession(value: string) {
    setParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set('sessionId', value)
      else next.delete('sessionId')
      next.set('week', week)
      return next
    })
  }

  return <div className="page coach-schedule-page">
    <PageHeader eyebrow="Coach" title="Class Roster" description="Review confirmed members for a session assigned to you. Personal contact details are restricted." actions={<Button variant="secondary" disabled={!selectedSession || roster.status !== 'ready'} onClick={() => window.print()}>Print roster</Button>} />
    <Card title="Select a session">
      <div className="browse-actions coach-week-nav"><Button variant="secondary" onClick={() => setParams(changeWeekParams(params, browseAddDays(week, -7)))}>Previous week</Button><Button variant="secondary" onClick={() => setParams(changeWeekParams(params, browseMonday(browseToday())))}>This week</Button><Button variant="secondary" onClick={() => setParams(changeWeekParams(params, browseAddDays(week, 7)))}>Next week</Button><strong>{browseDate(week)} - {browseDate(weekEnd)}</strong></div>
      {sessionsResource.status === 'error' ? <ErrorState title="We could not load your sessions" error={sessionsResource.error} onRetry={reloadSessions} /> : sessionsResource.status === 'loading' ? <PageLoader label="Loading your sessions" /> : sessions.length === 0 ? <EmptyState title="No assigned sessions this week" description="Change the week to find one of your sessions." /> : <Select label="Session" placeholder="Choose a session" value={selectedSessionId ? String(selectedSessionId) : ''} options={options} onChange={(event) => selectSession(event.target.value)} />}
    </Card>
    {selectedSession && <Card className="coach-print" title={sessionTitle(selectedSession)} actions={<StatusBadge status={selectedSession.status} />}>
      <dl className="browse-facts coach-roster-facts"><div><dt>Date</dt><dd>{browseDate(selectedSession.sessionDate)}</dd></div><div><dt>Time</dt><dd>{displayTime(selectedSession.startTime)} - {displayTime(selectedSession.endTime)} Vietnam time</dd></div><div><dt>Sport</dt><dd>{selectedSession.sportName}</dd></div><div><dt>Facility</dt><dd>{selectedSession.facilityName || 'Not provided'}</dd></div><div><dt>Capacity</dt><dd>{selectedSession.capacity ?? 'Not provided'}</dd></div><div><dt>Confirmed bookings</dt><dd>{selectedSession.bookedCount}</dd></div></dl>
      <p className="notice notice--info coach-privacy-note">Personal contact details are restricted. This roster shows member names and package details only.</p>
      {roster.status === 'loading' ? <PageLoader label="Loading confirmed roster" /> : roster.status === 'error' ? <ErrorState title="We could not load this class roster" error={roster.error} onRetry={reloadRoster} /> : roster.data.length === 0 ? <EmptyState title="No confirmed bookings" description="There are no confirmed members on this session roster." /> : <RosterTable rows={roster.data} />}
    </Card>}
    {sessionsResource.status === 'ready' && selectedSessionId && !selectedSession && <Card><ErrorState title="Session is not in this week's schedule" message="Choose a session from your schedule, or change the week and select it again." /></Card>}
    {sessionsResource.status === 'ready' && !selectedSessionId && sessions.length > 0 && <EmptyState title="Choose a session to view its roster" description="Only your assigned sessions are available in this coach workspace." />}
    {sessionsResource.status === 'ready' && selectedSession && roster.status === 'ready' && roster.data.length > 0 && <div className="coach-print-hidden"><Link className="btn btn--secondary" to={`/coach?week=${week}`}>Back to teaching schedule</Link></div>}
  </div>
}
