import type { ReactNode } from 'react'
import { humanizeCode } from '../../utils/format'
import { statusTone, type BadgeTone } from './status'

export interface BadgeProps {
  tone?: BadgeTone
  children: ReactNode
}

export function Badge({ tone = 'neutral', children }: BadgeProps) {
  return <span className={`badge badge--${tone}`}>{children}</span>
}

export interface StatusBadgeProps {
  status: string | null | undefined
  /** Overrides the label generated from the status code. */
  label?: ReactNode
}

/** Badge that picks its color and label from a backend status code such as PENDING_PAYMENT. */
export function StatusBadge({ status, label }: StatusBadgeProps) {
  return <Badge tone={statusTone(status)}>{label ?? (status ? humanizeCode(status) : 'Unknown')}</Badge>
}
