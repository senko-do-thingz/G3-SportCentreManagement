import { useCallback, useRef, useState } from 'react'
import { managerUsersApi } from '../../api/managerUsersApi'
import { packagesApi } from '../../api/packagesApi'
import { PasswordRules } from '../../components/PasswordRules'
import { Button, Checkbox, Input, Modal, PasswordInput, Select, Spinner } from '../../components/ui'
import type { StaffAccountRequest, StaffRole } from '../../types/manager'
import { ManagerError } from './ManagerShared'
import { validateStaff } from './managerValidation'
import { useManagerForm, useResource } from './useManagerTools'

const FIELDS = ['fullName', 'email', 'phone', 'temporaryPassword', 'role', 'sportIds'] as const
const INITIAL: StaffAccountRequest = { fullName: '', email: '', phone: '', temporaryPassword: '', role: 'RECEPTIONIST', sportIds: [] }

export function AddStaffAccountModal({ onClose, onSuccess }: { onClose: () => void; onSuccess: () => void }) {
  const [draft, setDraft] = useState<StaffAccountRequest>(INITIAL)
  const formRef = useRef<HTMLFormElement>(null)
  const loadSports = useCallback((signal: AbortSignal) => draft.role === 'COACH' ? packagesApi.sports({ signal }) : Promise.resolve([]), [draft.role])
  const [sports, retrySports] = useResource(loadSports)
  const form = useManagerForm(formRef, FIELDS, () => validateStaff(draft), () => managerUsersApi.create({ ...draft, fullName: draft.fullName.trim(), email: draft.email.trim() }), onSuccess)
  function change<K extends keyof StaffAccountRequest>(key: K, value: StaffAccountRequest[K]) {
    setDraft((current) => ({ ...current, [key]: value }))
    form.clear(key)
  }
  const sportsUnavailable = draft.role === 'COACH' && sports.status !== 'ready'
  return <Modal open title="Add staff account" description="Fixed role access. No custom permission selection." onClose={onClose} dismissible={!form.saving}>
    <form ref={formRef} className="manager-form" onSubmit={form.submit} noValidate aria-busy={form.saving}>
      {form.error && <p className="notice notice--danger" role="alert">{form.error}</p>}
      <fieldset className="manager-form-grid" disabled={form.saving}>
        <Input label="Full name" name="fullName" value={draft.fullName} onChange={(event) => change('fullName', event.target.value)} error={form.errors.fullName} maxLength={100} required autoComplete="name" />
        <Input label="Email address" name="email" type="email" value={draft.email} onChange={(event) => change('email', event.target.value)} error={form.errors.email} maxLength={255} required autoComplete="email" />
        <Input label="Phone number" name="phone" type="tel" value={draft.phone} onChange={(event) => change('phone', event.target.value)} error={form.errors.phone} maxLength={20} required autoComplete="tel" />
        <Select label="Role" name="role" value={draft.role} onChange={(event) => { change('role', event.target.value as StaffRole); change('sportIds', []) }} error={form.errors.role} options={[{ value: 'RECEPTIONIST', label: 'Receptionist' }, { value: 'COACH', label: 'Coach' }, { value: 'MANAGER', label: 'Manager' }]} required />
        <div className="manager-form-full">
          <PasswordInput label="Temporary password" name="temporaryPassword" autoComplete="new-password" value={draft.temporaryPassword} onChange={(event) => change('temporaryPassword', event.target.value)} error={form.errors.temporaryPassword} maxLength={64} required />
          <PasswordRules id="staff-password-rules" password={draft.temporaryPassword} />
        </div>
        {draft.role === 'COACH' && <div className="manager-form-full">
          <fieldset className="manager-sports" aria-describedby="coach-sports-help" aria-invalid={Boolean(form.errors.sportIds)}>
            <legend>Qualified sports</legend>
            <p id="coach-sports-help" className="manager-muted">The first sport you select becomes the primary sport.</p>
            {sports.status === 'loading' && <Spinner label="Loading sports" />}
            {sports.status === 'error' && <ManagerError error={sports.error} onRetry={retrySports} />}
            {sports.status === 'ready' && <div className="manager-sport-options">{sports.data.map((sport) => <Checkbox key={sport.id} label={<>{sport.name}{draft.sportIds[0] === sport.id && <span className="manager-primary-sport">Primary</span>}</>} checked={draft.sportIds.includes(sport.id)} onChange={(event) => change('sportIds', event.target.checked ? [...draft.sportIds, sport.id] : draft.sportIds.filter((id) => id !== sport.id))} />)}</div>}
            {sports.status === 'ready' && !sports.data.length && <p className="notice notice--warning">No sports are available. A coach cannot be created until sports are configured.</p>}
            {form.errors.sportIds && <p className="field__error" role="alert">{form.errors.sportIds}</p>}
          </fieldset>
        </div>}
      </fieldset>
      <p className="manager-muted">The new staff account starts active. Share its temporary password privately.</p>
      <div className="manager-form-actions"><Button type="submit" loading={form.saving} disabled={sportsUnavailable}>Save account</Button><Button variant="secondary" disabled={form.saving} onClick={onClose}>Cancel</Button></div>
    </form>
  </Modal>
}
