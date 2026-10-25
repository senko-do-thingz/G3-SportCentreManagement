import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Brand } from '../../components/Brand'
import { Photo } from '../../media/Photo'
import { AppearanceControl } from '../../theme/AppearanceControl'
import { ThemeToggle } from '../../theme/ThemeToggle'
import { HOME_PHOTOS, homeHero } from './homeContent'

export interface AuthShellProps {
  className?: string
  title: string
  subtitle: string
  children: ReactNode
  /**
   * "split" (default, Sign up): fixed two column card with the photo hero on the left.
   * "stage" (Log in since UI-06): the Home hero photo fills the page, a compact Home header sits on top, the slogan
   * is on the left and the form card floats on the right; on narrow screens the photo becomes a band above the card.
   */
  layout?: 'split' | 'stage'
}

function Slogan({ block }: { block: string }) {
  return (
    <section className={block} aria-hidden="true">
      <p className={`${block}__eyebrow`}>One center. More possibilities.</p>
      <p className={`${block}__title`}>
        More sport.
        <br />
        <span className={`${block}__accent`}>More you.</span>
      </p>
      <p className={`${block}__text`}>Find your sport, train your way, and make every session count.</p>
    </section>
  )
}

function Heading({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div className="auth-panel__heading">
      <h1>{title}</h1>
      <p>{subtitle}</p>
    </div>
  )
}

export function AuthShell({ className, title, subtitle, children, layout = 'split' }: AuthShellProps) {
  if (layout === 'stage') {
    return (
      <div className={['auth-stage', className].filter(Boolean).join(' ')}>
        <Photo className="auth-stage__photo" src={HOME_PHOTOS.hero} fallback={homeHero} sizes="100vw" width="2200" height="1467" fetchPriority="high" />
        <div className="auth-stage__header">
          <Brand />
          <div className="auth-stage__actions">
            <Link className="auth-stage__home" to="/">Back to Home</Link>
            <ThemeToggle className="auth-theme" />
          </div>
        </div>
        <div className="auth-stage__body">
          <Slogan block="auth-stage__copy" />
          <section className="auth-panel">
            <Heading title={title} subtitle={subtitle} />
            {children}
          </section>
        </div>
      </div>
    )
  }
  return (
    <div className={['auth-shell', className].filter(Boolean).join(' ')}>
      <Slogan block="auth-hero" />
      <section className="auth-panel">
        <div className="auth-panel__toolbar"><Brand /><AppearanceControl /></div>
        <Heading title={title} subtitle={subtitle} />
        {children}
      </section>
    </div>
  )
}
