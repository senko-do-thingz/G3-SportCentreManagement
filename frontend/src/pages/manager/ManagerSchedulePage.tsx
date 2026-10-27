import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { managerClassesApi } from '../../api/managerClassesApi'
import { managerSessionsApi, type ManagerSession } from '../../api/managerSessionsApi'
import { facilitiesApi } from '../../api/facilitiesApi'
import { Button, Card, EmptyState, Input, PageHeader, PageLoader, Select, StatusBadge, useToast } from '../../components/ui'
import { ManagerError, PreviewNotice } from './ManagerShared'
import { useResource } from './useManagerTools'
import { loadScheduleClasses } from './loadScheduleClasses'
import { addDays, canManageSession, centerNow, displayDate, monday, WEEKDAYS } from './scheduleModel'
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
  const toast = useToast()
  function query(key: string, value: string) { setParams((previous) => { const next = new URLSearchParams(previous); if (value) next.set(key, value); else next.delete(key); next.delete('create'); return next }) }
  function closeEditor() { setEditing(null); if (params.has('create')) query('create', '') }
  function saved() { closeEditor(); setCancelling(null); reload(); toast.success('Schedule updated') }
  const dates = Array.from({ length: 7 }, (_, index) => addDays(week, index))
  const today = centerNow().date
  const timeSlots = sessions.status === 'ready' ? [...new Set(sessions.data.map((session) => session.startTime.slice(0, 5)))].sort() : []
  const sessionsByDateAndTime = new Map<string, ManagerSession[]>()
  if (sessions.status === 'ready') {
    for (const session of sessions.data) {
      const key = `${session.date}|${session.startTime.slice(0, 5)}`
      sessionsByDateAndTime.set(key, [...(sessionsByDateAndTime.get(key) ?? []), session])
    }
  }
  const manualFacility = facilities.status === 'error' && facilities.error instanceof ApiError && facilities.error.status === 404
  const facilitiesUsable = manualFacility || (facilities.status === 'ready' && facilities.data.some((item) => item.active !== false))
  const available = classes.status === 'ready' && facilitiesUsable && classes.data.some((item) => item.active)
  return <div className="page manager-page">
    <PageHeader eyebrow="Class management" title="Schedule & Coach Assignment" description="Plan single sessions or a weekly schedule. Times use Vietnam time (UTC+7)." actions={<Button disabled={!available} onClick={() => setEditing('new')}>+ Create session</Button>} />
    <PreviewNotice />
    <Card title="Weekly schedule">
      <div className="manager-filters">
        <Select label="Class" placeholder="All classes" value={classId ? String(classId) : ''} options={classes.status === 'ready' ? classes.data.map((item) => ({ value: String(item.id), label: `${item.name}${item.active ? '' : ' (Inactive)'}` })) : []} onChange={(event) => query('classId', event.target.value)} />
        <Select label="Coach" placeholder="All coaches" value={coachId ? String(coachId) : ''} options={coaches.status === 'ready' ? coaches.data.map((item) => ({ value: String(item.id), label: item.fullName })) : []} onChange={(event) => query('coachId', event.target.value)} />
        {manualFacility ? <Input label="Facility ID filter" type="number" min="1" step="1" placeholder="All facilities" value={facilityId ?? ''} onChange={(event) => query('facilityId', event.target.value)} /> : <Select label="Facility" placeholder="All facilities" value={facilityId ? String(facilityId) : ''} options={facilities.status === 'ready' ? facilities.data.map((item) => ({ value: String(item.id), label: item.name })) : []} onChange={(event) => query('facilityId', event.target.value)} />}
      </div>
      {classes.status === 'error' && <ManagerError error={classes.error} onRetry={retryClasses} />}
      {coaches.status === 'error' && <ManagerError error={coaches.error} onRetry={retryCoaches} />}
      {manualFacility ? <div className="notice notice--info">The facility dropdown API is not available. You can still create and edit sessions using a known facility ID; the scheduling API validates it. No sample IDs are substituted. <Button variant="ghost" onClick={retryFacilities}>Retry facility lookup</Button></div> : facilities.status === 'error' && <ManagerError error={facilities.error} onRetry={retryFacilities} />}
      {classes.status === 'ready' && !classes.data.some((item) => item.active) && <p className="notice notice--info">Create an active class before scheduling sessions. <Link to="/manager/classes/new">Create class</Link></p>}
      <div className="manager-row-actions"><Button variant="secondary" onClick={() => query('week', addDays(week, -7))}>Previous week</Button><Button variant="secondary" onClick={() => query('week', monday(centerNow().date))}>This week</Button><Button variant="secondary" onClick={() => query('week', addDays(week, 7))}>Next week</Button><strong>{displayDate(week)} - {displayDate(addDays(week, 6))}</strong></div>
      {sessions.status === 'loading' ? <PageLoader label="Loading schedule" /> : sessions.status === 'error' ? <ManagerError error={sessions.error} onRetry={reload} /> : <>
        {!sessions.data.length ? <EmptyState title="No sessions this week" description="Change filters or create a session for an active class." /> : (
          <div className="f2-schedule-table-wrap" role="region" aria-label="Weekly session timetable" tabIndex={0}>
            <table className="f2-schedule-table" aria-label="Manager weekly schedule">
              <thead>
                <tr>
                  <th className="f2-schedule-table__time-heading" scope="col">Time</th>
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
                          />)}
                        </div> : <span className="f2-schedule-table__empty-cell" aria-label="No sessions">-</span>}
                      </td>
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </>}
    </Card>
    {editing && classes.status === 'ready' && facilitiesUsable && <SessionEditorModal item={editing === 'new' ? undefined : editing} classes={classes.data} facilities={facilities.status === 'ready' ? facilities.data : []} manualFacility={manualFacility} classId={classId} onClose={closeEditor} onSuccess={saved} />}
    {cancelling && <CancelSessionModal item={cancelling} onClose={() => setCancelling(null)} onSuccess={saved} />}
  </div>
}

function ManagerScheduleEntry({
  session,
  canManage,
  editDisabled,
  onEdit,
  onCancel,
}: {
  session: ManagerSession
  canManage: boolean
  editDisabled: boolean
  onEdit: () => void
  onCancel: () => void
}) {
  return <article className={`f2-session f2-session--${session.status.toLowerCase()}`}>
    <strong className="f2-session__title">{session.class?.name ?? 'Unassigned class'}</strong>
    <div className="f2-session__timing">
      <span>{session.startTime.slice(0, 5)} - {session.endTime.slice(0, 5)}</span>
      <StatusBadge status={session.status} />
    </div>
    <p className="f2-session__meta">Coach: {session.coach?.name ?? 'Not assigned'}</p>
    <p className="f2-session__meta">Facility: {session.facility?.name ?? 'Not assigned'}</p>
    <p className="f2-session__meta">{session.bookedCount} / {session.capacity} booked · {session.trainingType === 'COACH_LED' ? 'Coach-led' : 'Self-training'}</p>
    {session.status === 'CANCELLED' && <p className="f2-session__meta f2-session__reason">Reason: {session.cancelReason || 'Not provided'}</p>}
    {canManage && <div className="f2-session__actions">
      <Button size="sm" variant="secondary" disabled={editDisabled} onClick={onEdit}>Edit</Button>
      <Button size="sm" variant="danger" aria-label="Cancel session" title="Cancel session" onClick={onCancel}>Cancel</Button>
    </div>}
  </article>
}
