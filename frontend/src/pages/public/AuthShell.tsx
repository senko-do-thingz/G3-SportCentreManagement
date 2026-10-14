import type { ReactNode } from 'react'
import { Brand } from '../../components/Brand'

export interface AuthShellProps {
  title: string
  subtitle: string
  children: ReactNode
}

/**
 * Two column card shared by Log in (F1-05) and Sign up (F1-06): the photo hero with the slogan on
 * the left, the brand, heading and form on the right. Below 960 px only the form column is shown.
 */
export function AuthShell({ title, subtitle, children }: AuthShellProps) {
  return (
    <div className="auth-shell">
      <section className="auth-hero" aria-hidden="true">
        <p className="auth-hero__eyebrow">One center. More possibilities.</p>
        <p className="auth-hero__title">
          More sport.
          <br />
          More you.
        </p>
        <p className="auth-hero__text">Find your sport, train your way, and make every session count.</p>
      </section>

      <section className="auth-panel">
        <Brand />
        <div className="auth-panel__heading">
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        {children}
      </section>
    </div>
  )
}
