import { useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../../api/client'
import type { CardTier } from '../../api/cardsApi'
import type { Sport, SportPackage } from '../../api/packagesApi'
import { homePathFor } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { Button, Modal } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { useResource } from '../../hooks/useResource'
import { BROWSE_LEVELS } from '../member/scheduleFilters'
import { isSingleVisit } from '../member/packagesModel'
import { HomeHeader } from './HomeHeader'
import { HomeAuthDialog } from './HomeAuthDialog'
import { HomeIcon } from './HomeIcons'
import { HOME_FACILITIES, HOME_FAQ, HOME_HERO_VIDEOS, HOME_NOTICES, HOME_PHOTOS, HOME_SPORTS, homeHero } from './homeContent'
import { Photo } from '../../media/Photo'
import { HomeHeroVideo } from './HomeHeroVideo'
import { HomeSelect } from './HomeSelect'
import { HomeCoaches } from './HomeCoaches'
import type { PublicCoach } from './homeCoachModel'
import { buildVenueTiles, facilitySummary, isPublicFacility, type PublicFacility } from './homeFacilityModel'
import '@fontsource/be-vietnam-pro/700.css'
import '@fontsource/be-vietnam-pro/800.css'
import '@fontsource/be-vietnam-pro/800-italic.css'
import '@fontsource/be-vietnam-pro/900.css'
import '../../styles/home.css'

interface Notice { title: string; text: string }

const FORMAT_OPTIONS = [
  { value: 'COACH_LED', label: 'Coach-led' },
  { value: 'SELF_TRAINING', label: 'Self-training' },
]

function restoredFinder(state: unknown) {
  const saved = (state as { homeFinder?: Record<string, unknown> } | null)?.homeFinder
  return {
    sportId: typeof saved?.sportId === 'string' && /^\d+$/.test(saved.sportId) && Number.isSafeInteger(Number(saved.sportId)) && Number(saved.sportId) > 0 ? saved.sportId : '',
    level: BROWSE_LEVELS.some((option) => option.value === saved?.level) ? String(saved?.level) : '',
    format: FORMAT_OPTIONS.some((option) => option.value === saved?.format) ? String(saved?.format) : '',
  }
}

/** Only public endpoints, without tokens or authentication refresh side effects. */
async function publicList<T>(path: string, signal: AbortSignal, valid: (entry: unknown) => boolean): Promise<T[]> {
  const result = await api.get<unknown>(path, { signal, auth: false })
  if (!Array.isArray(result) || !result.every(valid)) throw new Error('The public catalogue could not be read.')
  return result as T[]
}
function namedRecord(entry: unknown): entry is Record<string, unknown> {
  if (!entry || typeof entry !== 'object') return false
  const row = entry as Record<string, unknown>
  return Number.isSafeInteger(row.id) && Number(row.id) > 0 && typeof row.name === 'string'
}
const loadSports = (signal: AbortSignal) => publicList<Sport>('/sports', signal, namedRecord)
const loadPackages = (signal: AbortSignal) => publicList<SportPackage>('/packages', signal, (row) => namedRecord(row)
  && typeof row.sportName === 'string' && ['SELF_TRAINING', 'COACH_LED'].includes(String(row.trainingFormat))
  && typeof row.durationDays === 'number' && row.durationDays >= 0
  && (typeof row.priceAmount === 'number' || typeof row.priceAmount === 'string')
  && String(row.priceAmount).trim() !== '' && Number.isFinite(Number(row.priceAmount)) && Number(row.priceAmount) >= 0)
const loadCoaches = (signal: AbortSignal) => publicList<PublicCoach>('/coaches/public', signal, (entry) => {
  if (!entry || typeof entry !== 'object') return false
  const row = entry as Record<string, unknown>
  const optionalText = (value: unknown) => value === undefined || value === null || typeof value === 'string'
  return Number.isSafeInteger(row.id) && Number(row.id) > 0 && typeof row.fullName === 'string' && row.fullName.trim() !== ''
    && Array.isArray(row.sportIds) && row.sportIds.every((id) => Number.isSafeInteger(id)) && optionalText(row.bio) && optionalText(row.avatarUrl)
})
const loadFacilities = (signal: AbortSignal) => publicList<PublicFacility>('/facilities/public', signal, isPublicFacility)
const loadTiers = (signal: AbortSignal) => publicList<CardTier>('/membership-cards/tiers', signal, (row) => namedRecord(row)
  && (row.discountPercentage === null || typeof row.discountPercentage === 'number' && row.discountPercentage >= 0 && row.discountPercentage <= 100))

/** The three package cards from the approved design. Each links to a live package of that kind when one exists. */
const PLAN_CARDS = [
  { kind: 'single', tag: 'Flexible', title: 'Single visit', points: ['Join one session', 'Any sport', 'No commitment'] },
  { kind: 'coach', tag: 'Most guided', title: 'Coach-led', points: ['Qualified coach', 'Classes by level', 'Card discount'] },
  { kind: 'self', tag: 'Independent', title: 'Self-training', points: ['Train at your pace', 'Package per sport', 'Card discount'] },
] as const

export function HomePage() {
  const { role, status, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [sports, retrySports] = useResource(loadSports)
  const [packages, retryPackages] = useResource(loadPackages)
  const [facilities, retryFacilities] = useResource(loadFacilities)
  const [tiers, retryTiers] = useResource(loadTiers)
  const [coaches, retryCoaches] = useResource(loadCoaches)
  const [finder, setFinder] = useState(() => restoredFinder(location.state))
  const { sportId, level, format } = finder
  const [notice, setNotice] = useState<Notice | null>(null)
  const [authDestination, setAuthDestination] = useState<string | null>(null)
  const [previousStatus, setPreviousStatus] = useState(status)
  // Drop a guest prompt immediately when another tab restores or changes the session.
  if (previousStatus !== status) {
    setPreviousStatus(status)
    setAuthDestination(null)
  }
  const [featured, setFeatured] = useState(0)
  const spotlight = [HOME_SPORTS[featured], HOME_SPORTS[(featured + 1) % HOME_SPORTS.length]]
  const catalogSports = sports.status === 'ready' ? sports.data : []
  const sportFor = (name: string) => catalogSports.find((sport) => sport.name.toLowerCase() === name.toLowerCase())
  const classPath = (id = '', trainingFormat = '') => {
    const params = new URLSearchParams()
    if (id) params.set('sportId', id)
    if (level) params.set('level', level)
    if (trainingFormat) params.set('format', trainingFormat)
    return `/member/classes${params.size ? `?${params}` : ''}`
  }
  const staff = Boolean(role && role !== 'MEMBER')
  const facilityRows = facilities.status === 'ready' ? facilities.data : null
  const venueTiles = buildVenueTiles(HOME_FACILITIES, sports.status === 'ready' ? sports.data : null, facilityRows)
  const sportCount = sports.status === 'ready' && sports.data.length ? sports.data.length : HOME_SPORTS.length

  /** Facility tiles preselect their sport in the class finder (when the catalogue is loaded) and move to it. */
  function findSport(name: string) {
    const record = sportFor(name)
    if (record) changeFinder('sportId', String(record.id))
    const section = document.getElementById('finder')
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    section?.scrollIntoView?.({ behavior: reduce ? 'auto' : 'smooth', block: 'center' })
    section?.querySelector<HTMLButtonElement>('.home-select__button')?.focus({ preventScroll: true })
  }

  function changeFinder(key: keyof typeof finder, value: string) {
    const next = { ...finder, [key]: value }
    setFinder(next)
    // History state restores public filters when authentication is canceled with Back.
    navigate(location.pathname + location.search + location.hash, { replace: true, state: { ...location.state, homeFinder: next } })
  }

  function requestMemberAction(path: string) {
    if (status === 'loading') return
    if (staff) setNotice(HOME_NOTICES.member)
    else if (status === 'anonymous') setAuthDestination(path)
    else if (status === 'error') navigate('/login', { state: { from: path } })
    else navigate(path)
  }

  function memberAction(path: string, children: ReactNode, variant: 'primary' | 'secondary' = 'primary', label?: string, className = buttonClassName(variant, 'md')) {
    return staff ? (
      <button type="button" className={className} aria-label={label} onClick={() => setNotice(HOME_NOTICES.member)}>{children}</button>
    ) : (
      <Link className={className} to={path} aria-label={label} aria-disabled={status === 'loading' || undefined} onClick={(event) => {
        if (status !== 'authenticated') {
          event.preventDefault()
          requestMemberAction(path)
        }
      }}>{children}</Link>
    )
  }

  const availablePackages = packages.status === 'ready' ? packages.data.filter((pkg) => pkg.isActive !== false) : []
  const footballPackages = availablePackages.filter((pkg) => pkg.sportName.toLowerCase() === 'football')
  const packageChoices = footballPackages.length ? footballPackages : availablePackages
  const planPackage = {
    single: packageChoices.find(isSingleVisit),
    coach: packageChoices.find((pkg) => pkg.trainingFormat === 'COACH_LED' && !isSingleVisit(pkg)),
    self: packageChoices.find((pkg) => pkg.trainingFormat === 'SELF_TRAINING' && !isSingleVisit(pkg)),
  }

  return (
    <div className="home-page">
      <a className="home-skip" href="#main-content">Skip to content</a>
      <HomeHeader groups={[
        { label: 'Sports', href: '#sports', children: HOME_SPORTS.map((sport) => ({ label: sport.name, href: `#home-sport-${sport.name.toLowerCase()}` })) },
        { label: 'Coaches', href: '#coaches', children: coaches.status === 'ready' ? coaches.data.map((coach) => ({ label: coach.fullName, href: `#coach-${coach.id}` })) : [] },
        { label: 'Packages', href: '#packages', children: PLAN_CARDS.map((card) => ({ label: card.title, href: `#home-plan-${card.kind}` })) },
        { label: 'FAQ', href: '#faq', children: HOME_FAQ.map((item, index) => ({ label: item.question, href: `#home-faq-${index}` })) },
      ]} />
      <main className="home-main" id="main-content" tabIndex={-1}>
        <section className="home-hero" aria-labelledby="home-title">
          <Photo className="home-hero__photo" src={HOME_PHOTOS.hero} fallback={homeHero} sizes="100vw" width="2200" height="1467" fetchPriority="high" />
          <HomeHeroVideo sources={HOME_HERO_VIDEOS} />
          <div className="home-wrap home-hero__body">
            <div className="home-highlight">
              <h2>Facility highlights</h2>
              <p>{sportCount} {sportCount === 1 ? 'sport' : 'sports'}. 1 center.</p>
              <a href="#sports" aria-label="See all sports"><HomeIcon name="arrow" /></a>
            </div>
            <div className="home-hero__copy">
              <h1 id="home-title">Find your sport.<br /><em>Build your skills.</em></h1>
              <p>Coach-led classes or self-training, at every level.</p>
              <div className="home-hero__actions">
                {status === 'anonymous'
                  ? <Link className="home-btn home-btn--primary" to="/register">Join now</Link>
                  : memberAction('/member/classes', 'Browse classes', 'primary', undefined, 'home-btn home-btn--primary')}
                <a className="home-play" href="#finder"><span aria-hidden="true" />Find a class</a>
              </div>
            </div>
          </div>
          <div className="home-wrap">
            <div className="home-strip">
              <Photo key={spotlight[0].name} className="home-strip__photo" src={spotlight[0].photo} fallback={spotlight[0].image} sizes="(min-width: 1101px) 300px, 100vw" width="1000" height="667" />
              <div className="home-strip__sports" aria-live="polite">
                {spotlight.map((sport) => <div key={sport.name}><span>Sports</span><h3>{sport.name}</h3><p>{sport.description}</p></div>)}
              </div>
              <div className="home-strip__side">
                {memberAction('/member/membership/cards', 'Membership offers', 'primary', undefined, 'home-strip__offer')}
                <div className="home-strip__count">
                  <button type="button" aria-label="Previous sports" onClick={() => setFeatured((value) => (value + HOME_SPORTS.length - 1) % HOME_SPORTS.length)}><HomeIcon name="arrow" className="home-icon home-icon--flip" /></button>
                  <span aria-hidden="true">{String(featured + 1).padStart(2, '0')}</span>
                  <span className="home-strip__track" aria-hidden="true"><span style={{ width: `${((featured + 1) / HOME_SPORTS.length) * 100}%` }} /></span>
                  <span aria-hidden="true">{String(HOME_SPORTS.length).padStart(2, '0')}</span>
                  <button type="button" aria-label="Next sports" onClick={() => setFeatured((value) => (value + 1) % HOME_SPORTS.length)}><HomeIcon name="arrow" /></button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <div className="home-wrap">
          <section className="home-finder" id="finder" aria-labelledby="finder-title">
            <h2 id="finder-title" className="home-sr-only">Find your next class</h2>
            <form onSubmit={(event) => {
              event.preventDefault()
              requestMemberAction(classPath(sportId, format))
            }}>
              <HomeSelect layout="grid" label="Sport" placeholder="All sports" value={sportId} options={catalogSports.map((sport) => ({ value: String(sport.id), label: sport.name }))} onChange={(value) => changeFinder('sportId', value)} />
              <HomeSelect label="Level" placeholder="All levels" value={level} options={BROWSE_LEVELS} onChange={(value) => changeFinder('level', value)} />
              <HomeSelect label="Training" placeholder="Any format" value={format} options={FORMAT_OPTIONS} onChange={(value) => changeFinder('format', value)} />
              <Button type="submit" disabled={status === 'loading'}>Find classes</Button>
            </form>
            {sports.status === 'error' && <CatalogNotice message="Sport filters are temporarily unavailable. You can still browse classes." retry={retrySports} />}
          </section>
        </div>

        <section className="home-features" aria-labelledby="features-title">
          <div className="home-wrap">
            <div className="home-head home-head--center"><span className="home-kicker">Why Sportify</span><h2 id="features-title">Our <span>features</span></h2></div>
            <ul className="home-features__list">
              <li><span className="home-features__icon"><HomeIcon name="sports" /></span><h3>Six sports</h3><p>One center, every level</p></li>
              <li><span className="home-features__icon"><HomeIcon name="coach" /></span><h3>Expert coaches</h3><p>Certified and experienced</p></li>
              <li><span className="home-features__icon"><HomeIcon name="package" /></span><h3>Flexible packages</h3><p>Per sport or single visit</p></li>
              <li><span className="home-features__icon"><HomeIcon name="card" /></span><h3>Member discounts</h3><p>Save with membership cards</p></li>
            </ul>
          </div>
        </section>

        <section className="home-section" id="sports" aria-labelledby="sports-title">
          <div className="home-wrap">
            <div className="home-head"><div><span className="home-kicker">Sports offered</span><h2 id="sports-title">Choose your <span>sport</span></h2></div>{memberAction('/member/schedule', 'View schedule', 'secondary', undefined, 'home-more')}</div>
            <div className="home-sports">
              {HOME_SPORTS.map((sport) => {
                const record = sportFor(sport.name)
                const content = <><Photo src={sport.photo} fallback={sport.image} alt={`${sport.name} training`} sizes="(min-width: 1101px) 400px, (min-width: 641px) 50vw, 100vw" width="1000" height="667" loading="lazy" /><span className="home-sport__label"><h3>{sport.name}</h3><span className="home-sport__go" aria-hidden="true"><HomeIcon name="arrow" /></span></span></>
                return <article id={`home-sport-${sport.name.toLowerCase()}`} className="home-sport" key={sport.name}>
                  {record
                    ? memberAction(classPath(String(record.id)), content, 'secondary', `Explore ${sport.name}`, 'home-sport__link')
                    : <button type="button" className="home-sport__link" aria-label={`Explore ${sport.name}`} onClick={() => setNotice({ title: sport.name, text: `${sport.description}. Choose from current sports in the class finder when the catalogue is available.` })}>{content}</button>}
                </article>
              })}
            </div>
          </div>
        </section>

        <section className="home-section" id="facilities" aria-labelledby="facilities-title">
          <div className="home-wrap">
            <div className="home-head"><div><span className="home-kicker">Our facilities</span><h2 id="facilities-title">Where you <span>play</span></h2><p className="home-head__sub">{facilitySummary(facilityRows)}</p></div><a className="home-more" href="#finder">Book a session</a></div>
            {facilities.status === 'error' && <CatalogNotice message="Court details are temporarily unavailable." retry={retryFacilities} />}
            {facilities.status === 'ready' && sports.status === 'ready' && !venueTiles.length && <p className="home-loading">Facility details will appear here soon.</p>}
            {venueTiles.length > 0 && <ul className={`home-venues${venueTiles.some((venue) => venue.wide) ? '' : ' home-venues--even'}`}>
              {venueTiles.map((venue) => (
                <li key={venue.name} className={`home-venue${venue.wide ? ' home-venue--wide' : ''}`}>
                  <Photo src={venue.photo} fallback={venue.image} alt="" sizes={venue.wide ? '(min-width: 1101px) 620px, 100vw' : '(min-width: 1101px) 310px, 50vw'} width={venue.wide ? '1400' : '900'} height={venue.wide ? '700' : '600'} loading="lazy" />
                  <span className="home-venue__label">{venue.label}</span>
                  <div className="home-venue__text">
                    <h3>{venue.name}</h3>
                    {venue.note && <p>{venue.note}</p>}
                    <span className="home-venue__go" aria-hidden="true">Find classes -&gt;</span>
                  </div>
                  <button type="button" className="home-venue__link" aria-label={`Find ${venue.sport} classes - ${venue.name}${venue.note ? `, ${venue.note}` : ''}`} onClick={() => findSport(venue.sport)} />
                </li>
              ))}
            </ul>}
          </div>
        </section>

        <section className="home-section" id="coaches" aria-labelledby="coaches-title">
          <div className="home-wrap">
            <div className="home-head"><div><span className="home-kicker">Our coaches</span><h2 id="coaches-title">Train with the <span>best</span></h2></div><button type="button" className="home-more" onClick={() => setNotice(HOME_NOTICES.coaches)}>All coaches</button></div>
            {coaches.status === 'loading' && <p className="home-loading" role="status">Loading coaches...</p>}
            {coaches.status === 'error' && <CatalogNotice message="Coach profiles are temporarily unavailable." retry={retryCoaches} />}
            {coaches.status === 'ready' && !coaches.data.length && <p className="home-loading">Coach profiles will appear here soon.</p>}
            {coaches.status === 'ready' && coaches.data.length > 0 && <HomeCoaches coaches={coaches.data} sports={catalogSports} onOpen={setNotice} />}
          </div>
        </section>

        <section className="home-pricing" id="packages" aria-labelledby="packages-title">
          <div className="home-wrap">
            <div className="home-head"><div><span className="home-kicker">Packages</span><h2 id="packages-title">Choose how you <span>train</span></h2></div></div>
            {packages.status === 'loading' && <p className="home-loading" role="status">Loading current packages...</p>}
            {packages.status === 'error' && <CatalogNotice message="Current packages and prices are temporarily unavailable." retry={retryPackages} />}
            {packages.status === 'ready' && !availablePackages.length && <p className="home-loading">No packages are currently available. Please check again later.</p>}
            <div className="home-plans">
              {PLAN_CARDS.map((card) => {
                const pkg = planPackage[card.kind]
                const main = card.kind === 'coach'
                return <article id={`home-plan-${card.kind}`} className={`home-plan${main ? ' home-plan--main' : ''}`} key={card.kind}>
                  {main && <span className="home-plan__badge">Popular</span>}
                  <small>{card.tag}</small>
                  <h3>{card.title}</h3>
                  <ul>{card.points.map((point) => <li key={point}>{point}</li>)}</ul>
                  {pkg
                    ? memberAction(`/member/packages/${pkg.id}`, 'View options', 'primary', `View options for ${pkg.name}`)
                    : memberAction('/member/packages', 'View options', 'primary', `View ${card.title.toLowerCase()} options`)}
                </article>
              })}
            </div>
            <div className="home-cards">
              <div>
                <h3>Membership cards</h3>
                <p>Save on every sport package.</p>
                {tiers.status === 'error' && <CatalogNotice message="Current card benefits are temporarily unavailable." retry={retryTiers} />}
              </div>
              {memberAction('/member/membership/cards', 'View cards', 'primary', undefined, 'home-btn home-btn--light')}
            </div>
          </div>
        </section>

        <section className="home-section" id="faq" aria-labelledby="faq-title">
          <div className="home-wrap home-faq">
            <div className="home-faq__intro">
              <span className="home-kicker">FAQ</span>
              <h2 id="faq-title">Got <span>questions?</span></h2>
            </div>
            <div className="home-faq__list">
              {HOME_FAQ.map((item, index) => <details id={`home-faq-${index}`} key={item.question} open={index === 0}><summary>{item.question}</summary><p>{item.answer}</p></details>)}
            </div>
          </div>
        </section>

        <section className="home-cta" aria-labelledby="cta-title">
          <Photo className="home-cta__photo" src={HOME_PHOTOS.cta} fallback={homeHero} sizes="100vw" width="2000" height="1333" loading="lazy" />
          <div className="home-wrap">
            <h2 id="cta-title">Ready to <span>play?</span></h2>
            <div className="home-cta__actions">
              {status === 'anonymous'
                ? <Link className="home-btn home-btn--primary" to="/register">Join now</Link>
                : memberAction('/member/classes', 'Browse classes', 'primary', undefined, 'home-btn home-btn--primary')}
              <a className="home-btn home-btn--line" href="#finder">Find a class</a>
            </div>
          </div>
        </section>
      </main>

      <footer className="home-footer">
        <div className="home-wrap home-footer__grid">
          <div><p className="home-footer__logo">Sportify</p><p>One center. Six sports. More ways to grow.</p></div>
          <nav aria-label="Footer explore"><h2>Explore</h2><ul><li><a href="#finder">Find a class</a></li><li><a href="#sports">Sports</a></li><li><a href="#facilities">Facilities</a></li><li><a href="#coaches">Coaches</a></li><li><a href="#packages">Packages</a></li></ul></nav>
          <nav aria-label="Footer sports"><h2>Sports</h2><ul>{HOME_SPORTS.map((sport) => <li key={sport.name}><a href="#sports">{sport.name}</a></li>)}</ul></nav>
          <div className="home-footer__help"><h2>Need help?</h2><Button onClick={() => setNotice(HOME_NOTICES.reception)}>Contact reception</Button><Button variant="secondary" onClick={() => setNotice(HOME_NOTICES.assistant)}>AI Assistant</Button></div>
        </div>
        <div className="home-wrap home-footer__bottom"><small>Copyright 2026 Sportify Sports Center</small><Link to="/credits">Photo credits</Link></div>
      </footer>

      <BackToTop />

      <HomeAuthDialog destination={status === 'anonymous' ? authDestination : null} onClose={() => setAuthDestination(null)} />

      <Modal open={notice !== null} onClose={() => setNotice(null)} title={notice?.title ?? ''} footer={notice === HOME_NOTICES.member && <Link className={buttonClassName('primary', 'md')} to={homePathFor(user)}>Open my workspace</Link>}>
        <p>{notice?.text}</p>
      </Modal>
    </div>
  )
}

function CatalogNotice({ message, retry }: { message: string; retry: () => void }) {
  return <div className="home-catalog-notice" role="status"><p>{message}</p><Button variant="secondary" size="sm" onClick={retry}>Retry</Button></div>
}

/** Floating button in the bottom right corner, shown after the visitor scrolls past the hero. */
function BackToTop() {
  const [visible, setVisible] = useState(false)
  useEffect(() => {
    const update = () => setVisible(window.scrollY > 600)
    update()
    window.addEventListener('scroll', update, { passive: true })
    return () => window.removeEventListener('scroll', update)
  }, [])
  return (
    <button
      type="button"
      className={`home-top${visible ? ' home-top--visible' : ''}`}
      aria-label="Back to top"
      tabIndex={visible ? 0 : -1}
      onClick={() => {
        const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
        window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' })
        document.getElementById('main-content')?.focus({ preventScroll: true })
      }}
    >
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M12 19V5M5.5 11.5L12 5l6.5 6.5" /></svg>
    </button>
  )
}
