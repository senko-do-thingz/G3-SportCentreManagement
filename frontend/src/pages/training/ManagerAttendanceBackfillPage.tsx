import { useState, type FormEvent } from 'react'
import { attendanceBackfillApi as api, type BackfillWorkspace } from '../../api/attendanceBackfillApi'
import type { AttendanceStatus } from '../../api/staffTrainingApi'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { Select } from '../../components/ui/Select'
import { TrainingData } from '../member/TrainingData'
import { ActionMessage, TrainingTextarea } from './TrainingFormFields'
import { attendanceOptions, useTrainingAction } from './trainingFormState'
import '../member/training.css'

export function ManagerAttendanceBackfillPage() {
  const [revision, refresh] = useState(0)
  const [saved, setSaved] = useState(false)
  return <div className="training-page">
    <PageHeader title="Missed attendance" description="Manager-only administrative recovery for a completed past session with a confirmed booking and no saved attendance. A reason and permanent audit are required." />
    {saved && <p role="status">Attendance backfilled and audited. The member can view the saved status.</p>}
    <TrainingData key={revision} load={api.workspace}>{workspace => <BackfillForm workspace={workspace} onSaved={() => { setSaved(true); refresh(n => n + 1) }} />}</TrainingData>
  </div>
}

function BackfillForm({ workspace, onSaved }: { workspace: BackfillWorkspace; onSaved: () => void }) {
  const [bookingId, select] = useState('')
  const action = useTrainingAction()
  const booking = workspace.bookings.find(item => String(item.bookingId) === bookingId)
  if (!workspace.enabled) return <Card title="Backfill unavailable"><p role="status">{workspace.message}</p></Card>
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    void action.run(async () => {
      const reason = String(values.get('reason') ?? '').trim()
      if (!booking || reason.length < 3 || reason.length > 500) throw new Error('Select a booking and enter a meaningful reason of 3 to 500 characters.')
      await api.save({ bookingId: booking.bookingId, status: String(values.get('status')) as AttendanceStatus,
        reason, notes: String(values.get('notes') ?? '').trim() || null })
      onSaved()
    }, 'Attendance backfilled and audited.')
  }
  return <Card title="Recover a missing attendance record">
    <p>Only an enabled Manager may use this action. Existing records must use the correction workflow. Eligibility and the configured cutoff are checked again when saved.</p>
    {!workspace.bookings.length ? <EmptyState title="No eligible missed bookings" description="No recent confirmed booking is available within the configured administrative window." />
      : <form className="training-form" onSubmit={submit}>
        <fieldset className="training-fieldset" disabled={action.pending}>
          <Select label="Missed booking" required value={bookingId} onChange={event => select(event.target.value)} placeholder="Select a member and session"
            options={workspace.bookings.map(item => ({ value: String(item.bookingId), label: `${item.memberName} - ${item.className} - ${item.date} - Booking #${item.bookingId}` }))} />
          {booking && <p>Session #{booking.sessionId}: {booking.date}, {booking.startTime.slice(0, 5)} to {booking.endTime.slice(0, 5)} (Asia/Ho_Chi_Minh). Current attendance: Unmarked.</p>}
          <Select label="Backfill status" name="status" required defaultValue="PRESENT" options={attendanceOptions} />
          <TrainingTextarea label="Administrative reason" name="reason" required minLength={3} maxLength={500} />
          <TrainingTextarea label="Attendance notes" name="notes" maxLength={500} />
          <Button type="submit" loading={action.pending} disabled={!booking}>Save administrative attendance</Button>
        </fieldset>
        <ActionMessage {...action} />
      </form>}
  </Card>
}
