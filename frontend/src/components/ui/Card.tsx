import type { HTMLAttributes, ReactNode } from 'react'

export interface CardProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  title?: ReactNode
  actions?: ReactNode
  children?: ReactNode
}

/** White rounded panel used for every content block in the Figma screens. */
export function Card({ title, actions, children, className, ...rest }: CardProps) {
  return (
    <section className={['card', className ?? ''].filter(Boolean).join(' ')} {...rest}>
      {(title || actions) && (
        <div className="card__header">
          {title && <h2 className="card__title">{title}</h2>}
          {actions && <div className="card__actions">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  )
}

export interface StatCardProps {
  label: ReactNode
  value: ReactNode
  hint?: ReactNode
}

/** Small metric card, for example "Active packages 0" on the member dashboard. */
export function StatCard({ label, value, hint }: StatCardProps) {
  return (
    <div className="card stat-card">
      <p className="stat-card__label">{label}</p>
      <p className="stat-card__value">{value}</p>
      {hint && <p className="stat-card__hint">{hint}</p>}
    </div>
  )
}
