import { type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/memberCommerce.css'

interface MemberCommerceHeaderProps {
  eyebrow?: string
  title: string
  highlight?: string
  description?: string
  actions?: ReactNode
}

export function MemberCommerceHeader({
  eyebrow = 'Member Workspace',
  title,
  highlight,
  description,
  actions,
}: MemberCommerceHeaderProps) {
  return (
    <header className="member-commerce__header">
      <div className="member-commerce__header-main">
        <div className="member-commerce__eyebrow">{eyebrow}</div>
        <h1 className="member-commerce__title">
          {title}
          {highlight && <span className="member-commerce__title-highlight"> {highlight}</span>}
        </h1>
        {description && <p className="member-commerce__subtitle">{description}</p>}
      </div>
      {actions && <div className="member-commerce__actions">{actions}</div>}
    </header>
  )
}

interface MemberCommerceHeroProps {
  kicker: string
  title: string
  highlight?: string
  description: string
  actionLabel?: string
  actionHref?: string
  onAction?: () => void
}

export function MemberCommerceHero({
  kicker,
  title,
  highlight,
  description,
  actionLabel,
  actionHref,
  onAction,
}: MemberCommerceHeroProps) {
  return (
    <div className="member-commerce-hero" role="region" aria-label={kicker}>
      <div className="member-commerce-hero__content">
        <p className="member-commerce-hero__kicker">{kicker}</p>
        <h2 className="member-commerce-hero__title">
          {title}
          {highlight && <span className="member-commerce-hero__highlight"> {highlight}</span>}
        </h2>
        <p className="member-commerce-hero__description">{description}</p>
      </div>
      {actionLabel && (
        <div className="member-commerce-hero__action">
          {actionHref ? (
            <Link className="member-commerce-hero__btn" to={actionHref}>
              {actionLabel}
            </Link>
          ) : (
            <button type="button" className="member-commerce-hero__btn" onClick={onAction}>
              {actionLabel}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

interface KpiItem {
  icon: string
  label: string
  value: string | number
  linkText?: string
  linkHref?: string
}

export function MemberCommerceKpiGrid({ items }: { items: KpiItem[] }) {
  return (
    <div className="member-commerce-kpi-grid" role="region" aria-label="Key account metrics">
      {items.map((item, idx) => (
        <div className="member-commerce-kpi" key={`${item.label}-${idx}`}>
          <div className="member-commerce-kpi__icon" aria-hidden="true">
            {item.icon}
          </div>
          <div className="member-commerce-kpi__body">
            <span className="member-commerce-kpi__label">{item.label}</span>
            <span className="member-commerce-kpi__value">{item.value}</span>
            {item.linkText && item.linkHref && (
              <Link className="member-commerce-kpi__link" to={item.linkHref}>
                {item.linkText} &gt;
              </Link>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}

interface MemberCommerceCardProps {
  title: string
  actionText?: string
  actionHref?: string
  onAction?: () => void
  children: ReactNode
  className?: string
}

export function MemberCommerceCard({
  title,
  actionText,
  actionHref,
  onAction,
  children,
  className = '',
}: MemberCommerceCardProps) {
  return (
    <section className={`member-commerce-card ${className}`.trim()} aria-label={title}>
      <div className="member-commerce-card__header">
        <h3 className="member-commerce-card__title">{title}</h3>
        {actionText &&
          (actionHref ? (
            <Link className="member-commerce-card__action" to={actionHref}>
              {actionText} &gt;
            </Link>
          ) : (
            <button
              type="button"
              className="member-commerce-card__action"
              onClick={onAction}
              style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
            >
              {actionText}
            </button>
          ))}
      </div>
      <div className="member-commerce-card__body">{children}</div>
    </section>
  )
}

interface QuickActionItem {
  icon: string
  title: string
  desc: string
  href: string
}

export function MemberCommerceQuickActions({ actions }: { actions: QuickActionItem[] }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
      {actions.map((act) => (
        <Link className="member-commerce-quick-action" to={act.href} key={act.href}>
          <div className="member-commerce-quick-action__icon" aria-hidden="true">
            {act.icon}
          </div>
          <div className="member-commerce-quick-action__body">
            <p className="member-commerce-quick-action__title">{act.title}</p>
            <p className="member-commerce-quick-action__desc">{act.desc}</p>
          </div>
          <span className="member-commerce-quick-action__chevron" aria-hidden="true">
            &gt;
          </span>
        </Link>
      ))}
    </div>
  )
}
