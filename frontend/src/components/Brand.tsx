import { Link } from 'react-router-dom'
import '@fontsource/be-vietnam-pro/800.css'
import '@fontsource/be-vietnam-pro/800-italic.css'

export interface BrandProps {
  to?: string
  /** Hide the "Sports center" line, for narrow headers. */
  compact?: boolean
}

export function Brand({ to = '/', compact = false }: BrandProps) {
  return (
    <Link to={to} className="brand" aria-label="Sportify Center home">
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
