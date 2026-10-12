import type { ReactNode } from 'react'

export interface EmptyStateProps {
  title: ReactNode
  description?: ReactNode
  action?: ReactNode
  icon?: ReactNode
  /** Dashed panel used inside cards in the Figma screens. */
  variant?: 'panel' | 'plain'
}

export function EmptyState({ title, description, action, icon, variant = 'panel' }: EmptyStateProps) {
  return (
    <div className={`state state--${variant}`}>
      {icon && <div className="state__icon">{icon}</div>}
      <h3 className="state__title">{title}</h3>
      {description && <p className="state__description">{description}</p>}
      {action && <div className="state__action">{action}</div>}
    </div>
  )
}
