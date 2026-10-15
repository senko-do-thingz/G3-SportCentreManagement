import { useRef, useState } from 'react'
import { managerPackagesApi } from '../../api/managerPackagesApi'
import type { Sport } from '../../api/packagesApi'
import { Button, Input, Modal, Select } from '../../components/ui'
import type { ManagerPackage, TrainingFormat } from '../../types/manager'
import { numericError, positivePriceError } from './managerValidation'
import { useManagerForm } from './useManagerTools'

const FIELDS = ['code', 'name', 'sportId', 'trainingFormat', 'durationDays', 'sessionCount', 'sessionMinutes', 'priceAmount', 'price', 'description', 'isActive', 'active'] as const

export function PackageEditorModal({ item, sports, onClose, onSuccess }: { item?: ManagerPackage; sports: Sport[]; onClose: () => void; onSuccess: () => void }) {
  const formRef = useRef<HTMLFormElement>(null)
  const [draft, setDraft] = useState(() => ({
    code: item?.code ?? '', name: item?.name ?? '', sportId: item ? String(item.sportId) : '',
    trainingFormat: item?.trainingFormat ?? 'SELF_TRAINING', durationDays: String(item?.durationDays ?? 30),
    sessionCount: String(item?.sessionCount ?? 8), sessionMinutes: String(item?.sessionMinutes ?? 60),
    priceAmount: item ? String(item.priceAmount) : '', description: item?.description ?? '', isActive: item?.isActive !== false,
  }))
  const form = useManagerForm(formRef, FIELDS, () => {
    const errors: Record<string, string> = {}
    if (!draft.name.trim()) errors.name = 'Package name is required.'
    else if (draft.name.trim().length > 100) errors.name = 'Package name must be at most 100 characters.'
    if (!item) {
      if (!draft.code.trim()) errors.code = 'Package code is required.'
      if (!sports.some((sport) => sport.id === Number(draft.sportId))) errors.sportId = 'Choose an available sport.'
      if (!['SELF_TRAINING', 'COACH_LED'].includes(draft.trainingFormat)) errors.trainingFormat = 'Choose a training type.'
    }
    const duration = numericError(draft.durationDays, 'Duration', 1, true, 3650)
    const sessions = numericError(draft.sessionCount, 'Session count', 1, true, 1000)
    const price = positivePriceError(draft.priceAmount)
    if (duration) errors.durationDays = duration
    if (sessions) errors.sessionCount = sessions
    if (price) errors.priceAmount = price
    if (!['60', '90'].includes(draft.sessionMinutes)) errors.sessionMinutes = 'Choose 60 or 90 minutes.'
    return errors
  }, async () => {
    const values = { name: draft.name.trim(), durationDays: Number(draft.durationDays), sessionCount: Number(draft.sessionCount), sessionMinutes: Number(draft.sessionMinutes), priceAmount: Number(draft.priceAmount) }
    if (item) return managerPackagesApi.update(item.id, { name: values.name, durationDays: values.durationDays, sessionCount: values.sessionCount, sessionMinutes: values.sessionMinutes, price: values.priceAmount, active: draft.isActive })
    return managerPackagesApi.create({ ...values, code: draft.code.trim(), sportId: Number(draft.sportId), trainingFormat: draft.trainingFormat as TrainingFormat, ...(draft.description.trim() ? { description: draft.description.trim() } : {}) })
  }, onSuccess)
  function change<K extends keyof typeof draft>(key: K, value: typeof draft[K]) {
    setDraft((current) => ({ ...current, [key]: value }))
    form.clear(key)
    if (key === 'priceAmount') form.clear('price')
    if (key === 'isActive') form.clear('active')
  }
  return <Modal open title={item ? 'Edit package' : 'Create sport package'} description="One sport per package. Existing paid registrations are not changed." onClose={onClose} dismissible={!form.saving}>
    <form ref={formRef} className="manager-form" onSubmit={form.submit} noValidate aria-busy={form.saving}>
      {form.error && <p className="notice notice--danger" role="alert">{form.error}</p>}
      <fieldset className="manager-form-grid" disabled={form.saving}>
        <Input label="Package code" name="code" value={draft.code} readOnly={Boolean(item)} onChange={(event) => change('code', event.target.value)} error={form.errors.code} required />
        <Input label="Package name" name="name" value={draft.name} onChange={(event) => change('name', event.target.value)} error={form.errors.name} required />
        {item ? <Input label="Sport" value={item.sportName} readOnly /> : <Select label="Sport" name="sportId" placeholder="Choose a sport" value={draft.sportId} options={sports.map((sport) => ({ value: String(sport.id), label: sport.name }))} onChange={(event) => change('sportId', event.target.value)} error={form.errors.sportId} required />}
        {item ? <Input label="Training type" value={item.trainingFormat === 'COACH_LED' ? 'Coach-led' : 'Self-training'} readOnly /> : <Select label="Training type" name="trainingFormat" options={[{ value: 'SELF_TRAINING', label: 'Self-training' }, { value: 'COACH_LED', label: 'Coach-led' }]} value={draft.trainingFormat} onChange={(event) => change('trainingFormat', event.target.value)} error={form.errors.trainingFormat} required />}
        <Input label="Duration (days)" name="durationDays" type="number" min="1" step="1" value={draft.durationDays} onChange={(event) => change('durationDays', event.target.value)} error={form.errors.durationDays} hint="Use 1 day for a single-session visit." required />
        <Input label="Included sessions" name="sessionCount" type="number" min="1" step="1" value={draft.sessionCount} onChange={(event) => change('sessionCount', event.target.value)} error={form.errors.sessionCount} required />
        <Select label="Minutes per session" name="sessionMinutes" value={draft.sessionMinutes} options={[{ value: '60', label: '60 minutes' }, { value: '90', label: '90 minutes' }]} onChange={(event) => change('sessionMinutes', event.target.value)} error={form.errors.sessionMinutes} required />
        <Input label="Price (VND)" name="priceAmount" type="number" min="0.01" step="0.01" value={draft.priceAmount} onChange={(event) => change('priceAmount', event.target.value)} error={form.errors.priceAmount ?? form.errors.price} required />
        {item ? <Select label="Status" name="isActive" value={String(draft.isActive)} options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]} onChange={(event) => change('isActive', event.target.value === 'true')} error={form.errors.isActive ?? form.errors.active} /> : <Input className="manager-form-full" label="Description" name="description" value={draft.description} onChange={(event) => change('description', event.target.value)} error={form.errors.description} />}
      </fieldset>
      <div className="manager-form-actions"><Button type="submit" loading={form.saving}>Save package</Button><Button variant="secondary" onClick={onClose} disabled={form.saving}>Cancel</Button></div>
    </form>
  </Modal>
}
