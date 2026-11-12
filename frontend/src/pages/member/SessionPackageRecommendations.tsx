import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import type { BrowseSession } from '../../api/memberSessionsApi'
import { packagesApi } from '../../api/packagesApi'
import { sessionRouteReference } from '../../api/sessionIdentity'
import { Button, ErrorState, PageLoader, buttonClassName } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import { formatMoney } from '../../utils/format'
import { bookingDate } from './bookingPresentation'
import { durationLabel, sessionsLabel } from './packagesModel'
import { trainingFormatLabel } from './dashboardModel'

export function SessionPackageRecommendations({ session, memberId, returnTo }: {
  session: BrowseSession
  memberId?: number
  returnTo?: string
}) {
  const load = useCallback((signal: AbortSignal) => packagesApi.packages({ signal }), [])
  const [catalog, reload] = useResource(load)
  const sessionReference = sessionRouteReference(session)

  return <div className="booking-recommendations">
    <h3>Selected Session</h3>
    <p>{session.sportName} — {trainingFormatLabel(session.trainingFormat)} — {bookingDate(session.sessionDate)}</p>
    <h3>Recommended Sport Packages</h3>
    {catalog.status === 'loading' ? <PageLoader label="Loading matching sport packages" />
      : catalog.status === 'error' ? <ErrorState title="Packages could not be loaded" error={catalog.error} onRetry={reload} />
      : (() => {
        const matches = catalog.data.filter((pkg) => pkg.isActive === true
          && pkg.sportId === session.sportId && pkg.trainingFormat === session.trainingFormat)
        if (matches.length === 0) return <p className="notice notice--info">No matching sport package is currently available. Please contact Receptionist for assistance.{memberId ? ' Package availability must be checked with Manager.' : ''}</p>
        return <><p className="browse-muted">Choose a start date that covers the selected session. Purchase does not reserve a seat; eligibility is checked again before booking.</p>
          <div className="booking-package-options">{matches.map((pkg) => {
            const destination = memberId
              ? `/reception/register?${new URLSearchParams({ tab: 'package', memberId: String(memberId), packageId: String(pkg.id), sessionId: sessionReference, week: session.sessionDate })}`
              : `/member/packages/${pkg.id}?${new URLSearchParams({ sport: String(session.sportId), format: session.trainingFormat, sessionId: sessionReference, sessionDate: session.sessionDate, ...(returnTo ? { returnTo } : {}) })}`
            return <div className="booking-package" key={pkg.id}><span className="booking-package__body">
              <strong>{pkg.name}</strong><span>{pkg.sportName} — {trainingFormatLabel(pkg.trainingFormat)}</span>
              <span>{sessionsLabel(pkg.sessionCount)} · {durationLabel(pkg.durationDays)}</span>
              <span>Catalog price {formatMoney(pkg.priceAmount)}</span>
              <Link className={buttonClassName('secondary', 'md')} to={destination}>{memberId ? 'Register this package' : 'View matching package'}</Link>
            </span></div>
          })}</div></>
      })()}
    <div className="browse-actions"><Button variant="ghost" onClick={reload}>Refresh packages</Button></div>
  </div>
}
