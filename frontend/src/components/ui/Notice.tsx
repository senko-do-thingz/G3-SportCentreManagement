import type { HTMLAttributes, ReactNode } from 'react'

export type NoticeTone = 'info' | 'success' | 'warning' | 'danger'

/** How a notice is announced: assertive interrupts (role alert), polite waits (role status), off is silent. */
export type NoticeLive = 'assertive' | 'polite' | 'off'

export interface NoticeProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title' | 'role'> {
  tone?: NoticeTone
  /** Short bold line above the message, for example "Payment pending". */
  title?: ReactNode
  children?: ReactNode
  /** Defaults to assertive for danger and off for other tones, matching how the screens use notices. */
  live?: NoticeLive
  /** Optional control shown after the message, for example a retry or a link. */
  action?: ReactNode
}

const ROLE_BY_LIVE: Record<NoticeLive, 'alert' | 'status' | undefined> = {
  assertive: 'alert',
  polite: 'status',
  off: undefined,
}

/**
 * Inline message banner that replaces hand written "notice notice--tone" paragraphs. Color is never
 * the only signal: a danger or warning notice also gets a visually hidden "Error:" or "Warning:"
 * prefix, so screen reader users hear the same severity that sighted users see.
 */
export function Notice({ tone = 'info', title, children, live, action, className, ...rest }: NoticeProps) {
  const resolvedLive = live ?? (tone === 'danger' ? 'assertive' : 'off')
  const prefix = tone === 'danger' ? 'Error: ' : tone === 'warning' ? 'Warning: ' : null
  return (
    <div
      {...rest}
      className={['notice', 'notice--stack', `notice--${tone}`, className ?? ''].filter(Boolean).join(' ')}
      role={ROLE_BY_LIVE[resolvedLive]}
    >
      <div className="notice__content">
        {title && (
          <p className="notice__title">
            {prefix && <span className="visually-hidden">{prefix}</span>}
            {title}
          </p>
        )}
        {children && (
          <div className="notice__message">
            {!title && prefix && <span className="visually-hidden">{prefix}</span>}
            {children}
          </div>
        )}
      </div>
      {action && <div className="notice__action">{action}</div>}
    </div>
  )
}
