import type { ReactNode } from 'react'

export interface PaymentHeroProps {
  /** Small gold line above the title, for example "Member / Payments". */
  eyebrow: string
  title: string
  description?: ReactNode
  /** Right side of the hero: a reference, a status or an amount. */
  aside?: ReactNode
}

/**
 * Page heading of the payment and refund screens (UI-27 to UI-32) in the Home theme: navy feature surface,
 * gold eyebrow and uppercase title, as on Find Classes. Colors come only from the shared tokens.
 * A plain header, not a named region, so it never competes with the page's own regions (a receipt card is named
 * like the page title).
 */
export function PaymentHero({ eyebrow, title, description, aside }: PaymentHeroProps) {
  return (
    <header className="payment-hero">
      <div className="payment-hero__copy">
        <p className="payment-hero__eyebrow">{eyebrow}</p>
        <h1 className="payment-hero__title">{title}</h1>
        {description && <p className="payment-hero__description">{description}</p>}
      </div>
      {aside && <div className="payment-hero__aside">{aside}</div>}
    </header>
  )
}
