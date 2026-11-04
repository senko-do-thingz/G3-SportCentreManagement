import type { ReactNode } from 'react'
import { ApiError, getErrorMessage } from '../../api/errors'
import { Button } from './Button'

export interface ErrorStateProps {
  title?: ReactNode
  /** Any thrown value; ApiError messages from the backend are shown as is. */
  error?: unknown
  message?: ReactNode
  onRetry?: () => void
  retryLabel?: string
}

export function ErrorState({
  title = 'Something went wrong',
  error,
  message,
  onRetry,
  retryLabel = 'Try again',
}: ErrorStateProps) {
  const text = message ?? (error !== undefined ? getErrorMessage(error) : 'Please try again in a moment.')
  const conflict = error instanceof ApiError && error.status === 409
  return (
    <div className={`state state--error${conflict ? ' state--conflict' : ''}`} role="alert">
      <div className="state__icon state__icon--error" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="22" height="22">
          <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="1.8" />
          <path d="M12 7.5v5.5M12 16.2v.3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      </div>
      <h3 className="state__title">{conflict && 'Conflict: '}{title}</h3>
      <p className="state__description">{text}</p>
      {conflict && <p className="state__recovery">Review the latest information before retrying.</p>}
      {onRetry && (
        <div className="state__action">
          <Button variant="secondary" onClick={onRetry}>
            {retryLabel}
          </Button>
        </div>
      )}
    </div>
  )
}
