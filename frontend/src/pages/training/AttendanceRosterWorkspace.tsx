import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { staffTrainingApi as training, type AttendanceStatus, type SessionMember, type StaffSession } from '../../api/staffTrainingApi'
import { sessionIdText } from '../../api/sessionIdentity'
import { formatCalendarDate } from '../../utils/date'
import { SessionContext } from '../../components/SessionContext'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { EmptyState } from '../../components/ui/EmptyState'
import { Input } from '../../components/ui/Input'
import { PageHeader } from '../../components/ui/PageHeader'
import { TrainingData } from '../member/TrainingData'
import { ActionMessage } from './TrainingFormFields'
import { attendanceLabel, attendanceOptions, useTrainingAction } from './trainingFormState'
import '../member/training.css'
import './attendanceRoster.css'

const key = (session: StaffSession) => session.sessionId || String(session.id)
const actionable = (session: StaffSession) => session.attendanceDateOpen === true
  && typeof session.bookedCount === 'number' && typeof session.markedCount === 'number'
  && session.bookedCount > session.markedCount

export function ReceptionTrainingAttendancePage() { return <AttendanceBrowser coach={false} /> }
export function CoachTrainingAttendancePage() { return <AttendanceBrowser coach /> }

function AttendanceBrowser({ coach }: { coach: boolean }) {
  const [revision, refresh] = useState(0)
  const [selected, select] = useState('')
  const [search, setSearch] = useState('')
  const [message, setMessage] = useState('')
  const [focusBooking, setFocusBooking] = useState<number | null>(null)
  return <div className="training-page">
    <PageHeader title="Session attendance" description={coach
      ? 'Record participation for your assigned sessions. Corrections require a reason. Saved results require Present or Late attendance.'
      : 'Record attendance using confirmed member bookings. Enter a reason when correcting a saved record to keep an audit history.'} />
    {message && <p role="status" aria-live="polite">{message}</p>}
    <TrainingData key={revision} load={coach ? training.coachSessions : training.receptionSessions}>{sessions => {
      const ordered = [...sessions].sort((a, b) => Number(actionable(b)) - Number(actionable(a)))
      const active = ordered.find(session => key(session) === selected) || (!selected ? ordered.find(actionable) : undefined)
      const query = search.trim().toLowerCase()
      const visible = ordered.filter(session => [session.sessionId, session.className, session.sportName, session.coachName, session.facilityName, formatCalendarDate(session.date), session.date].some(value => value?.toLowerCase().includes(query)))
      return <>
        <Card title="Select a session">
          <Input label="Search sessions" type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Session ID, class, Coach or facility" />
          <p className="attendance-help">Sessions with attendance to record today appear first. Counts reflect confirmed bookings. Unmarked does not mean absent.</p>
          {!sessions.length ? <EmptyState title="No sessions available" /> : !visible.length ? <EmptyState title="No matching sessions" description="Search by Session ID, class, Coach or facility." /> : <ul className="attendance-session-list" aria-label="Attendance sessions">
            {visible.map(session => <li key={key(session)}>
              <button type="button" className={`attendance-session-card${active?.id === session.id ? ' is-selected' : ''}${actionable(session) ? ' is-actionable' : ''}`}
                aria-pressed={active?.id === session.id} aria-label={`Select session ${sessionIdText(session.sessionId)} - ${session.className}`}
                onClick={() => { select(key(session)); setMessage(''); setFocusBooking(null) }}>
                <SessionContext {...session} />
                <span className="attendance-session-state">{session.attendanceState === 'UPCOMING' ? 'Upcoming - first marking is not open' : session.attendanceDateOpen === true ? 'Today - attendance open' : 'Closed for first marking'}</span>
                <span>{session.status || 'Session status unavailable'}</span>
                <span>{typeof session.bookedCount === 'number' && typeof session.markedCount === 'number'
                  ? `${session.markedCount} of ${session.bookedCount} marked${session.markedCount > 0 && session.markedCount < session.bookedCount ? ' - Partially marked' : ''}`
                  : 'Attendance totals unavailable'}</span>
                {actionable(session) && <strong className="attendance-actionable">Attendance to record today</strong>}
                {active?.id === session.id && <span>Selected</span>}
              </button>
            </li>)}
          </ul>}
        </Card>
        {active ? <AttendanceWorkspace session={active} coach={coach} focusBooking={focusBooking}
          onActionStart={() => setMessage('')}
          onRefresh={() => refresh(value => value + 1)}
          onSaved={(bookingId, correcting) => { setFocusBooking(bookingId); setMessage(correcting ? 'Attendance corrected and the reason saved. The roster is updated.' : 'Attendance saved and the list updated.'); select(key(active)); refresh(value => value + 1) }} />
          : <EmptyState title="Choose a session to view its member roster" />}
      </>
    }}</TrainingData>
  </div>
}

function AttendanceWorkspace({ session, coach, focusBooking, onSaved, onRefresh, onActionStart }: {
  session: StaffSession; coach: boolean; focusBooking: number | null; onSaved: (bookingId: number, correcting: boolean) => void; onRefresh: () => void; onActionStart: () => void
}) {
  const load = useCallback((signal: AbortSignal) => (coach ? training.coachRoster : training.receptionRoster)(key(session), signal), [session, coach])
  const dateOpen = session.attendanceDateOpen === true
  return <Card title={`Member roster - ${sessionIdText(session.sessionId)}`}>
    <div className="attendance-roster-heading"><SessionContext {...session} /><Button variant="secondary" onClick={onRefresh}>Refresh roster</Button></div>
    {!dateOpen && <p role="status">Initial attendance is closed for this session. Ordinary attendance is recorded only on the session date in Asia/Ho_Chi_Minh. Saved records remain visible; existing corrections require a reason.</p>}
    <TrainingData load={load}>{members => !members.length ? <EmptyState title="No valid bookings for this session" /> : <ul className="attendance-roster" aria-label="Member attendance roster">
      {members.map(member => <li key={member.bookingId}><AttendanceRow member={member} coach={coach} dateOpen={dateOpen} restoreFocus={focusBooking === member.bookingId} onActionStart={onActionStart} onSaved={() => onSaved(member.bookingId, member.attendanceId != null)} /></li>)}
    </ul>}</TrainingData>
  </Card>
}

function AttendanceRow({ member, coach, dateOpen, restoreFocus, onSaved, onActionStart }: {
  member: SessionMember; coach: boolean; dateOpen: boolean; restoreFocus: boolean; onSaved: () => void; onActionStart: () => void
}) {
  const action = useTrainingAction()
  const form = useRef<HTMLFormElement>(null)
  const correcting = member.attendanceId != null
  const options = member.resultId != null ? attendanceOptions.filter(option => option.value === 'PRESENT' || option.value === 'LATE') : attendanceOptions
  const [status, setStatus] = useState<AttendanceStatus>(() => (options.find(option => option.value === member.status)?.value ?? 'PRESENT') as AttendanceStatus)
  useEffect(() => { if (restoreFocus) form.current?.querySelector<HTMLButtonElement>('button[type="submit"]')?.focus() }, [restoreFocus])
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onActionStart()
    const values = new FormData(event.currentTarget)
    void action.run(async () => {
      const input = { status: String(values.get('status')) as AttendanceStatus, notes: String(values.get('notes')).trim() || null }
      if (correcting) {
        const reason = String(values.get('reason')).trim()
        if (reason.length < 3) throw new Error('Enter a meaningful correction reason of at least 3 characters.')
        await (coach ? training.coachCorrectAttendance : training.correctAttendance)(member.attendanceId!, { ...input, reason })
      } else {
        if (!dateOpen) throw new Error('Initial attendance is closed for this session.')
        await (coach ? training.coachAttendance : training.attendance)(member.bookingId, input)
      }
      onSaved()
    }, correcting ? 'Attendance corrected and the reason saved.' : 'Attendance saved.')
  }
  return <form ref={form} className="attendance-row" aria-label={`${correcting ? 'Correct' : 'Record'} attendance for ${member.memberName}, booking ${member.bookingId}`} onSubmit={submit}>
    <div className="attendance-member-heading"><h3>{member.memberName}</h3><span>Booking #{member.bookingId}</span><strong className="attendance-status">{attendanceLabel(member.status)}</strong></div>
    {!dateOpen && !correcting ? <p>Attendance not recorded. An unmarked record does not mean absent. This session is outside the ordinary attendance date.</p> : <>
      {member.resultId != null && <p role="status">A result is saved. Attendance must remain Present or Late.</p>}
      <fieldset className="attendance-row-fields" disabled={action.pending}>
        <fieldset className="attendance-status-choices" aria-label={`Attendance status for ${member.memberName}, booking ${member.bookingId}`}>
          <legend>Attendance status</legend>
          <div className="attendance-status-options">
            {options.map(option => <label className="attendance-status-option" key={option.value}>
              <input type="radio" name="status" value={option.value} checked={status === option.value} onChange={() => setStatus(option.value as AttendanceStatus)} onKeyDown={event => { if (event.key === 'Enter') event.preventDefault() }} required />
              <span>{option.label}</span><small aria-hidden="true">Selected</small>
            </label>)}
          </div>
          <p className="attendance-status-help">Choose a status, then save. Selection alone does not record attendance.</p>
        </fieldset>
        <Input label="Attendance note" aria-label={`Attendance note for ${member.memberName}, booking ${member.bookingId}`} name="notes" maxLength={500} placeholder="Short attendance note" defaultValue={member.notes ?? ''} />
        {correcting && <Input className="attendance-correction-reason" label="Correction reason" aria-label={`Correction reason for ${member.memberName}, booking ${member.bookingId}`} name="reason" minLength={3} maxLength={500} required placeholder="Explain the correction" hint="Required: 3 to 500 characters." />}
        <Button type="submit" loading={action.pending}>{correcting ? 'Save correction' : 'Save attendance'}</Button>
      </fieldset>
      <ActionMessage {...action} />
    </>}
  </form>
}
