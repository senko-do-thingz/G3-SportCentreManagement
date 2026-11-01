import { useCallback, useState, type FormEvent } from 'react'
import { staffTrainingApi as training, type AttendanceStatus, type SessionMember, type StaffSession } from '../../api/staffTrainingApi'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { Select } from '../../components/ui/Select'
import { TrainingData } from '../member/TrainingData'
import { ActionMessage, TrainingTextarea } from './TrainingFormFields'
import { attendanceLabel, attendanceOptions, useTrainingAction } from './trainingFormState'
import '../member/training.css'

export function ReceptionTrainingAttendancePage() {
  return (
    <div className="training-page">
      <PageHeader title="Session attendance" description="Record attendance using member bookings. Enter a reason when correcting a saved record to keep an audit history." />
      <TrainingData load={training.receptionSessions}>{sessions => <AttendanceSessionPicker sessions={sessions} />}</TrainingData>
    </div>
  )
}

function AttendanceSessionPicker({ sessions }: { sessions: StaffSession[] }) {
  const [sessionId, setSessionId] = useState('')
  return (
    <>
      <Card title="Select a session">
        <Select label="Session to mark attendance" value={sessionId} onChange={event => setSessionId(event.target.value)} placeholder="Select a session"
          options={sessions.map(item => ({ value: String(item.id), label: `${item.className} · ${item.date} · ${item.startTime.slice(0, 5)}` }))} />
        {!sessions.length && <p>No sessions available.</p>}
      </Card>
      {sessionId && <AttendanceWorkspace key={sessionId} sessionId={sessionId} />}
    </>
  )
}

function AttendanceWorkspace({ sessionId }: { sessionId: string }) {
  const [revision, refresh] = useState(0)
  const [saved, setSaved] = useState(false)
  const load = useCallback((signal: AbortSignal) => training.receptionRoster(sessionId, signal), [sessionId])
  return (
    <Card title="Bookings and attendance status">
      {saved && <p role="status">Attendance saved and the list updated.</p>}
      <TrainingData key={revision} load={load}>{members => <AttendanceRoster members={members} onSaved={() => { setSaved(true); refresh(value => value + 1) }} />}</TrainingData>
    </Card>
  )
}

function AttendanceRoster({ members, onSaved }: { members: SessionMember[]; onSaved: () => void }) {
  const [bookingId, setBookingId] = useState('')
  const member = members.find(item => String(item.bookingId) === bookingId)
  if (!members.length) return <EmptyState title="No valid bookings for this session" />
  return (
    <div className="training-form">
      <Select label="Booking to mark attendance" value={bookingId} onChange={event => setBookingId(event.target.value)} placeholder="Select a member and booking"
        options={members.map(item => ({ value: String(item.bookingId), label: `${item.memberName} · Booking #${item.bookingId} · ${attendanceLabel(item.status)}` }))} />
      {member && <AttendanceForm key={member.bookingId} member={member} onSaved={onSaved} />}
    </div>
  )
}

function AttendanceForm({ member, onSaved }: { member: SessionMember; onSaved: () => void }) {
  const action = useTrainingAction()
  const correcting = member.attendanceId !== null
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    void action.run(async () => {
      const input = { status: String(values.get('status')) as AttendanceStatus, notes: String(values.get('notes')).trim() || null }
      if (correcting) {
        const reason = String(values.get('reason')).trim()
        if (!reason) throw new Error('Enter a reason for the attendance correction.')
        await training.correctAttendance(member.attendanceId!, { ...input, reason })
      } else {
        await training.attendance(member.bookingId, input)
      }
      onSaved()
    }, correcting ? 'Attendance corrected and the reason saved.' : 'Attendance saved.')
  }
  return (
    <form className="training-form" onSubmit={submit}>
      <h3>{correcting ? 'Correct attendance' : 'Record attendance'} - {member.memberName}</h3>
      <fieldset className="training-fieldset" disabled={action.pending}>
        <Select label="Attendance status" name="status" required defaultValue={member.status ?? 'PRESENT'} options={attendanceOptions} />
        <TrainingTextarea label="Attendance notes" name="notes" maxLength={500} defaultValue={member.notes ?? ''} />
        {correcting && <TrainingTextarea label="Correction reason" name="reason" maxLength={500} required />}
        <Button type="submit" loading={action.pending}>{correcting ? 'Save correction' : 'Save attendance'}</Button>
      </fieldset>
      <ActionMessage {...action} />
    </form>
  )
}
