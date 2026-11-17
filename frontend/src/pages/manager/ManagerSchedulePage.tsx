import { sessionIdText } from '../../api/sessionIdentity'
import { SessionIdGuide } from '../../components/SessionIdGuide'
import '../../components/sessionContext.css'
import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { managerClassesApi } from '../../api/managerClassesApi'
import { managerSessionsApi, type ManagerSession } from '../../api/managerSessionsApi'
import { facilitiesApi } from '../../api/facilitiesApi'
import { Button, Card, EmptyState, Input, PageLoader, Select, StatusBadge, useToast, StatIcon } from '../../components/ui'
import { ConfirmationModal, DeleteReasonModal, ManagerError, PreviewNotice } from './ManagerShared'
import { useResource } from './useManagerTools'
import { loadScheduleClasses } from './loadScheduleClasses'
import { addDays, canManageSession, centerNow, compareScheduleSessions, displayDate, monday, WEEKDAYS } from './scheduleModel'
import { SessionEditorModal } from './SessionEditorModal'
import { CancelSessionModal } from './CancelSessionModal'

const loadCoaches = (signal: AbortSignal) => managerClassesApi.coaches(undefined, signal)
const loadFacilities = (signal: AbortSignal) => facilitiesApi.list(signal)
const id = (value: string | null) => value && /^\d+$/.test(value) && Number(value) > 0 ? Number(value) : undefined

export function ManagerSchedulePage() {
  const [params, setParams] = useSearchParams()
  const week = monday(params.get('week') ?? '')
  const classId = id(params.get('classId'))
  const coachId = id(params.get('coachId'))
  const facilityId = id(params.get('facilityId'))
  const [classes, retryClasses] = useResource(loadScheduleClasses)
  const [coaches, retryCoaches] = useResource(loadCoaches)
  const [facilities, retryFacilities] = useResource(loadFacilities)
  const loader = useCallback((signal: AbortSignal) => managerSessionsApi.list({ from: week, to: addDays(week, 6), classId, coachId, facilityId }, signal), [week, classId, coachId, facilityId])
  const [sessions, reload] = useResource(loader)
  const [editing, setEditing] = useState<ManagerSession | 'new' | null>(params.get('create') === '1' ? 'new' : null)
  const [cancelling, setCancelling] = useState<ManagerSession | null>(null)
  const [publishing, setPublishing] = useState<ManagerSession | null>(null)
  const [deleting, setDeleting] = useState<ManagerSession | null>(null)
  const toast = useToast()
  function query(key: string, value: string) { setParams((previous) => { const next = new URLSearchParams(previous); if (value) next.set(key, value); else next.delete(key); next.delete('create'); return next }) }
  function closeEditor() { setEditing(null); if (params.has('create')) query('create', '') }
  function saved() { closeEditor(); setCancelling(null); reload(); toast.success('Schedule updated') }
  function sessionSaved(session?: ManagerSession) {
    if (!session) { saved(); return }
    closeEditor()
    if (monday(session.date) !== week) query('week', monday(session.date))
    else reload()
    toast.success(`Session ${sessionIdText(session.sessionId)} saved for ${displayDate(session.date)}`)
  }
  const dates = Array.from({ length: 7 }, (_, index) => addDays(week, index))
  const today = centerNow().date
  const timeSlots = sessions.status === 'ready' ? [...new Set(sessions.data.map((session) => session.startTime.slice(0, 5)))].sort() : []
  const sessionsByDateAndTime = new Map<string, ManagerSession[]>()
  if (sessions.status === 'ready') {
    for (const session of sessions.data) {
      const key = `${session.date}|${session.startTime.slice(0, 5)}`
      const group = sessionsByDateAndTime.get(key)
      if (group) group.push(session)
      else sessionsByDateAndTime.set(key, [session])
    }
    for (const group of sessionsByDateAndTime.values()) group.sort(compareScheduleSessions)
  }
  const manualFacility = facilities.status === 'error' && facilities.error instanceof ApiError && facilities.error.status === 404
  const facilitiesUsable = manualFacility || (facilities.status === 'ready' && facilities.data.some((item) => item.active !== false))
  const available = classes.status === 'ready' && facilitiesUsable && classes.data.some((item) => item.active)

  const weekSessionsCount = sessions.status === 'ready' ? String(sessions.data.length).padStart(2, '0') : '--'
  const publishedCount = sessions.status === 'ready' ? String(sessions.data.filter((s) => s.status === 'PUBLISHED').length).padStart(2, '0') : '--'
  const coachesCount = coaches.status === 'ready' ? String(coaches.data.length).padStart(2, '0') : '--'
  const isThisWeek = week === monday(centerNow().date)

  return <div className="page manager-page manager-concept-page manager-schedule-page">
    <div className="concept-header">
      <div className="concept-header__main">
        <div className="concept-eyebrow">MANAGER WORKSPACE</div>
        <h1 className="concept-title">Schedule &amp; Coach Assignment</h1>
        <p className="concept-sub">Plan single sessions or a weekly schedule. Times use Vietnam time (UTC+7).</p>
      </div>
    </div>

    <div className="concept-hero">
      <div>
        <small>FACILITY TIMETABLE</small>
        <h2>TIMELY COORDINATION. <span className="concept-goldtext">SEAMLESS OPERATIONS.</span></h2>
        <p>Manage venue availability, coach shifts, and member bookings.</p>
      </div>
      <Button variant="secondary" className="concept-btn concept-btn--gold" disabled={!available} onClick={() => setEditing('new')}>+ Create session</Button>
    </div>

    <div className="concept-stats concept-stats--schedule">
      <div className="concept-stat concept-stat--schedule">
        <StatIcon name="calendar" />
        <div>
          <strong>{weekSessionsCount}</strong>
          <small>Week sessions</small>
        </div>
      </div>
      <div className="concept-stat concept-stat--schedule">
        <StatIcon name="send" />
        <div>
          <strong>{publishedCount}</strong>
          <small>Published in week</small>
        </div>
      </div>
      <div className="concept-stat concept-stat--schedule">
        <StatIcon name="coach" />
        <div>
          <strong>{coachesCount}</strong>
          <small>Active coaches</small>
        </div>
      </div>
      <div className="concept-stat concept-stat--schedule">
        <StatIcon name="calendar" />
        <div>
          <strong>{displayDate(week)} - {displayDate(addDays(week, 6))}</strong>
          <small>Week in view</small>
        </div>
      </div>
    </div>

    <PreviewNotice />
    <Card title="Weekly schedule">
      <SessionIdGuide />
      <div className="manager-filters">
        <Select label="Class" placeholder="All classes" value={classId ? String(classId) : ''} options={classes.status === 'ready' ? classes.data.map((item) => ({ value: String(item.id), label: `${item.name}${item.active ? '' : ' (Inactive)'}` })) : []} onChange={(event) => query('classId', event.target.value)} />
        <Select label="Coach" placeholder="All coaches" value={coachId ? String(coachId) : ''} options={coaches.status === 'ready' ? coaches.data.map((item) => ({ value: String(item.id), label: item.fullName })) : []} onChange={(event) => query('coachId', event.target.value)} />
        {manualFacility ? <Input label="Facility ID filter" type="number" min="1" step="1" placeholder="All facilities" value={facilityId ?? ''} onChange={(event) => query('facilityId', event.target.value)} /> : <Select label="Facility" placeholder="All facilities" value={facilityId ? String(facilityId) : ''} options={facilities.status === 'ready' ? facilities.data.map((item) => ({ value: String(item.id), label: item.name })) : []} onChange={(event) => query('facilityId', event.target.value)} />}
      </div>
      {classes.status === 'error' && <ManagerError error={classes.error} onRetry={retryClasses} />}
      {coaches.status === 'error' && <ManagerError error={coaches.error} onRetry={retryCoaches} />}
      {manualFacility ? <div className="notice notice--info">The facility dropdown API is not available. You can still create and edit sessions using a known facility ID; the scheduling API validates it. No sample IDs are substituted. <Button variant="ghost" onClick={retryFacilities}>Retry facility lookup</Button></div> : facilities.status === 'error' && <ManagerError error={facilities.error} onRetry={retryFacilities} />}
      {classes.status === 'ready' && !classes.data.some((item) => item.active) && <p className="notice notice--info">Create an active class before scheduling sessions. <Link to="/manager/classes/new">Create class</Link></p>}
      <div className="manager-row-actions">
        <Button variant="secondary" onClick={() => query('week', addDays(week, -7))}>Previous week</Button>
        <Button variant={isThisWeek ? 'primary' : 'secondary'} onClick={() => query('week', monday(centerNow().date))}>This week</Button>
        <Button variant="secondary" onClick={() => query('week', addDays(week, 7))}>Next week</Button>
      </div>
      {sessions.status === 'loading' ? <PageLoader label="Loading schedule" /> : sessions.status === 'error' ? <ManagerError error={sessions.error} onRetry={reload} /> : <>
        {!sessions.data.length ? <EmptyState title="No sessions this week" description="Change filters or create a session for an active class." /> : (
          <>
          <p id="manager-schedule-grouping" className="f2-schedule-table__guidance">Sessions are grouped by start time. Each card shows its full time range; separate rows can overlap.</p>
          <div className="f2-schedule-table-wrap" role="region" aria-label="Weekly session timetable" tabIndex={0}>
            <table className="f2-schedule-table" aria-label="Manager weekly schedule" aria-describedby="manager-schedule-grouping">
              <thead>
                <tr>
                  <th className="f2-schedule-table__time-heading" scope="col">Starts at</th>
                  {dates.map((date, index) => {
                    const isToday = date === today
                    return <th className={isToday ? 'f2-schedule-table__today' : undefined} scope="col" key={date} aria-current={isToday ? 'date' : undefined}>
                      <span>{WEEKDAYS[index].slice(0, 3)}</span>
                      <time dateTime={date}>{displayDate(date)}</time>
                    </th>
                  })}
                </tr>
              </thead>
              <tbody>
                {timeSlots.map((time) => (
                  <tr key={time}>
                    <th scope="row">{time}</th>
                    {dates.map((date) => {
                      const cellSessions = sessionsByDateAndTime.get(`${date}|${time}`) ?? []
                      const isToday = date === today
                      return <td className={isToday ? 'f2-schedule-table__today' : undefined} key={date}>
                        {cellSessions.length ? <div className="f2-schedule-table__entries">
                          {cellSessions.map((session) => <ManagerScheduleEntry
                            key={session.id}
                            session={session}
                            canManage={canManageSession(session)}
                            editDisabled={!facilitiesUsable || classes.status !== 'ready'}
                            onEdit={() => setEditing(session)}
                            onCancel={() => setCancelling(session)}
                            onPublish={() => setPublishing(session)}
                            onDelete={() => setDeleting(session)}
                          />)}
                        </div> : <span className="f2-schedule-table__empty-cell" aria-label="No sessions">-</span>}
                      </td>
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          </>
        )}
      </>}
    </Card>
    {editing && classes.status === 'ready' && facilitiesUsable && <SessionEditorModal item={editing === 'new' ? undefined : editing} classes={classes.data} facilities={facilities.status === 'ready' ? facilities.data : []} manualFacility={manualFacility} classId={classId} onClose={closeEditor} onSuccess={sessionSaved} />}
    {cancelling && <CancelSessionModal item={cancelling} onClose={() => setCancelling(null)} onSuccess={saved} />}
    {publishing && <ConfirmationModal title="Publish session" description={`${publishing.class?.name ?? 'Session'} on ${displayDate(publishing.date)} at ${publishing.startTime.slice(0, 5)}: members will be able to find and book it.`} onClose={() => setPublishing(null)} save={() => managerSessionsApi.publish(publishing.id)} onSuccess={() => { setPublishing(null); reload(); toast.success(`Session ${sessionIdText(publishing.sessionId)} published`) }} confirmLabel="Publish session" />}
    {deleting && <DeleteReasonModal title="Delete Session" entityName={`Session ${sessionIdText(deleting.sessionId)}: ${deleting.class?.name ?? 'Class'} on ${displayDate(deleting.date)}`} description="Deleting a session removes it from the schedule and records the action in the audit log. It will fail if there are active bookings." onClose={() => setDeleting(null)} onConfirm={async (reason) => { await managerSessionsApi.delete(deleting.id, reason) }} onSuccess={() => { setDeleting(null); reload(); toast.success('Session deleted', 'The session has been removed and audit log updated.') }} />}
  </div>
}

function ManagerScheduleEntry({
  session,
  canManage,
  editDisabled,
  onEdit,
  onCancel,
  onPublish,
  onDelete,
}: {
  session: ManagerSession
  canManage: boolean
  editDisabled: boolean
  onEdit: () => void
  onCancel: () => void
  onPublish: () => void
  onDelete: () => void
}) {
  const [expanded, setExpanded] = useState(false)

  const toggleExpanded = (event: React.MouseEvent | React.KeyboardEvent) => {
    const target = event.target as HTMLElement
    if (target.closest('button') || target.closest('a') || target.closest('input')) {
      return
    }
    setExpanded((prev) => !prev)
  }

  return (
    <article
      className={`f2-session f2-session--${session.status.toLowerCase()}${expanded ? ' f2-session--expanded' : ' f2-session--collapsed'}`}
      onClick={toggleExpanded}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          toggleExpanded(event)
        }
      }}
      role="button"
      tabIndex={0}
      aria-expanded={expanded}
      aria-label={`${session.class?.name ?? 'Session'} (${sessionIdText(session.sessionId)})`}
      title={expanded ? 'Click to collapse details' : 'Click to view full details'}
    >
      <div className="f2-session__header">
        <span className="session-identity"><code>{sessionIdText(session.sessionId)}</code></span>
      </div>
      <strong className="f2-session__title">{session.class?.name ?? 'Unassigned class'}</strong>
      <div className="f2-session__timing">
        <span>{session.startTime.slice(0, 5)} - {session.endTime.slice(0, 5)}</span>
        <StatusBadge status={session.status} />
      </div>
      <p className="f2-session__meta f2-session__booked">{session.bookedCount} / {session.capacity} booked</p>

      <div className={`f2-session__details ${expanded ? 'f2-session__details--expanded' : 'f2-session__details--collapsed'}`}>
        <p className="f2-session__meta">Sport: {session.sportName}</p>
        <p className="f2-session__meta">{session.trainingType === 'SELF_TRAINING' && !session.coach ? 'Self-training - No assigned coach' : `Coach: ${session.coach?.name ?? 'Coach not assigned'}`}</p>
        <p className="f2-session__meta">Facility: {session.facility?.name ?? 'Not assigned'}</p>
        <p className="f2-session__meta">Type: {session.trainingType === 'COACH_LED' ? 'Coach-led' : 'Self-training'}</p>
        {session.status === 'CANCELLED' && <p className="f2-session__meta f2-session__reason">Reason: {session.cancelReason || 'Not provided'}</p>}
      </div>

      <div className="f2-session__actions" onClick={(e) => e.stopPropagation()}>
        {session.status === 'DRAFT' && <Button size="sm" onClick={onPublish}>Publish</Button>}
        {canManage && <Button size="sm" variant="secondary" disabled={editDisabled} onClick={onEdit}>Edit</Button>}
        {canManage && session.status === 'PUBLISHED' && <Button size="sm" variant="danger" aria-label="Cancel session" title="Cancel session" onClick={onCancel}>Cancel</Button>}
        <Button size="sm" variant="danger" aria-label="Delete session" title="Delete session" onClick={onDelete}>Delete</Button>
      </div>
    </article>
  )
}
