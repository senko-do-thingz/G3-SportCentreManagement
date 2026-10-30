import { useState, type FormEvent } from 'react'
import { trainingApi, type AttendanceStatus, type AttendanceRow } from '../../api/trainingApi'
import { Badge, Button, Card, Input, PageHeader, Select } from '../../components/ui'

export function ReceptionTrainingAttendancePage() {
  const [bookingId, setBookingId] = useState('')
  const [status, setStatus] = useState<AttendanceStatus>('PRESENT')
  const [result, setResult] = useState<AttendanceRow | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); setError(null); setResult(null); try { setResult(await trainingApi.receptionAttendance(Number(bookingId), status)) } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not record attendance.') } finally { setSaving(false) } }
  return <div className="page training-page"><PageHeader eyebrow="Reception" title="Training Attendance" description="Record self-training attendance from a confirmed booking. Front-desk arrival check-in remains a separate record." /><Card title="Record attendance"><form className="training-form" onSubmit={submit}><div className="training-form-grid"><Input label="Booking ID" required type="number" min="1" value={bookingId} onChange={(e) => setBookingId(e.target.value)} /><Select label="Attendance status" required value={status} onChange={(e) => setStatus(e.target.value as AttendanceStatus)} options={['PRESENT', 'LATE', 'ABSENT', 'EXCUSED'].map((value) => ({ value, label: value }))} /></div><p className="notice notice--info">This action records training participation. It does not create a booking or deduct a visit a second time.</p>{error && <p className="notice notice--error" role="alert">{error}</p>}<div className="training-actions"><Button type="submit" disabled={!/^\d+$/.test(bookingId) || saving}>{saving ? 'Saving…' : 'Record attendance'}</Button></div></form></Card>{result && <Card title="Attendance recorded" actions={<Badge tone="success">{result.status}</Badge>}><dl className="training-metrics"><div><dt>Member</dt><dd>{result.memberName}</dd></div><div><dt>Member code</dt><dd>{result.memberCode}</dd></div><div><dt>Booking</dt><dd>#{result.bookingId}</dd></div></dl></Card>}</div>
}
