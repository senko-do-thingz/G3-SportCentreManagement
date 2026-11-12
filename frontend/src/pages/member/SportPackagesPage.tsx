import { useState } from 'react'
import { flushSync } from 'react-dom'
import { Link, useSearchParams } from 'react-router-dom'
import { memberApi, type MemberCard } from '../../api/memberApi'
import { packagesApi, type Sport, type SportPackage } from '../../api/packagesApi'
import { Button, EmptyState, ErrorState, PageHeader } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Photo } from '../../media/Photo'
import { Spinner } from '../../components/ui/Spinner'
import { formatMoney } from '../../utils/format'
import { cardDiscountText, currentCard, MEMBER_PATHS, toIsoDate, trainingFormatLabel } from './dashboardModel'
import {
  filterPackages,
  filtersToParams,
  isSingleVisit,
  durationLabel,
  packageDetailPath,
  packageSummary,
  priceBreakdown,
  sessionsLabel,
} from './packagesModel'
import { sportPhotoFor } from './sportImages'
import { useMemberResource } from './useMemberResource'

interface Catalog {
  sports: Sport[]
  packages: SportPackage[]
}

const PACKAGE_FORMATS = [
  { value: 'COACH_LED', label: 'Coach-led' },
  { value: 'SELF_TRAINING', label: 'Self-training' },
] as const

type ViewTransitionDocument = Document & {
  startViewTransition?: (callback: () => void) => unknown
}

function transitionPackageView(update: () => void) {
  const viewTransitionDocument = document as ViewTransitionDocument
  const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

  if (!viewTransitionDocument.startViewTransition || prefersReducedMotion) {
    update()
    return
  }

  viewTransitionDocument.startViewTransition(() => flushSync(update))
}

function loadCatalog(signal: AbortSignal): Promise<Catalog> {
  return Promise.all([packagesApi.sports({ signal }), packagesApi.packages({ signal })]).then(([sports, packages]) => ({
    sports,
    packages,
  }))
}

/** The member's cards only decide the discount; a failure there must not hide the catalog. */
function loadMyCards(signal: AbortSignal): Promise<MemberCard[]> {
  return memberApi.myCards({ signal })
}

/**
 * Sport Packages (Figma F1-09). Data: GET /sports and GET /packages (public) for the catalog,
 * GET /membership-cards/my for the discount. The selected sport lives in the URL so package detail
 * can return to the same focused view; training formats and terms stay visible together.
 */
export function SportPackagesPage() {
  const [catalog, reloadCatalog] = useMemberResource(loadCatalog)
  const [cards] = useMemberResource(loadMyCards)
  const [searchParams, setSearchParams] = useSearchParams()
  const [today] = useState(() => toIsoDate(new Date()))
  const requestedSportId = searchParams.get('sport') ?? ''

  const header = (
    <PageHeader
      eyebrow="Sport packages / build your routine"
      title="Choose your sport. Find your pace."
      description="Pick a sport, choose how you want to train, and find the package that fits your week."
      actions={
        <Link className={buttonClassName('secondary', 'md')} to={MEMBER_PATHS.membership}>
          Membership cards
        </Link>
      }
    />
  )

  if (catalog.status === 'loading') {
    return (
      <div className="page member-ui member-ui--packages">
        {header}
        <Spinner label="Loading sport packages" />
      </div>
    )
  }
  if (catalog.status === 'error') {
    return (
      <div className="page member-ui member-ui--packages">
        {header}
        <ErrorState title="We could not load the sport packages" error={catalog.error} onRetry={reloadCatalog} />
      </div>
    )
  }

  const { sports, packages } = catalog.data
  const sportColumns = [...sports]
    .filter((sport) => packages.some((pkg) => pkg.sportId === sport.id && pkg.isActive !== false))
    .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0) || a.name.localeCompare(b.name))
  const selectedSport = sportColumns.find((sport) => String(sport.id) === requestedSportId)
  const filters = { sport: selectedSport ? requestedSportId : '', format: '', duration: '' }
  const withSessionContext = (params: URLSearchParams) => {
    for (const key of ['sessionId', 'sessionDate', 'returnTo']) {
      const value = searchParams.get(key)
      if (value) params.set(key, value)
    }
    return params
  }
  const toggleSport = (sportId: string) => {
    const nextSport = filters.sport === sportId ? '' : sportId
    transitionPackageView(() => setSearchParams(withSessionContext(filtersToParams({ ...filters, sport: nextSport })), { replace: true }))
  }
  const card = cards.status === 'ready' ? currentCard(cards.data, today) : null
  const shown = filterPackages(packages, filters)
  const query = withSessionContext(filtersToParams(filters)).toString()
  const search = query ? `?${query}` : ''
  const selectedSportPackages = selectedSport ? shown.filter((pkg) => pkg.sportId === selectedSport.id) : []
  const detailId = selectedSport ? `package-detail-${selectedSport.id}` : undefined

  return (
    <div className="page member-ui member-ui--packages">
      {header}

      {shown.length === 0 ? (
        <EmptyState
          title="No active sport packages are available"
          description="Please check back later or ask the reception team about upcoming packages."
          action={
            <Button variant="secondary" onClick={() => setSearchParams(new URLSearchParams(), { replace: true })}>
              Browse all sports
            </Button>
          }
        />
      ) : (
        <div className={`package-stage${selectedSport ? ' package-stage--selected' : ''}`}>
          {selectedSport ? (
            <>
              <SportCard
                sport={selectedSport}
                optionCount={packages.filter((pkg) => pkg.sportId === selectedSport.id && pkg.isActive !== false).length}
                selected
                detailId={detailId}
                onClick={() => toggleSport(String(selectedSport.id))}
              />
              <section
                className="package-detail"
                id={detailId}
                aria-labelledby={`package-detail-title-${selectedSport.id}`}
              >
                <Photo
                  {...sportPhotoFor(selectedSport.name, 1440)}
                  className="package-detail__image"
                  alt=""
                  aria-hidden="true"
                  loading="lazy"
                  sizes="(min-width: 1280px) 62vw, 100vw"
                />
                <div className="package-detail__scrim" aria-hidden="true" />
                <div className="package-detail__content">
                  <header className="package-detail__header">
                    <div>
                      <p className="package-detail__eyebrow">Your next move</p>
                      <h2 id={`package-detail-title-${selectedSport.id}`}>{selectedSport.name} packages</h2>
                      <p>Choose a training style to compare sessions and prices.</p>
                    </div>
                    <div className="package-detail__header-actions">
                      {card && card.discountPercentage != null && card.discountPercentage > 0 ? (
                        <p className="package-detail__benefit">
                        Your {card.tierName} card gives you {cardDiscountText(card.discountPercentage)}. Prices below include it.
                        </p>
                      ) : null}
                      {cards.status === 'error' ? (
                        <p className="package-detail__warning" role="alert">
                          Membership discount could not be loaded. Package prices are shown without a card discount.
                        </p>
                      ) : null}
                      <button className="package-detail__back" type="button" onClick={() => toggleSport(String(selectedSport.id))}>
                        All sports
                      </button>
                    </div>
                  </header>
                  <div className="package-sport__formats">
                    {PACKAGE_FORMATS.map((format) => {
                      const options = selectedSportPackages.filter((pkg) => pkg.trainingFormat === format.value)
                      const formatId = `package-format-${selectedSport.id}-${format.value.toLowerCase()}`

                      return (
                        <section className="package-format" aria-labelledby={formatId} key={format.value}>
                          <header className="package-format__header">
                            <h3 id={formatId}>{format.label}</h3>
                            <span className="package-format__count">
                              {options.length} {options.length === 1 ? 'plan' : 'plans'}
                            </span>
                          </header>
                          {options.length > 0 ? (
                            <ul className="package-format__list" aria-label={`${selectedSport.name} ${format.label} packages`}>
                              {options.map((pkg) => (
                                <PackageCard
                                  key={pkg.id}
                                  pkg={pkg}
                                  card={card}
                                  cardsLoading={cards.status === 'loading'}
                                  search={search}
                                />
                              ))}
                            </ul>
                          ) : (
                            <p className="package-format__empty">
                              No {format.label.toLowerCase()} plans are available for this sport.
                            </p>
                          )}
                        </section>
                      )
                    })}
                  </div>
                </div>
              </section>
              <div className="package-sport-rail" role="group" aria-label="Choose another sport">
                {sportColumns
                  .filter((sport) => sport.id !== selectedSport.id)
                  .map((sport) => (
                    <SportCard
                      key={sport.id}
                      sport={sport}
                      optionCount={packages.filter((pkg) => pkg.sportId === sport.id && pkg.isActive !== false).length}
                      compact
                      onClick={() => toggleSport(String(sport.id))}
                    />
                  ))}
              </div>
            </>
          ) : (
            <div className="package-sport-grid" role="group" aria-label="Sport packages">
              {sportColumns.map((sport) => (
                <SportCard
                  key={sport.id}
                  sport={sport}
                  optionCount={packages.filter((pkg) => pkg.sportId === sport.id && pkg.isActive !== false).length}
                  onClick={() => toggleSport(String(sport.id))}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function SportCard({
  sport,
  optionCount,
  selected = false,
  compact = false,
  detailId,
  onClick,
}: {
  sport: Sport
  optionCount: number
  selected?: boolean
  compact?: boolean
  detailId?: string
  onClick: () => void
}) {
  return (
    <button
      className={`package-sport${selected ? ' package-sport--selected' : ''}${compact ? ' package-sport--compact' : ''}`}
      type="button"
      aria-label={`${selected ? 'Close' : 'Choose'} ${sport.name} packages`}
      aria-pressed={selected}
      aria-controls={selected ? detailId : undefined}
      onClick={onClick}
    >
      <Photo
        {...sportPhotoFor(sport.name, compact ? 300 : 1080)}
        className="package-sport__image"
        alt=""
        aria-hidden="true"
        loading="lazy"
        sizes={compact ? '(min-width: 1101px) 5vw, 15vw' : '(min-width: 1280px) 20vw, (min-width: 720px) 33vw, 82vw'}
        data-testid="package-sport-photo"
      />
      <span className="package-sport__scrim" aria-hidden="true" />
      <span className="package-sport__name">{sport.name}</span>
      {!compact && <span className="package-sport__count">{optionCount} {optionCount === 1 ? 'option' : 'options'}</span>}
      {!selected && !compact && <span className="package-sport__hint">Explore packages</span>}
    </button>
  )
}

function PackageCard({
  pkg,
  card,
  cardsLoading,
  search,
}: {
  pkg: SportPackage
  card: MemberCard | null
  cardsLoading: boolean
  search: string
}) {
  const price = priceBreakdown(pkg, card)
  const title = `${pkg.sportName} - ${packageSummary(pkg)}`
  return (
    <li
      className="card package-card package-option"
      aria-label={`${trainingFormatLabel(pkg.trainingFormat)} ${pkg.sportName}, ${packageSummary(pkg)}`}
      data-testid="package-option"
    >
      <div className="package-option__heading">
        <p className="package-option__sessions">{sessionsLabel(pkg.sessionCount)}</p>
        <p className="package-option__duration">{durationLabel(pkg.durationDays)}</p>
      </div>
      <p className="package-card__price" data-testid="final-price">
        {/* The price waits for the card, so a discounted package never shows the full price first. */}
        {cardsLoading && !isSingleVisit(pkg) ? '-' : formatMoney(price.final)}
      </p>
      <p className="package-card__price-note">
        {cardsLoading && !isSingleVisit(pkg) ? (
          'Checking your card discount'
        ) : price.discountPercent > 0 ? (
          <>
            <s>{formatMoney(price.original)}</s> - {card?.tierName} {price.discountPercent}% off
          </>
        ) : isSingleVisit(pkg) ? (
          'Single visit - no card discount'
        ) : (
          'Original price'
        )}
        {pkg.sessionMinutes ? ` - ${pkg.sessionMinutes} min / session` : ''}
      </p>
      <Link className={buttonClassName('primary', 'md', true)} to={packageDetailPath(pkg.id, search)} aria-label={`View package ${title}`}>
        View package
      </Link>
    </li>
  )
}
