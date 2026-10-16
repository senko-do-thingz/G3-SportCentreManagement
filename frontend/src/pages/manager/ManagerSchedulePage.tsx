import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { managerClassesApi } from '../../api/managerClassesApi'
import { managerSessionsApi, type ManagerSession } from '../../api/managerSessionsApi'
import { facilitiesApi } from '../../api/facilitiesApi'
import { Button, Card, EmptyState, PageHeader, PageLoader, Select, StatusBadge, useToast } from '../../components/ui'
import { ManagerError, PreviewNotice } from './ManagerShared'
import { useResource } from './useManagerTools'
import { addDays, canManageSession, centerNow, displayDate, monday, WEEKDAYS } from './scheduleModel'
import { SessionEditorModal } from './SessionEditorModal'
import { CancelSessionModal } from './CancelSessionModal'

const loadClasses = async (signal: AbortSignal) => {
  const first = await managerClassesApi.list({ size: 100 }, signal)
  const items = [...first.content]
  for (let page = 1; page < first.totalPages; page++) items.push(...(await managerClassesApi.list({ size: 100, page }, signal)).content)
  return items
}
const loadCoaches = (signal: AbortSignal) => managerClassesApi.coaches(undefined, signal)
const loadFacilities = (signal: AbortSignal) => facilitiesApi.list(signal)
const id = (value: string | null) => value && /^\d+$/.test(value) && Number(value) > 0 ? Number(value) : undefined

export function ManagerSchedulePage() {
  const [params, setParams] = useSearchParams()
  const week = monday(params.get('week') ?? '')
  const classId = id(params.get('classId'))
  const coachId = id(params.get('coachId'))
  const facilityId = id(params.get('facilityId'))
  const [classes, retryClasses] = useResource(loadClasses)
  const [coaches, retryCoaches] = useResource(loadCoaches)
  const [facilities, retryFacilities] = useResource(loadFacilities)
  const loader = useCallback((signal: AbortSignal) => managerSessionsApi.list({ from: week, to: addDays(week, 6), classId, coachId, facilityId }, signal), [week, classId, coachId, facilityId])
  const [sessions, reload] = useResource(loader)
  const [editing, setEditing] = useState<ManagerSession | 'new' | null>(params.get('create') === '1' ? 'new' : null)
  const [cancelling, setCancelling] = useState<ManagerSession | null>(null)
  const toast = useToast()
  function query(key: string, value: string) { setParams((previous) => { const next = new URLSearchParams(previous); if (value) next.set(key, value); else next.delete(key); next.delete('create'); return next }) }
  function closeEditor() { setEditing(null); if (params.has('create')) query('create', '') }
  function saved() { closeEditor(); setCancelling(null); reload(); toast.success('Schedule updated') }
  const dates = Array.from({ length: 7 }, (_, index) => addDays(week, index))
  const available = classes.status === 'ready' && facilities.status === 'ready' && facilities.data.some((item) => item.active !== false) && classes.data.some((item) => item.active)
  return <div className="page manager-page">
    <PageHeader eyebrow="Class management" title="Schedule & Coach Assignment" description="Plan single sessions or a weekly schedule. Times use Vietnam time (UTC+7)." actions={<Button disabled={!available} onClick={() => setEditing('new')}>+ Create session</Button>} />
    <PreviewNotice />
    <Card title="Weekly schedule">
      <div className="manager-filters">
        <Select label="Class" placeholder="All classes" value={classId ? String(classId) : ''} options={classes.status === 'ready' ? classes.data.map((item) => ({ value: String(item.id), label: `${item.name}${item.active ? '' : ' (Inactive)'}` })) : []} onChange={(event) => query('classId', event.target.value)} />
        <Select label="Coach" placeholder="All coaches" value={coachId ? String(coachId) : ''} options={coaches.status === 'ready' ? coaches.data.map((item) => ({ value: String(item.id), label: item.fullName })) : []} onChange={(event) => query('coachId', event.target.value)} />
        <Select label="Facility" placeholder="All facilities" value={facilityId ? String(facilityId) : ''} options={facilities.status === 'ready' ? facilities.data.map((item) => ({ value: String(item.id), label: item.name })) : []} onChange={(event) => query('facilityId', event.target.value)} />
      </div>
      {classes.status === 'error' && <ManagerError error={classes.error} onRetry={retryClasses} />}
      {coaches.status === 'error' && <ManagerError error={coaches.error} onRetry={retryCoaches} />}
      {facilities.status === 'error' && <><p className="notice notice--info">Facility lookup is unavailable. Session creation and editing require the backend facility-list API; existing sessions remain visible.</p><ManagerError error={facilities.error} onRetry={retryFacilities} /></>}
      <div className="manager-row-actions"><Button variant="secondary" onClick={() => query('week', addDays(week, -7))}>Previous week</Button><Button variant="secondary" onClick={() => query('week', monday(centerNow().date))}>This week</Button><Button variant="secondary" onClick={() => query('week', addDays(week, 7))}>Next week</Button><strong>{displayDate(week)} - {displayDate(addDays(week, 6))}</strong></div>
      {sessions.status === 'loading' ? <PageLoader label="Loading schedule" /> : sessions.status === 'error' ? <ManagerError error={sessions.error} onRetry={reload} /> : <>
        {!sessions.data.length && <EmptyState title="No sessions this week" description="Change filters or create a session for an active class." />}
        <div className="f2-calendar">{dates.map((date, index) => <section className="f2-day" key={date} aria-label={`${WEEKDAYS[index]} ${displayDate(date)}`}><h3>{WEEKDAYS[index].slice(0, 3)} {displayDate(date)}</h3>{sessions.data.filter((session) => session.date === date).sort((a, b) => a.startTime.localeCompare(b.startTime) || a.id - b.id).map((session) => <article key={session.id} className={`f2-session f2-session--${session.status.toLowerCase()}`}>
          <strong>{session.class?.name ?? 'Unassigned class'}</strong><p>{session.startTime.slice(0, 5)} - {session.endTime.slice(0, 5)}</p><StatusBadge status={session.status} /><p>Coach: {session.coach?.name ?? 'Not assigned'}</p><p>Facility: {session.facility?.name ?? 'Not assigned'}</p><p>{session.bookedCount} / {session.capacity} booked</p><p>{session.trainingType === 'COACH_LED' ? 'Coach-led' : 'Self-training'}</p>
          {session.status === 'CANCELLED' && <p>Reason: {session.cancelReason || 'Not provided'}</p>}
          {canManageSession(session) && <div className="manager-row-actions"><Button size="sm" variant="secondary" disabled={facilities.status !== 'ready' || classes.status !== 'ready'} onClick={() => setEditing(session)}>Edit</Button><Button size="sm" variant="danger" onClick={() => setCancelling(session)}>Cancel session</Button></div>}
        </article>)}</section>)}</div>
      </>}
    </Card>
    {editing && classes.status === 'ready' && facilities.status === 'ready' && <SessionEditorModal item={editing === 'new' ? undefined : editing} classes={classes.data} facilities={facilities.data} classId={classId} onClose={closeEditor} onSuccess={saved} />}
    {cancelling && <CancelSessionModal item={cancelling} onClose={() => setCancelling(null)} onSuccess={saved} />}
  </div>
}
