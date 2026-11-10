import '@fontsource/be-vietnam-pro/800.css'
import '../../styles/receptionConcept.css'
import type { ReactNode } from 'react'

/*
 * RUI-00: presentation helpers for the Receptionist Concept A pages. They only arrange markup; they never fetch,
 * compute or invent data. Every value passed to ReceptionStat must come from data the page already loaded.
 */

/** Page wrapper. Adds the .rcp scope so the Concept A styles apply to this page only. */
export function ReceptionScreen({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={['page', 'rcp', className ?? ''].filter(Boolean).join(' ')}>{children}</div>
}

export interface ReceptionHeroProps {
  kicker: ReactNode
  title: ReactNode
  highlight?: ReactNode
  description?: ReactNode
  actions?: ReactNode
}

/** Navy banner with static copy. Decorative context only; it must not carry live data. */
export function ReceptionHero({ kicker, title, highlight, description, actions }: ReceptionHeroProps) {
  return (
    <section className="rcp-hero" aria-label="Overview">
      <div>
        <p className="rcp-hero__kicker">{kicker}</p>
        <p className="rcp-hero__title">
          {title}
          {highlight && <span className="rcp-hero__highlight">{highlight}</span>}
        </p>
        {description && <p className="rcp-hero__text">{description}</p>}
      </div>
      {actions && <div className="rcp-hero__actions">{actions}</div>}
    </section>
  )
}

/** Row of summary metrics. */
export function ReceptionStats({ label, children }: { label: string; children: ReactNode }) {
  return <ul className="rcp-stats" aria-label={label}>{children}</ul>
}

export interface ReceptionStatProps {
  label: ReactNode
  value: ReactNode
  /** One or two characters shown in the square mark. Text only, no icons or emoji. */
  mark: string
  tone?: 'info' | 'warning' | 'success' | 'danger'
}

export function ReceptionStat({ label, value, mark, tone = 'info' }: ReceptionStatProps) {
  return (
    <li className={'rcp-stat' + (tone === 'info' ? '' : ` rcp-stat--${tone}`)}>
      <span className="rcp-stat__mark" aria-hidden="true">{mark}</span>
      <span>
        <span className="rcp-stat__value">{value}</span>
        <span className="rcp-stat__label">{label}</span>
      </span>
    </li>
  )
}

/** Static safety note with a colored rule. Use the shared Notice for live errors and results. */
export function ReceptionCallout({ tone = 'info', children }: { tone?: 'info' | 'warning' | 'danger'; children: ReactNode }) {
  return <p className={'rcp-callout' + (tone === 'info' ? '' : ` rcp-callout--${tone}`)}>{children}</p>
}
