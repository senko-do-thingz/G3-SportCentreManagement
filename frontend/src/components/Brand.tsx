import { Link } from 'react-router-dom'

export interface BrandProps {
  to?: string
  /** Hide the "Sports center" line, for narrow headers. */
  compact?: boolean
}

export function Brand({ to = '/', compact = false }: BrandProps) {
  return (
    <Link to={to} className="brand" aria-label="Sportify Sports center home">
      <span className="brand__mark" aria-hidden="true">
        S
      </span>
      <span className="brand__text">
        <span className="brand__name">Sportify</span>
        {!compact && <span className="brand__tagline">Sports center</span>}
      </span>
    </Link>
  )
}
