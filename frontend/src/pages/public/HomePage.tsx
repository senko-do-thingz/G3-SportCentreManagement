import { useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../../api/client'
import type { CardTier } from '../../api/cardsApi'
import type { Sport, SportPackage } from '../../api/packagesApi'
import { homePathFor } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { Button, Modal, Select } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { useResource } from '../../hooks/useResource'
import { formatMoney } from '../../utils/format'
import { BROWSE_LEVELS } from '../member/scheduleFilters'
import { isSingleVisit } from '../member/packagesModel'
import { HomeHeader } from './HomeHeader'
import { EXAMPLE_COACHES, HOME_NOTICES, HOME_SPORTS, homeHero } from './homeContent'
import '../../styles/home.css'

interface Notice { title: string; text: string }

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
const loadTiers = (signal: AbortSignal) => publicList<CardTier>('/membership-cards/tiers', signal, (row) => namedRecord(row)
  && (row.discountPercentage === null || typeof row.discountPercentage === 'number' && row.discountPercentage >= 0 && row.discountPercentage <= 100))

export function HomePage() {
  const { role, user } = useAuth()
  const navigate = useNavigate()
  const [sports, retrySports] = useResource(loadSports)
  const [packages, retryPackages] = useResource(loadPackages)
  const [tiers, retryTiers] = useResource(loadTiers)
  const [sportId, setSportId] = useState('')
  const [level, setLevel] = useState('')
  const [notice, setNotice] = useState<Notice | null>(null)
  const catalogSports = sports.status === 'ready' ? sports.data : []
  const sportFor = (name: string) => catalogSports.find((sport) => sport.name.toLowerCase() === name.toLowerCase())
  const classPath = (id = '', format = '') => {
    const params = new URLSearchParams()
    if (id) params.set('sportId', id)
    if (level) params.set('level', level)
    if (format) params.set('format', format)
    return `/member/classes${params.size ? `?${params}` : ''}`
  }

  function memberAction(path: string, children: ReactNode, variant: 'primary' | 'secondary' = 'primary', label?: string) {
    const className = buttonClassName(variant, 'md')
    return role && role !== 'MEMBER' ? (
      <button type="button" className={className} aria-label={label} onClick={() => setNotice(HOME_NOTICES.member)}>{children}</button>
    ) : (
      <Link className={className} to={path} aria-label={label}>{children}</Link>
    )
  }

  const availablePackages = packages.status === 'ready' ? packages.data.filter((pkg) => pkg.isActive !== false) : []
  const footballPackages = availablePackages.filter((pkg) => pkg.sportName.toLowerCase() === 'football')
  const packageChoices = footballPackages.length ? footballPackages : availablePackages
  const featuredPackages = [
    packageChoices.find((pkg) => pkg.trainingFormat === 'SELF_TRAINING' && !isSingleVisit(pkg)),
    packageChoices.find((pkg) => pkg.trainingFormat === 'COACH_LED' && !isSingleVisit(pkg)),
    packageChoices.find(isSingleVisit),
  ].filter((pkg): pkg is SportPackage => pkg !== undefined)

  return (
    <div className="home-page">
      <a className="home-skip" href="#main-content">Skip to content</a>
      <HomeHeader />
      <main className="home-container home-main" id="main-content" tabIndex={-1}>
        <section className="home-hero" aria-labelledby="home-title">
          <div className="home-hero__copy">
            <span className="home-eyebrow">One center. Six sports.</span>
            <h1 id="home-title">Find your sport.<br />Build your skills.</h1>
            <p>Classes for your age, level and goals.</p>
            <p>Train with a coach or practise at your own pace.</p>
            <div className="home-actions">
              <a className={buttonClassName('primary', 'md')} href="#finder">Find a class</a>
              {memberAction('/member/schedule', 'View schedule', 'secondary')}
            </div>
            <div className="home-hero__facts"><strong>6 sports</strong><strong>Coach-led & self-training</strong></div>
          </div>
          <img className="home-hero__image" src={homeHero} width="310" height="228" alt="Players practising football together on an outdoor pitch" fetchPriority="high" />
        </section>

        <section className="home-finder home-panel" id="finder" aria-labelledby="finder-title">
          <h2 id="finder-title">Find your next class</h2>
          <form onSubmit={(event) => {
            event.preventDefault()
            if (role && role !== 'MEMBER') setNotice(HOME_NOTICES.member)
            else navigate(classPath(sportId))
          }}>
            <Select label="Sport" placeholder="All sports" value={sportId} options={catalogSports.map((sport) => ({ value: String(sport.id), label: sport.name }))} onChange={(event) => setSportId(event.target.value)} />
            <Select label="Age group" value="" options={[]} placeholder="All ages" disabled hint="Ask reception for age guidance." />
            <Select label="Level" placeholder="All levels" value={level} options={BROWSE_LEVELS} onChange={(event) => setLevel(event.target.value)} />
            <Button type="submit">Find classes</Button>
          </form>
          {sports.status === 'error' && <CatalogNotice message="Sport filters are temporarily unavailable. You can still browse classes." retry={retrySports} />}
        </section>

        <section id="sports" className="home-section" aria-labelledby="sports-title">
          <div className="home-section__heading"><div><h2 id="sports-title">Choose your sport</h2><p>Six ways to move, learn and improve.</p></div>{memberAction('/member/schedule', 'View schedule', 'secondary')}</div>
          <div className="home-sports">
            {HOME_SPORTS.map((sport) => {
              const record = sportFor(sport.name)
              const path = record ? classPath(String(record.id)) : '/member/classes'
              return <article className="home-panel home-sport" key={sport.name}>
                <img src={sport.image} alt={`${sport.name} training`} width="86" height="66" loading="lazy" />
                <h3>{sport.name}</h3><p>{sport.description}</p>
                {record ? memberAction(path, 'Explore', 'secondary', `Explore ${sport.name}`) : <Button variant="secondary" onClick={() => setNotice({ title: sport.name, text: `${sport.description}. Choose from current sports in the class finder when the catalogue is available.` })} aria-label={`Explore ${sport.name}`}>Explore</Button>}
              </article>
            })}
          </div>
        </section>

        <section id="classes" className="home-section" aria-labelledby="classes-title">
          <div className="home-section__heading"><div><h2 id="classes-title">Find a class that fits</h2><p>Example classes. Sign in for current times, coaches and availability.</p></div></div>
          <div className="home-grid home-grid--two">
            <article className="home-panel home-session"><h3>Football Fundamentals</h3><p>Beginner - Coach-led</p><p>Build your passing, ball control and confidence.</p><div className="home-session__action"><span className="home-example">Example class</span>{memberAction(classPath(sportFor('Football')?.id.toString(), 'COACH_LED'), 'Find sessions')}</div></article>
            <article className="home-panel home-session"><h3>Badminton Self-training</h3><p>Self-training - No assigned coach</p><p>Practise independently and improve at your own pace.</p><div className="home-session__action"><span className="home-example">Example class</span>{memberAction(classPath(sportFor('Badminton')?.id.toString(), 'SELF_TRAINING'), 'Find sessions')}</div></article>
          </div>
        </section>

        <section className="home-goals" aria-labelledby="goals-title">
          <div><h2 id="goals-title">Not sure where to start?</h2><p>Tell us your goals. Get suggestions for sports and classes<br className="home-desktop-break" /> that fit your level and free time.</p><Button onClick={() => setNotice(HOME_NOTICES.recommendations)}>Get recommendations</Button></div>
          <div className="home-goals__list"><span>Your goal</span><strong>Fitness & wellbeing</strong><strong>Build skills</strong><strong>Train for competition</strong></div>
        </section>

        <section id="coaches" className="home-section" aria-labelledby="coaches-title">
          <div className="home-section__heading"><div><h2 id="coaches-title">Learn from qualified coaches</h2><p>Explore their sports, skills and certifications. Illustrative profiles.</p></div></div>
          <div className="home-grid">
            {EXAMPLE_COACHES.map((coach) => <article className="home-panel home-coach" key={coach.name}>
              <img src={coach.image} alt={`${coach.sport} coaching example`} width="188" height="92" loading="lazy" />
              <h3>{coach.name}</h3><p>{coach.sport}</p><p className="home-coach__skills">{coach.skills}</p><p className="home-coach__certificate">{coach.certificate}</p>
              <Button variant="secondary" aria-label={`View profile of ${coach.name}`} onClick={() => setNotice({ title: coach.name, text: `Illustrative ${coach.sport.toLowerCase()} coach profile: ${coach.skills}. Example qualification: ${coach.certificate}. For current coach profiles and verified certifications, sign in as a member and open a coach-led session.` })}>View profile</Button>
            </article>)}
          </div>
        </section>

        <section id="packages" className="home-section" aria-labelledby="packages-title">
          <div className="home-section__heading"><div><h2 id="packages-title">Choose how you train</h2><p>Buy a package for each sport. Self-training and coached sessions have separate prices.</p></div></div>
          {packages.status === 'loading' && <p className="home-panel home-loading" role="status">Loading current packages...</p>}
          {packages.status === 'error' && <CatalogNotice message="Current packages and prices are temporarily unavailable." retry={retryPackages} />}
          {packages.status === 'ready' && !featuredPackages.length && <p className="home-panel home-loading">No packages are currently available. Please check again later.</p>}
          <div className="home-grid">
            {featuredPackages.map((pkg) => <article className="home-panel home-package" key={pkg.id}>
              <h3>{pkg.sportName} - {isSingleVisit(pkg) ? 'Single visit' : pkg.trainingFormat === 'SELF_TRAINING' ? 'Self-training' : 'Coach-led'}</h3>
              <p>{isSingleVisit(pkg) ? 'Join one session' : pkg.trainingFormat === 'SELF_TRAINING' ? 'Practise independently' : 'Learn with a qualified coach'}</p>
              <strong className="home-package__price">{formatMoney(pkg.priceAmount)}</strong>
              <p>{isSingleVisit(pkg) ? 'One session - No card discount' : `${pkg.durationDays} days${pkg.sessionCount ? ` - ${pkg.sessionCount} sessions` : ''}`}</p>
              {memberAction(`/member/packages/${pkg.id}`, 'View options', 'secondary', `View options for ${pkg.name}`)}
            </article>)}
          </div>
          <div className="home-benefits"><div><h3>Membership card benefits</h3>
            {tiers.status === 'ready' ? <p>{tiers.data.filter((tier) => tier.isActive !== false && (tier.discountPercentage ?? 0) > 0).map((tier) => `${tier.name}: ${tier.discountPercentage}% off eligible sport packages`).join(' - ') || 'Compare current membership card options.'} Card validity is separate from package validity.</p> : <p>Explore membership discounts. Card validity is separate from package validity.</p>}
            {tiers.status === 'error' && <CatalogNotice message="Current card benefits are temporarily unavailable." retry={retryTiers} />}
          </div>{memberAction('/member/membership/cards', 'View cards')}</div>
        </section>
      </main>
      <footer className="home-footer"><div className="home-container home-footer__grid">
        <div><h2>Sportify</h2><p>One center. Multiple sports. More ways to grow.</p><small>2026 Sportify Sports Center</small></div>
        <div><h3>Explore</h3><div className="home-footer__links"><a href="#sports">Sports</a><a href="#classes">Classes</a><a href="#coaches">Coaches</a></div></div>
        <div><h3>Need assistance?</h3><Button onClick={() => setNotice(HOME_NOTICES.reception)}>Contact reception</Button><Button onClick={() => setNotice(HOME_NOTICES.assistant)}>AI Assistant</Button></div>
      </div></footer>
      <Modal open={notice !== null} onClose={() => setNotice(null)} title={notice?.title ?? ''} footer={notice === HOME_NOTICES.member && <Link className={buttonClassName('primary', 'md')} to={homePathFor(user)}>Open my workspace</Link>}>
        <p>{notice?.text}</p>
      </Modal>
    </div>
  )
}

function CatalogNotice({ message, retry }: { message: string; retry: () => void }) {
  return <div className="home-catalog-notice" role="status"><p>{message}</p><Button variant="secondary" size="sm" onClick={retry}>Retry</Button></div>
}
