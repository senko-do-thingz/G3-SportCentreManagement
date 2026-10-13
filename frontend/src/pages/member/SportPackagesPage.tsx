import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { memberApi, type MemberCard } from '../../api/memberApi'
import { packagesApi, type Sport, type SportPackage } from '../../api/packagesApi'
import { Button, Card, EmptyState, ErrorState, PageHeader, Select } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { formatMoney } from '../../utils/format'
import { cardDiscountText, currentCard, MEMBER_PATHS, toIsoDate, trainingFormatLabel } from './dashboardModel'
import {
  durationOptions,
  EMPTY_FILTERS,
  filterPackages,
  filtersFromParams,
  filtersToParams,
  FORMAT_FILTER_OPTIONS,
  isSingleVisit,
  packageDetailPath,
  packageSummary,
  priceBreakdown,
  sanitizeFilters,
  type PackageFilters,
} from './packagesModel'
import { useMemberResource } from './useMemberResource'

interface Catalog {
  sports: Sport[]
  packages: SportPackage[]
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
 * GET /membership-cards/my for the discount. Filters live in the URL query (sport, format,
 * duration) so the detail page can come back to the same list.
 */
export function SportPackagesPage() {
  const [catalog, reloadCatalog] = useMemberResource(loadCatalog)
  const [cards] = useMemberResource(loadMyCards)
  const [searchParams, setSearchParams] = useSearchParams()
  const [today] = useState(() => toIsoDate(new Date()))
  const rawFilters = filtersFromParams(searchParams)

  const header = (
    <PageHeader
      eyebrow="Your sport, your way"
      title="Sport Packages"
      description="Choose one sport, your training style and a package that suits you."
      actions={
        <Link className={buttonClassName('secondary', 'md')} to={MEMBER_PATHS.membership}>
          Membership cards
        </Link>
      }
    />
  )

  if (catalog.status === 'loading') {
    return (
      <div className="page">
        {header}
        <Spinner label="Loading sport packages" />
      </div>
    )
  }
  if (catalog.status === 'error') {
    return (
      <div className="page">
        {header}
        <ErrorState title="We could not load the sport packages" error={catalog.error} onRetry={reloadCatalog} />
      </div>
    )
  }

  const { sports, packages } = catalog.data
  // Values in the URL that match nothing selectable are ignored, so the selects and the list agree.
  const filters = sanitizeFilters(rawFilters, sports, packages)
  const setFilter = (key: keyof PackageFilters, value: string) => {
    setSearchParams(filtersToParams({ ...filters, [key]: value }), { replace: true })
  }
  const card = cards.status === 'ready' ? currentCard(cards.data, today) : null
  const shown = filterPackages(packages, filters)
  const query = filtersToParams(filters).toString()
  const search = query ? `?${query}` : ''

  return (
    <div className="page">
      {header}

      <Card title="Find your package">
        <div className="package-filters">
          <Select
            label="Sport"
            name="sport"
            placeholder="All sports"
            options={[...sports]
              .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0) || a.name.localeCompare(b.name))
              .map((sport) => ({ value: String(sport.id), label: sport.name }))}
            value={filters.sport}
            onChange={(event) => setFilter('sport', event.target.value)}
          />
          <Select
            label="Training type"
            name="format"
            placeholder="All types"
            options={FORMAT_FILTER_OPTIONS}
            value={filters.format}
            onChange={(event) => setFilter('format', event.target.value)}
          />
          <Select
            label="Duration"
            name="duration"
            placeholder="All durations"
            options={durationOptions(packages)}
            value={filters.duration}
            onChange={(event) => setFilter('duration', event.target.value)}
          />
          <div className="package-filters__reset">
            <Button variant="secondary" onClick={() => setSearchParams(filtersToParams(EMPTY_FILTERS), { replace: true })}>
              Reset filters
            </Button>
          </div>
        </div>
        {card && card.discountPercentage ? (
          <p className="notice notice--info package-filters__note">
            Your {card.tierName} card gives you {cardDiscountText(card.discountPercentage)}. Prices below already include
            it.
          </p>
        ) : null}
        {cards.status === 'error' && (
          <p className="notice notice--warning package-filters__note" role="alert">
            We could not load your membership card, so prices are shown without a card discount. The final price is
            shown again before you confirm.
          </p>
        )}
      </Card>

      {shown.length === 0 ? (
        <EmptyState
          title="No packages match these filters"
          description="Try another sport, training type or duration."
          action={
            <Button variant="secondary" onClick={() => setSearchParams(filtersToParams(EMPTY_FILTERS), { replace: true })}>
              Reset filters
            </Button>
          }
        />
      ) : (
        <ul className="package-grid" aria-label="Sport packages">
          {shown.map((pkg) => (
            <PackageCard key={pkg.id} pkg={pkg} card={card} cardsLoading={cards.status === 'loading'} search={search} />
          ))}
        </ul>
      )}
    </div>
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
    <li className="card package-card" aria-label={`${trainingFormatLabel(pkg.trainingFormat)} ${pkg.sportName}, ${packageSummary(pkg)}`}>
      <p className="package-card__format">{trainingFormatLabel(pkg.trainingFormat)}</p>
      <h2 className="package-card__title">{pkg.sportName}</h2>
      <p className="package-card__summary">{packageSummary(pkg)}</p>
      <p className="package-card__price" data-testid="final-price">
        {formatMoney(price.final)}
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
