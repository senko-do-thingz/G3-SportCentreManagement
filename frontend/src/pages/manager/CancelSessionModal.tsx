import { useRef, useState } from 'react'
import { managerSessionsApi, type ManagerSession } from '../../api/managerSessionsApi'
import { Button, Modal, TextArea } from '../../components/ui'
import { displayDate } from './scheduleModel'
import { useManagerForm } from './useManagerTools'

export function CancelSessionModal({ item, onClose, onSuccess }: { item: ManagerSession; onClose: () => void; onSuccess: () => void }) {
  const ref = useRef<HTMLFormElement>(null)
  const [reason, setReason] = useState('')
  const form = useManagerForm(ref, ['reason'], (): Record<string, string> => reason.trim().length < 1 || reason.trim().length > 255 ? { reason: 'Enter a cancellation reason of 1 to 255 characters.' } : {}, () => managerSessionsApi.cancel(item.id, reason), onSuccess)
  return <Modal open title="Cancel session" description={`${item.class?.name ?? 'Session'} - ${displayDate(item.date)} ${item.startTime.slice(0, 5)}`} dismissible={!form.saving} onClose={onClose}>
    <form ref={ref} onSubmit={form.submit} className="manager-form" noValidate>
      <p className="notice notice--warning">This session has {item.bookedCount} bookings. Cancellation restores eligible session credits and cannot be undone here.</p>
      {form.error && <p role="alert" className="notice notice--danger">{form.error}</p>}
      <TextArea label="Cancellation reason" name="reason" value={reason} maxLength={255} onChange={(event) => { setReason(event.target.value); form.clear('reason') }} error={form.errors.reason} disabled={form.saving} required />
      <div className="manager-form-actions"><Button type="submit" variant="danger" loading={form.saving}>Confirm cancellation</Button><Button variant="secondary" disabled={form.saving} onClick={onClose}>Keep session</Button></div>
    </form>
  </Modal>
}
