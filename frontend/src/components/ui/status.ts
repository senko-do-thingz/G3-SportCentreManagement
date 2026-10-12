export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

/**
 * Tone for each status code used by the backend (user, card, package, registration, booking,
 * payment, invoice and refund statuses). Unknown codes fall back to neutral.
 */
const STATUS_TONES: Record<string, BadgeTone> = {
  ACTIVE: 'success',
  CONFIRMED: 'success',
  PAID: 'success',
  APPROVED: 'success',
  COMPLETED: 'success',
  CHECKED_IN: 'success',
  ATTENDED: 'success',
  OPEN: 'info',
  SCHEDULED: 'info',
  REFUNDED: 'info',
  PENDING: 'warning',
  PENDING_PAYMENT: 'warning',
  PENDING_VERIFICATION: 'warning',
  NEEDS_REVIEW: 'warning',
  WAITLISTED: 'warning',
  FULL: 'warning',
  INACTIVE: 'neutral',
  DRAFT: 'neutral',
  CLOSED: 'neutral',
  EXPIRED: 'danger',
  CANCELLED: 'danger',
  REJECTED: 'danger',
  FAILED: 'danger',
  LOCKED: 'danger',
  SUSPENDED: 'danger',
  NO_SHOW: 'danger',
}

export function statusTone(status: string | null | undefined): BadgeTone {
  if (!status) {
    return 'neutral'
  }
  return STATUS_TONES[status.toUpperCase()] ?? 'neutral'
}
