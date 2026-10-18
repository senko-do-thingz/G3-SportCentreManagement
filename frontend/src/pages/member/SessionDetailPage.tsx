import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { memberSessionsApi } from '../../api/memberSessionsApi'
import { Badge, Button, Card, PageHeader, PageLoader, buttonClassName } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { CoachProfileModal } from './CoachProfileModal'
import { MemberScheduleNav } from './MemberScheduleNav'
import { browseDate, sessionMinutes } from './memberScheduleDates'
import { browseId, safeBrowseReturn } from './scheduleFilters'
import { ELIGIBILITY_COPY, resolveSessionDetailState } from './sessionEligibility'
import { useMemberResource } from './useMemberResource'

export function SessionDetailPage() {
  const { sessionId } = useParams()
  const [params] = useSearchParams()
  const id = browseId(sessionId ?? null)
  const returnTo = safeBrowseReturn(params.get('returnTo'))
  if (!id) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Session Unavailable" /><p role="alert">Choose a valid session.</p><Link to={returnTo}>Back to results</Link></div>
  return <SessionDetailContent key={id} id={id} returnTo={returnTo} />
}

function SessionDetailContent({ id, returnTo }: { id: number; returnTo: string }) {
  const loader = useCallback((signal: AbortSignal) => memberSessionsApi.detail(id, signal), [id])
  const [resource, reload] = useMemberResource(loader)
  const [profileOpen, setProfileOpen] = useState(false)
  const lastRefresh = useRef(0)
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === 'visible' && !profileOpen && resource.status !== 'loading' && Date.now() - lastRefresh.current > 1000) { lastRefresh.current = Date.now(); reload() }
    }
    document.addEventListener('visibilitychange', refresh)
    return () => { document.removeEventListener('visibilitychange', refresh) }
  }, [reload, resource.status, profileOpen])
  if (resource.status === 'loading') return <div className="page browse-page"><MemberScheduleNav /><PageLoader label="Loading session detail" /></div>
  if (resource.status === 'error') return <div className="page browse-page"><MemberScheduleNav /><PageHeader title={resource.error instanceof ApiError && resource.error.status === 404 ? 'Session Unavailable' : 'Session Detail'} /><BrowseError error={resource.error} onRetry={reload} /><Link to={returnTo}>Back to results</Link></div>
  const detail = resource.data, session = detail.session
  const state = resolveSessionDetailState(detail), copy = ELIGIBILITY_COPY[state]
  const self = session.trainingFormat === 'SELF_TRAINING'
  const packageState = ['NO_PACKAGE', 'NO_SESSIONS_LEFT', 'PACKAGE_EXPIRED_BY_THEN', 'PACKAGE_NOT_STARTED_BY_THEN'].includes(state)
  const packageLink = `/member/packages?${new URLSearchParams({ sport: String(session.sportId), format: session.trainingFormat })}`
  const bookingLink = `/member/bookings/new?${new URLSearchParams({ sessionId: String(session.id), returnTo })}`
  return <div className="page browse-page">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / Classes & schedule" title={state === 'ELIGIBLE' && self ? 'Session Detail - Self-training' : copy.title} description="Choose a session before selecting your sport package. Times use Vietnam time (UTC+7)." />
    <div className="browse-detail">
      <Card title={session.className || `${session.sportName} ${self ? 'Self-training' : 'Session'}`}>
        {session.level && <Badge tone="success">{session.level[0] + session.level.slice(1).toLowerCase()}</Badge>}
        <dl className="browse-facts"><div><dt>Sport</dt><dd>{session.sportName}</dd></div><div><dt>Format</dt><dd>{self ? 'Self-training' : 'Coach-led'}</dd></div><div><dt>Facility</dt><dd>{session.facilityName || 'Not provided'}</dd></div><div><dt>Session length</dt><dd>{sessionMinutes(session.startTime, session.endTime)}</dd></div><div><dt>Capacity</dt><dd>{session.capacity} members</dd></div></dl>
        <h3>Upcoming session</h3><div className="browse-callout"><strong>{browseDate(session.sessionDate)} / {session.startTime.slice(0, 5)} - {session.endTime.slice(0, 5)}</strong><p>{session.bookedCount} / {session.capacity} booked - {session.freeSeats} spots remaining</p></div>
        <div className="browse-actions"><Link className={buttonClassName('secondary')} to={returnTo}>Back to results</Link><Button variant="ghost" onClick={reload}>Refresh availability</Button></div>
      </Card>
      <Card title={self ? 'Self-training access' : 'Your coach'}>
        {self ? <><dl className="browse-facts"><div><dt>Sport</dt><dd>{session.sportName}</dd></div><div><dt>Format</dt><dd>Self-training</dd></div><div><dt>Facility</dt><dd>{session.facilityName || 'Not provided'}</dd></div></dl><p className="browse-callout">Practice independently using a self-training sport package. Coach-led sessions need a separate matching package.</p></> : <><div className="browse-avatar" aria-hidden="true">{(session.coachName || '?').split(' ').filter(Boolean).slice(0, 2).map((name) => name[0]).join('')}</div><h3>{session.coachName || 'Coach not provided'}</h3>{session.coachId && <Button variant="secondary" onClick={() => setProfileOpen(true)}>View coach profile</Button>}</>}
        <div className="browse-eligibility" role="status"><Badge tone={state === 'ELIGIBLE' ? 'success' : state === 'FULL' ? 'warning' : 'info'}>{state === 'ELIGIBLE' ? 'Eligible' : state === 'FULL' ? 'Fully booked' : copy.title}</Badge><p>{copy.message}</p>
          {packageState && <Link className={buttonClassName()} to={packageLink}>View suitable sport packages</Link>}
          {state === 'ELIGIBLE' && <><p className="notice notice--info">Continue to choose an eligible package and review this booking. No booking has been created yet.</p><Link className={buttonClassName()} to={bookingLink}>Book session</Link></>}
          <Link className={buttonClassName('secondary')} to={returnTo}>Find another class</Link>
        </div>
      </Card>
    </div>
    {!self && profileOpen && session.coachId && <CoachProfileModal coachId={session.coachId} onClose={() => setProfileOpen(false)} />}
  </div>
}
