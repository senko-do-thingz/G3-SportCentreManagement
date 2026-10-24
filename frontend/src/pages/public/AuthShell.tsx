import type { ReactNode } from 'react'
import '@fontsource/be-vietnam-pro/800.css'
import '@fontsource/be-vietnam-pro/800-italic.css'
import { Link } from 'react-router-dom'
import authPhoto from '../../assets/auth/auth-dunk.jpg'
import { Brand } from '../../components/Brand'
import { Photo } from '../../media/Photo'
import { ThemeToggle } from '../../theme/ThemeToggle'
import '../../styles/auth.css'

export interface AuthShellProps {
  /** Screen modifier, for example auth-shell--login or auth-shell--register. */
  className?: string
  title: string
  subtitle: string
  children: ReactNode
}

function Slogan() {
  return (
    <section className="auth-stage__copy" aria-hidden="true">
      <p className="auth-stage__copy__eyebrow">One center. More possibilities.</p>
      <p className="auth-stage__copy__title">
        More sport.
        <br />
        <span className="auth-stage__copy__accent">More you.</span>
      </p>
      <p className="auth-stage__copy__text">Find your sport, train your way, and make every session count.</p>
    </section>
  )
}

/**
 * Log in and Sign up (layout B, UI-06 and UI-07): the dunk photo fills the page with the player on the left, a compact
 * Home header (brand, Back to Home, sun and moon switch) sits on top, the slogan sits low on the left under the player
 * and the form card floats on the dark right side. On narrow screens the photo becomes a band with the slogan above
 * the card.
 */
export function AuthShell({ className, title, subtitle, children }: AuthShellProps) {
  return (
    <div className={['auth-stage', className].filter(Boolean).join(' ')}>
      <Photo className="auth-stage__photo" src={authPhoto} fallback={authPhoto} width="1920" height="1280" fetchPriority="high" />
      <div className="auth-stage__header">
        <Brand />
        <div className="auth-stage__actions">
          <Link className="auth-stage__home" to="/">Back to Home</Link>
          <ThemeToggle className="auth-theme" />
        </div>
      </div>
      <div className="auth-stage__body">
        <Slogan />
        <section className="auth-panel">
          <div className="auth-panel__heading">
            <h1>{title}</h1>
            <p>{subtitle}</p>
          </div>
          {children}
        </section>
      </div>
    </div>
  )
}
