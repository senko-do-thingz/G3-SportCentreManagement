import { useRef, useState } from 'react'
import { managerCardTiersApi } from '../../api/managerCardTiersApi'
import { Button, Input, Modal, Select, TextArea } from '../../components/ui'
import type { ManagerCardTier } from '../../types/manager'
import { numericError, positivePriceError } from './managerValidation'
import { useManagerForm } from './useManagerTools'

const FIELDS = ['price', 'durationMonths', 'discountPercentage', 'description', 'isActive', 'active'] as const

export function EditMembershipCardModal({ tier, onClose, onSuccess }: { tier: ManagerCardTier; onClose: () => void; onSuccess: () => void }) {
  const standard = tier.code === 'STANDARD'
  const formRef = useRef<HTMLFormElement>(null)
  const [draft, setDraft] = useState(() => ({
    price: String(tier.price),
    durationMonths: tier.durationMonths === null ? '' : String(tier.durationMonths),
    discountPercentage: tier.discountPercentage === null ? '' : String(tier.discountPercentage),
    description: tier.description ?? '',
    isActive: tier.isActive,
  }))
  const form = useManagerForm(formRef, FIELDS, () => {
    const errors: Record<string, string> = {}
    if (standard) return { price: 'Standard is a protected free account tier.' }
    const price = positivePriceError(draft.price)
    const duration = numericError(draft.durationMonths, 'Validity', 0, true, 120)
    const discount = numericError(draft.discountPercentage, 'Discount', 0, true)
    if (price) errors.price = price
    if (duration) errors.durationMonths = duration
    if (discount || Number(draft.discountPercentage) > 100) errors.discountPercentage = 'Discount must be a whole number from 0 to 100.'
    if (draft.description.trim().length > 500) errors.description = 'Description must be at most 500 characters.'
    if (draft.isActive === null) errors.isActive = 'Choose whether this tier is active.'
    return errors
  }, () => managerCardTiersApi.update(tier.id, { price: Number(draft.price), durationMonths: Number(draft.durationMonths), discountPercentage: Number(draft.discountPercentage), description: draft.description.trim(), active: draft.isActive === true }), onSuccess)

  function change<K extends keyof typeof draft>(key: K, value: typeof draft[K]) {
    setDraft((current) => ({ ...current, [key]: value }))
    form.clear(key)
    if (key === 'isActive') form.clear('active')
  }

  return <Modal open title="Edit card benefits" description="Set the tier fee, validity and discount for future card purchases." onClose={onClose} dismissible={!form.saving}>
    <form ref={formRef} className="manager-form manager-catalog-form" onSubmit={form.submit} noValidate aria-busy={form.saving}>
      {form.error && <p className="notice notice--danger" role="alert">{form.error}</p>}
      <p className="manager-catalog-form__note">Discounts apply to eligible duration packages. Existing cards keep the terms they were purchased with.</p>
      {tier.isActive === null && <p className="manager-catalog-form__note">Tier status is not provided. Choose a status before saving.</p>}
      <fieldset className="manager-form-grid" disabled={form.saving || standard}>
        <Input label="Tier name" value={tier.name} readOnly />
        <Input label="Card fee (VND)" name="price" type="number" min="0.01" step="0.01" value={draft.price} onChange={(event) => change('price', event.target.value)} error={form.errors.price} hint="The fee for a new card purchase." required />
        <Input label="Validity (months)" name="durationMonths" type="number" step="1" min="0" max="120" value={draft.durationMonths} onChange={(event) => change('durationMonths', event.target.value)} error={form.errors.durationMonths} hint="From 0 to 120 months." required />
        <Input label="Package discount (%)" name="discountPercentage" type="number" min="0" max="100" step="1" value={draft.discountPercentage} onChange={(event) => change('discountPercentage', event.target.value)} error={form.errors.discountPercentage} hint="Whole percentage from 0 to 100." required />
        <Select label="Status" name="isActive" placeholder="Choose status" value={draft.isActive === null ? '' : String(draft.isActive)} options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]} onChange={(event) => change('isActive', event.target.value === '' ? null : event.target.value === 'true')} error={form.errors.isActive ?? form.errors.active} />
        <TextArea className="manager-form-full" label="Description" name="description" maxLength={500} value={draft.description} onChange={(event) => change('description', event.target.value)} error={form.errors.description} hint={draft.description.length + ' / 500 characters'} rows={3} />
      </fieldset>
      <p className="manager-catalog-form__policy">Eligible services: 30-day and 90-day sport packages. Single-session visits are excluded; discounts do not stack.</p>
      <div className="manager-form-actions"><Button type="submit" loading={form.saving} disabled={standard}>Save benefits</Button><Button variant="secondary" onClick={onClose} disabled={form.saving}>Cancel</Button></div>
    </form>
  </Modal>
}
