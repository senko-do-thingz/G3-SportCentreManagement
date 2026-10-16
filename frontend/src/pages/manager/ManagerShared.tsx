import { useRef, type ReactNode } from 'react'
import { ApiError } from '../../api/errors'
import { Button, ErrorState, Modal } from '../../components/ui'
import { isManagerPreviewSession } from '../../api/managerPreviewSession'
import { ForbiddenPage } from '../public/ForbiddenPage'
import { useManagerForm } from './useManagerTools'

export function PreviewNotice() {
  return isManagerPreviewSession() ? <p className="notice notice--warning manager-preview" role="status"><strong>Frontend preview</strong> Sample data only. Changes stay in this browser session and are never sent to the backend.</p> : null
}

export function ManagerError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (error instanceof ApiError && error.status === 403) return <ForbiddenPage />
  return <ErrorState error={error} onRetry={onRetry} />
}

export function DetailList({ items }: { items: { label: string; value: ReactNode }[] }) {
  return <dl className="manager-details">{items.map((item) => <div key={item.label}><dt>{item.label}</dt><dd>{item.value ?? '-'}</dd></div>)}</dl>
}

export function ConfirmationModal({ title, description, save, onClose, onSuccess, confirmLabel = 'Confirm', danger = false }: {
  title: string; description: ReactNode; save: () => Promise<unknown>; onClose: () => void; onSuccess: () => void; confirmLabel?: string; danger?: boolean
}) {
  const formRef = useRef<HTMLFormElement>(null)
  const form = useManagerForm(formRef, [], () => ({}), save, onSuccess)
  return <Modal open title={title} description={description} onClose={onClose} dismissible={!form.saving} size="sm">
    <form ref={formRef} onSubmit={form.submit} noValidate>
      {form.error && <p className="notice notice--danger" role="alert">{form.error}</p>}
      <div className="manager-form-actions">
        <Button type="submit" variant={danger ? 'danger' : 'primary'} loading={form.saving}>{confirmLabel}</Button>
        <Button variant="secondary" disabled={form.saving} onClick={onClose}>Cancel</Button>
      </div>
    </form>
  </Modal>
}
