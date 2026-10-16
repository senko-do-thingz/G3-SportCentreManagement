import { useCallback, useRef, useState } from 'react'
import type { Facility } from '../../api/facilitiesApi'
import { managerClassesApi, type ManagerClass } from '../../api/managerClassesApi'
import { managerSessionsApi, scheduleConflicts, type DayOfWeek, type ManagerSession, type ScheduleConflict, type SessionCreate, type TrainingType } from '../../api/managerSessionsApi'
import { Button, Checkbox, Input, Modal, Select } from '../../components/ui'
import { ManagerError } from './ManagerShared'
import { addDays, centerNow, displayDate, repeatDates, validDate, WEEKDAYS } from './scheduleModel'
import { useManagerForm, useResource } from './useManagerTools'

const FIELDS = ['classId', 'date', 'startTime', 'endTime', 'facilityId', 'coachId', 'capacity', 'trainingType', 'daysOfWeek', 'fromDate', 'toDate'] as const
export function SessionEditorModal({ item, classes, facilities, classId, onClose, onSuccess }: { item?: ManagerSession; classes: ManagerClass[]; facilities: Facility[]; classId?: number; onClose: () => void; onSuccess: () => void }) {
  const ref = useRef<HTMLFormElement>(null)
  const today = centerNow().date
  const initialClass = classes.find((entry) => entry.id === (item?.class?.id ?? classId))
  const [draft, setDraft] = useState({ classId: String(initialClass?.id ?? ''), date: item?.date ?? addDays(today, 1), startTime: item?.startTime.slice(0, 5) ?? '09:00', endTime: item?.endTime.slice(0, 5) ?? '10:00', facilityId: String(item?.facility?.id ?? ''), coachId: String(item?.coach?.id ?? initialClass?.coachId ?? ''), capacity: String(item?.capacity ?? initialClass?.maxMembers ?? 20), trainingType: item?.trainingType ?? 'COACH_LED', fromDate: addDays(today, 1), toDate: addDays(today, 7) })
  const [weekly, setWeekly] = useState(false)
  const [days, setDays] = useState<DayOfWeek[]>([])
  const [conflicts, setConflicts] = useState<ScheduleConflict[]>([])
  const selectedClass = classes.find((entry) => entry.id === Number(draft.classId))
  const coachLoader = useCallback((signal: AbortSignal) => selectedClass ? managerClassesApi.coaches(selectedClass.sportId, signal) : Promise.resolve([]), [selectedClass])
  const [coaches, retryCoaches] = useResource(coachLoader)
  const dates = weekly ? repeatDates(draft.fromDate, draft.toDate, days) : [draft.date]
  const form = useManagerForm(ref, FIELDS, () => {
    const errors: Record<string, string> = {}
    if (!selectedClass || (!item && !selectedClass.active)) errors.classId = 'Choose an active class.'
    const now = centerNow()
    if (weekly) {
      if (!validDate(draft.fromDate) || draft.fromDate < now.date) errors.fromDate = 'Choose today or a future start date.'
      if (!validDate(draft.toDate) || draft.toDate < draft.fromDate) errors.toDate = 'End date must be on or after the start date.'
      if (!days.length || !dates.length) errors.daysOfWeek = 'Choose weekdays that produce at least one session.'
      else if (dates.length > 100) errors.daysOfWeek = 'A weekly batch can contain at most 100 sessions.'
    } else if (!validDate(draft.date) || draft.date < now.date) errors.date = 'Choose today or a future date.'
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(draft.startTime)) errors.startTime = 'Choose a valid start time.'
    else if (dates.includes(now.date) && `${draft.startTime}:00` <= now.time) errors.startTime = 'Session must start in the future (Vietnam time).'
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(draft.endTime) || draft.endTime <= draft.startTime) errors.endTime = 'End time must be after the start time on the same day.'
    if (!facilities.some((entry) => entry.id === Number(draft.facilityId) && entry.active !== false)) errors.facilityId = 'Choose an available facility.'
    if (draft.trainingType === 'COACH_LED' && (coaches.status !== 'ready' || !coaches.data.some((entry) => entry.id === Number(draft.coachId)))) errors.coachId = 'Choose a coach qualified for this sport.'
    const capacity = Number(draft.capacity)
    if (!Number.isInteger(capacity) || capacity < Math.max(1, item?.bookedCount ?? 0) || capacity > (selectedClass?.maxMembers ?? 100)) errors.capacity = `Capacity must be a whole number from ${Math.max(1, item?.bookedCount ?? 0)} to ${selectedClass?.maxMembers ?? 100}.`
    return errors
  }, async () => {
    setConflicts([])
    const values = { startTime: `${draft.startTime}:00`, endTime: `${draft.endTime}:00`, facilityId: Number(draft.facilityId), coachId: draft.trainingType === 'COACH_LED' ? Number(draft.coachId) : null, capacity: Number(draft.capacity) }
    try {
      if (item) return await managerSessionsApi.update(item.id, { ...values, date: draft.date })
      const body: SessionCreate = { ...values, trainingType: draft.trainingType as TrainingType, ...(weekly ? { daysOfWeek: days, fromDate: draft.fromDate, toDate: draft.toDate } : { date: draft.date }) }
      return await managerSessionsApi.create(Number(draft.classId), body)
    } catch (error) { setConflicts(scheduleConflicts(error)); throw error }
  }, onSuccess)
  function change(key: keyof typeof draft, value: string) {
    setDraft((previous) => {
      const nextClass = key === 'classId' ? classes.find((entry) => entry.id === Number(value)) : undefined
      return { ...previous, [key]: value, ...(key === 'classId' ? { coachId: String(nextClass?.coachId ?? ''), capacity: String(nextClass?.maxMembers ?? 20) } : {}) }
    })
    form.clear(key)
  }
  function editConflict() { setConflicts([]); requestAnimationFrame(() => ref.current?.querySelector<HTMLInputElement>('[name="startTime"]')?.focus()) }
  return <>
    <Modal open size="lg" title={item ? 'Edit session' : 'Create session'} description="All times are Vietnam time (UTC+7). A weekly batch is saved only if every session is valid." onClose={onClose} dismissible={!form.saving}>
      <form ref={ref} onSubmit={form.submit} noValidate className="manager-form">
        {form.error && !conflicts.length && <p role="alert" className="notice notice--danger">{form.error}</p>}
        {item && <p className="notice notice--info">Editing does not notify booked members. Class and training type cannot be changed.</p>}
        <fieldset disabled={form.saving} className="manager-form-grid">
          <Select label="Class" name="classId" placeholder="Choose a class" disabled={Boolean(item)} value={draft.classId} options={classes.filter((entry) => entry.active || entry.id === item?.class?.id).map((entry) => ({ value: String(entry.id), label: entry.name }))} onChange={(event) => change('classId', event.target.value)} error={form.errors.classId} required />
          <Select label="Training type" name="trainingType" disabled={Boolean(item)} value={draft.trainingType} options={[{ value: 'COACH_LED', label: 'Coach-led' }, { value: 'SELF_TRAINING', label: 'Self-training' }]} onChange={(event) => change('trainingType', event.target.value)} error={form.errors.trainingType} required />
          {!weekly && <Input label="Date" name="date" type="date" min={today} value={draft.date} onChange={(event) => change('date', event.target.value)} error={form.errors.date} required />}
          <Input label="Start time" name="startTime" type="time" value={draft.startTime} onChange={(event) => change('startTime', event.target.value)} error={form.errors.startTime} required />
          <Input label="End time" name="endTime" type="time" value={draft.endTime} onChange={(event) => change('endTime', event.target.value)} error={form.errors.endTime} required />
          <Select label="Facility" name="facilityId" placeholder="Choose a facility" value={draft.facilityId} options={facilities.filter((entry) => entry.active !== false).map((entry) => ({ value: String(entry.id), label: entry.name }))} onChange={(event) => change('facilityId', event.target.value)} error={form.errors.facilityId} required />
          <Select label="Coach" name="coachId" placeholder="Choose a qualified coach" disabled={draft.trainingType !== 'COACH_LED' || coaches.status !== 'ready'} value={draft.trainingType === 'COACH_LED' ? draft.coachId : ''} options={coaches.status === 'ready' ? coaches.data.map((entry) => ({ value: String(entry.id), label: entry.fullName })) : []} onChange={(event) => change('coachId', event.target.value)} error={form.errors.coachId} required={draft.trainingType === 'COACH_LED'} />
          <Input label="Capacity" name="capacity" type="number" min={Math.max(1, item?.bookedCount ?? 0)} max={selectedClass?.maxMembers} step="1" value={draft.capacity} onChange={(event) => change('capacity', event.target.value)} error={form.errors.capacity} required />
          {!item && <Checkbox label="Repeat weekly" checked={weekly} onChange={(event) => setWeekly(event.target.checked)} />}
          {weekly && <><Input label="From date" name="fromDate" type="date" min={today} value={draft.fromDate} onChange={(event) => change('fromDate', event.target.value)} error={form.errors.fromDate} required /><Input label="To date" name="toDate" type="date" min={draft.fromDate} value={draft.toDate} onChange={(event) => change('toDate', event.target.value)} error={form.errors.toDate} required /><div className="manager-form-full"><fieldset><legend>Repeat on</legend><div className="f2-weekdays">{WEEKDAYS.map((day) => <Checkbox key={day} label={day.slice(0, 3)} checked={days.includes(day)} onChange={(event) => { setDays((previous) => event.target.checked ? [...previous, day] : previous.filter((entry) => entry !== day)); form.clear('daysOfWeek') }} />)}</div></fieldset>{form.errors.daysOfWeek && <p role="alert" className="field__error">{form.errors.daysOfWeek}</p>}<p>{dates.length > 100 ? 'More than 100 sessions - shorten the date range.' : `${dates.length} sessions will be created.`}</p></div></>}
        </fieldset>
        {coaches.status === 'error' && draft.trainingType === 'COACH_LED' && <ManagerError error={coaches.error} onRetry={retryCoaches} />}
        <p className="notice notice--info">{selectedClass?.name ?? 'Choose a class'} - Maximum {selectedClass?.maxMembers ?? '-'} members. Sessions are published when created.</p>
        <div className="manager-form-actions"><Button type="submit" loading={form.saving}>{item ? 'Save session' : 'Create schedule'}</Button><Button variant="secondary" disabled={form.saving} onClick={onClose}>Close</Button></div>
      </form>
    </Modal>
    {!!conflicts.length && <Modal open title="Schedule Conflict" description="Nothing was saved. Change the time, coach or facility and try again." onClose={editConflict} footer={<Button onClick={editConflict}>Edit session</Button>}>
      <p role="alert">{conflicts.length} conflicting sessions</p>
      {[...conflicts].sort((a, b) => a.date.localeCompare(b.date) || a.startTime.localeCompare(b.startTime)).map((conflict, index) => <div className="f2-conflict" key={`${conflict.sessionId}-${index}`}><strong>{displayDate(conflict.date)} {conflict.startTime.slice(0, 5)} - {conflict.endTime.slice(0, 5)}</strong><p>Session #{conflict.sessionId}: {conflict.class?.name ?? 'Unassigned class'}</p><p>Coach: {conflict.coach?.name ?? 'Not assigned'} - Facility: {conflict.facility?.name ?? 'Not assigned'}</p><p>Conflict: {conflict.conflictType === 'BOTH' ? 'Coach and facility' : conflict.conflictType === 'COACH' ? 'Coach' : 'Facility'}</p></div>)}
    </Modal>}
  </>
}
