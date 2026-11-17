import { useRef, useState, type ReactNode } from 'react'
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

export interface DeleteReasonModalProps {
  title: string
  entityName?: string
  description?: ReactNode
  confirmLabel?: string
  onClose: () => void
  onConfirm: (reason: string) => Promise<unknown>
  onSuccess: () => void
}

export function DeleteReasonModal({
  title,
  entityName,
  description,
  confirmLabel = 'Delete permanently',
  onClose,
  onConfirm,
  onSuccess,
}: DeleteReasonModalProps) {
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const trimmed = reason.trim()
  const validationError = touched && !trimmed ? 'Reason for deletion is required.' : trimmed.length > 255 ? 'Reason must be at most 255 characters.' : null

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setTouched(true)
    if (!trimmed) {
      setError('Please enter a non-blank reason for deletion.')
      return
    }
    if (trimmed.length > 255) {
      setError('Reason must not exceed 255 characters.')
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await onConfirm(trimmed)
      onSuccess()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Deletion failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open title={title} onClose={onClose} dismissible={!submitting} size="sm">
      <form onSubmit={handleSubmit} noValidate>
        {description ? <div className="mb-4 text-sm text-secondary">{description}</div> : null}
        <div className="notice notice--warning mb-4" role="status">
          {entityName ? (
            <p>
              You are about to delete <strong>{entityName}</strong>. This action requires a mandatory reason and is permanently recorded in the audit log.
            </p>
          ) : (
            <p>
              This action requires a mandatory reason and is permanently recorded in the audit log.
            </p>
          )}
        </div>
        <div className="field mb-4">
          <label htmlFor="deletion-reason" className="field__label">
            Reason for deletion <span className="field__required" aria-hidden="true">*</span>
          </label>
          <textarea
            id="deletion-reason"
            rows={3}
            className={`input textarea ${validationError ? 'input--invalid' : ''}`}
            placeholder="Enter the justification for this deletion..."
            value={reason}
            maxLength={255}
            disabled={submitting}
            onChange={(e) => {
              setReason(e.target.value)
              if (error) setError(null)
            }}
            onBlur={() => setTouched(true)}
            required
            autoFocus
          />
          <div className="flex justify-between items-center mt-1 text-xs text-secondary">
            <span>{validationError ? <span className="text-danger">{validationError}</span> : 'Mandatory for audit tracking'}</span>
            <span>{reason.length}/255</span>
          </div>
        </div>

        {error && <p className="notice notice--danger mb-4" role="alert">{error}</p>}

        <div className="manager-form-actions">
          <Button type="submit" variant="danger" loading={submitting} disabled={submitting || (touched && !trimmed)}>
            {confirmLabel}
          </Button>
          <Button type="button" variant="secondary" disabled={submitting} onClick={onClose}>
            Cancel
          </Button>
        </div>
      </form>
    </Modal>
  )
}
