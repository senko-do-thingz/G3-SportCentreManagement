import type { ReactNode } from 'react'
import { Brand } from '../../components/Brand'

export interface StatusPageProps {
  code: string
  title: string
  description: ReactNode
  actions?: ReactNode
}

/** Shared full page layout for the 403 and 404 pages. */
export function StatusPage({ code, title, description, actions }: StatusPageProps) {
  return (
    <main className="status-page">
      <div className="status-page__card">
        <Brand />
        <p className="status-page__code">{code}</p>
        <h1 className="status-page__title">{title}</h1>
        <p className="status-page__description">{description}</p>
        {actions && <div className="status-page__actions">{actions}</div>}
      </div>
    </main>
  )
}
