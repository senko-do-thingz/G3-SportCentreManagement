import { useRef, useState } from 'react'
import { managerCardTiersApi } from '../../api/managerCardTiersApi'
import { Button, Input, Modal, Select } from '../../components/ui'
import type { ManagerCardTier } from '../../types/manager'
import { numericError, positivePriceError } from './managerValidation'
import { useManagerForm } from './useManagerTools'

const FIELDS = ['price', 'durationMonths', 'discountPercentage', 'description', 'isActive'] as const

export function EditMembershipCardModal({ tier, onClose, onSuccess }: { tier: ManagerCardTier; onClose: () => void; onSuccess: () => void }) {
  const standard = tier.code === 'STANDARD'
  const formRef = useRef<HTMLFormElement>(null)
  const [draft, setDraft] = useState(() => ({ price: String(tier.price), durationMonths: tier.durationMonths === null ? '' : String(tier.durationMonths), discountPercentage: String(tier.discountPercentage ?? 0), description: tier.description ?? '', isActive: tier.isActive !== false }))
  const form = useManagerForm(formRef, FIELDS, () => {
    const errors: Record<string, string> = {}
    if (standard) return { price: 'Standard is a protected free account tier.' }
    const price = positivePriceError(draft.price)
    const duration = numericError(draft.durationMonths, 'Validity', 1, true)
    const discount = numericError(draft.discountPercentage, 'Discount', 0)
    if (price) errors.price = price
    if (duration) errors.durationMonths = duration
    if (discount || Number(draft.discountPercentage) > 100) errors.discountPercentage = 'Discount must be a number from 0 to 100.'
    return errors
  }, () => managerCardTiersApi.update(tier.id, { price: Number(draft.price), durationMonths: Number(draft.durationMonths), discountPercentage: Number(draft.discountPercentage), description: draft.description.trim(), isActive: draft.isActive }), onSuccess)
  function change<K extends keyof typeof draft>(key: K, value: typeof draft[K]) { setDraft((current) => ({ ...current, [key]: value })); form.clear(key) }
  return <Modal open title="Edit card benefits" description="Discounts apply to eligible duration packages, not single visits." onClose={onClose} dismissible={!form.saving}>
    <form ref={formRef} className="manager-form" onSubmit={form.submit} noValidate aria-busy={form.saving}>
      {form.error && <p className="notice notice--danger" role="alert">{form.error}</p>}
      <fieldset className="manager-form-grid" disabled={form.saving || standard}>
        <Input label="Tier name" value={tier.name} readOnly />
        <Input label="Card fee (VND)" name="price" type="number" step="0.01" value={draft.price} onChange={(event) => change('price', event.target.value)} error={form.errors.price} required />
        <Input label="Validity (months)" name="durationMonths" type="number" step="1" min="1" value={draft.durationMonths} onChange={(event) => change('durationMonths', event.target.value)} error={form.errors.durationMonths} required />
        <Input label="Package discount (%)" name="discountPercentage" type="number" min="0" max="100" step="any" value={draft.discountPercentage} onChange={(event) => change('discountPercentage', event.target.value)} error={form.errors.discountPercentage} required />
        <Select label="Status" name="isActive" value={String(draft.isActive)} options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]} onChange={(event) => change('isActive', event.target.value === 'true')} error={form.errors.isActive} />
        <Input className="manager-form-full" label="Description" name="description" value={draft.description} onChange={(event) => change('description', event.target.value)} error={form.errors.description} />
      </fieldset>
      <p className="notice notice--info">Benefits apply to 30-day and 90-day self-training and coach-led packages. No discount stacking.</p>
      <div className="manager-form-actions"><Button type="submit" loading={form.saving} disabled={standard}>Save benefits</Button><Button variant="secondary" onClick={onClose} disabled={form.saving}>Cancel</Button></div>
    </form>
  </Modal>
}
