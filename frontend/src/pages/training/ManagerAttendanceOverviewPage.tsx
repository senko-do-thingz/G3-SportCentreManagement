import { useCallback, useState } from 'react'
import { managerAttendanceApi as api } from '../../api/managerAttendanceApi'
import { managerClassesApi } from '../../api/managerClassesApi'
import { businessDate, formatCalendarDate } from '../../utils/date'
import { Button, CalendarDateField, Card, Input, Select } from '../../components/ui'
import { TrainingData } from '../member/TrainingData'
import { PreviewNotice } from '../manager/ManagerShared'
import '../member/training.css'

const loadCoaches = (signal: AbortSignal) => managerClassesApi.coaches(undefined, signal)
export function ManagerAttendanceOverviewPage() {
  const [from, setFrom] = useState(businessDate())
  const [to, setTo] = useState(businessDate())
  const [classId, setClassId] = useState('')
  const [classSearch, setClassSearch] = useState('')
  const [coachId, setCoachId] = useState('')
  const [selected, select] = useState('')
  const load = useCallback((signal: AbortSignal) => api.overview(from, to, classId, coachId, signal), [from, to, classId, coachId])
  const detail = useCallback((signal: AbortSignal) => api.detail(selected, signal), [selected])
  const loadClasses = useCallback((signal: AbortSignal) => managerClassesApi.list({ size: 100, search: classSearch || undefined }, signal), [classSearch])
  return <div className="page manager-page training-page manager-concept-page">
    <div className="concept-header">
      <div className="concept-header__main">
        <div className="concept-eyebrow">MANAGER WORKSPACE</div>
        <h1 className="concept-title">Attendance Overview</h1>
        <p className="concept-sub">Read session attendance across the center. Cancelled sessions and bookings are excluded from summary counts.</p>
      </div>
    </div>

    <div className="concept-hero">
      <div>
        <small>ATTENDANCE MONITOR</small>
        <h2>SESSION PARTICIPATION. <span className="concept-goldtext">AUDIT COMPLIANCE.</span></h2>
        <p>Review confirmed attendance rates, late arrivals and unmarked records across scheduled sessions.</p>
      </div>
    </div>

    <PreviewNotice />
    <Card title="Attendance filters">
      <div className="training-form-grid">
        <CalendarDateField label="From date" value={from} max={to} onChange={value => { select(''); setFrom(value) }} required />
        <CalendarDateField label="To date" value={to} min={from} onChange={value => { select(''); setTo(value) }} required />
        <Input label="Search classes" value={classSearch} onChange={event => { setClassSearch(event.target.value); setClassId(''); select('') }} />
        <TrainingData load={loadClasses}>{page => <Select label="Class" placeholder="All classes" value={classId} options={page.content.map(item => ({ value: String(item.id), label: item.name }))} onChange={event => { select(''); setClassId(event.target.value) }} hint={page.totalElements > page.content.length ? 'Showing the first 100 matches. Refine the class search to find another class.' : undefined} />}</TrainingData>
        <TrainingData load={loadCoaches}>{coaches => <Select label="Coach" placeholder="All coaches" value={coachId} options={coaches.map(item => ({ value: String(item.id), label: item.fullName }))} onChange={event => { select(''); setCoachId(event.target.value) }} />}</TrainingData>
      </div>
      <p>Choose up to 31 days. Attendance rate = (Present + Late) / confirmed bookings for completed sessions. Unmarked and excused bookings remain in that denominator. Unmarked is not ABSENT. A dash means the rate is not applicable or has a zero denominator.</p>
    </Card>
    <TrainingData load={load}>{overview => <>
      <div className="concept-stats">
        <div className="concept-stat">
          <span className="concept-stat__icon" aria-hidden="true">S</span>
          <div>
            <strong>{String(overview.sessions.length).padStart(2, '0')}</strong>
            <small>Matching sessions</small>
          </div>
        </div>
        <div className="concept-stat">
          <span className="concept-stat__icon" aria-hidden="true">C</span>
          <div>
            <strong>{String(overview.sessions.reduce((sum, item) => sum + item.confirmed, 0)).padStart(2, '0')}</strong>
            <small>Confirmed bookings</small>
          </div>
        </div>
        <div className="concept-stat">
          <span className="concept-stat__icon" aria-hidden="true">P</span>
          <div>
            <strong>{String(overview.sessions.reduce((sum, item) => sum + item.present + item.late, 0)).padStart(2, '0')}</strong>
            <small>Present + Late</small>
          </div>
        </div>
        <div className="concept-stat">
          <span className="concept-stat__icon" aria-hidden="true">U</span>
          <div>
            <strong>{String(overview.sessions.reduce((sum, item) => sum + item.unmarked, 0)).padStart(2, '0')}</strong>
            <small>Unmarked bookings</small>
          </div>
        </div>
      </div>
      <Card title="Session attendance">
        {overview.truncated && <p role="status">Only the newest 200 sessions are shown. Narrow the date, class or Coach filters.</p>}
        {overview.sessions.length === 0 ? <p role="status">No sessions match these filters.</p> : <table className="table"><caption>Attendance by session</caption><thead><tr><th>Session</th><th>Class / Coach</th><th>Confirmed</th><th>Present</th><th>Late</th><th>Absent</th><th>Excused</th><th>Unmarked</th><th>Rate</th><th>Action</th></tr></thead><tbody>{overview.sessions.map(item => <tr key={item.id}><td>{item.sessionCode}<br />{formatCalendarDate(item.date)} {item.startTime.slice(0,5)}</td><td>{item.className}<br />{item.coachName ?? 'No Coach'}</td><td>{item.confirmed}</td><td>{item.present}</td><td>{item.late}</td><td>{item.absent}</td><td>{item.excused}</td><td>{item.unmarked}</td><td>{item.attendanceRate == null ? '-' : `${item.attendanceRate}%`}</td><td><Button variant="secondary" onClick={() => select(item.sessionCode)}>View {item.sessionCode}</Button></td></tr>)}</tbody></table>}
      </Card>
    </>}</TrainingData>
    {selected && <TrainingData load={detail}>{data => <Card title={`Attendance details - ${data.session.sessionCode}`}>
      <p>Cancelled bookings are shown as history and are excluded from summary counts.</p>
      {data.members.length === 0 ? <p>No bookings for this session.</p> : <table className="table"><caption>Session attendance details</caption><thead><tr><th>Member</th><th>Booking</th><th>Attendance</th><th>Marked by / at</th><th>Notes</th></tr></thead><tbody>{data.members.map(item => <tr key={item.bookingId}><td>{item.memberCode} - {item.memberName}</td><td>{item.bookingStatus}</td><td>{item.attendanceStatus ?? 'Unmarked'}</td><td>{item.markedBy ?? '-'}<br />{item.markedAt ?? '-'}</td><td>{item.notes ?? '-'}</td></tr>)}</tbody></table>}
    </Card>}</TrainingData>}
  </div>
}
