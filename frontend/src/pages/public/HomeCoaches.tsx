import { useCallback, useEffect, useRef, useState } from 'react'
import { coachInitials, coachStory, safeAvatar, specialtyTags, sportSummary, type PublicCoach } from './homeCoachModel'


interface HomeCoachesProps {
  coaches: PublicCoach[]
  sports: { id: number; name: string }[]
  onOpen: (notice: { title: string; text: string }) => void
}

/** Coach cards (photo on top, details below) in a horizontal rail with previous and next controls. Content comes only from the coach record. */
export function HomeCoaches({ coaches, sports, onOpen }: HomeCoachesProps) {
  const track = useRef<HTMLUListElement>(null)
  const [edges, setEdges] = useState({ start: true, end: true })

  const measure = useCallback(() => {
    const el = track.current
    if (!el) return
    setEdges({ start: el.scrollLeft <= 4, end: el.scrollLeft + el.clientWidth >= el.scrollWidth - 4 })
  }, [])

  useEffect(() => {
    measure()
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  }, [measure, coaches.length])

  function move(direction: 1 | -1) {
    const el = track.current
    const card = el?.querySelector('li')
    if (!el || !card) return
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    el.scrollBy?.({ left: direction * (card.getBoundingClientRect().width + 24), behavior: reduce ? 'auto' : 'smooth' })
  }

  return (
    <div className="home-rail">
      <button type="button" className="home-rail__arrow home-rail__arrow--prev" aria-label="Previous coaches" disabled={edges.start} onClick={() => move(-1)}>
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M15 6l-6 6 6 6" /></svg>
      </button>
      <ul className="home-rail__track" ref={track} onScroll={measure} aria-label="Coaches">
        {coaches.map((coach) => {
          const names = coach.sportIds.map((id) => sports.find((sport) => sport.id === id)?.name).filter((name): name is string => Boolean(name))
          const story = coachStory(coach, names)
          const tags = specialtyTags(coach.specialties)
          const details = [coach.headline, coach.specialties && `Specialties: ${coach.specialties}`, coach.yearsExperience ? `${coach.yearsExperience} years of experience` : null, names.length ? `Sports: ${names.join(', ')}` : null].filter(Boolean).join('. ')
          return (
            <li key={coach.id} className="home-coach-card">
              <article className="home-coach-card__inner" aria-labelledby={`coach-${coach.id}`}>
                <div className="home-coach-card__media">
                  <CoachAvatar name={coach.fullName} url={safeAvatar(coach.avatarUrl)} />
                  <span className="home-coach-card__sport">{sportSummary(names)}</span>
                  {coach.yearsExperience ? <span className="home-coach-card__years">{coach.yearsExperience} {coach.yearsExperience === 1 ? 'year' : 'years'}</span> : null}
                </div>
                <div className="home-coach-card__body">
                  <h3 id={`coach-${coach.id}`}>
                    <button type="button" aria-label={`View profile of ${coach.fullName}`} onClick={() => onOpen({ title: coach.fullName, text: `${coach.bio?.trim() ? `${coach.bio.trim()} ` : ''}${details ? `${details}. ` : ''}Sign in as a member and open a coach-led class to see upcoming sessions and verified certifications.` })}>{coach.fullName}</button>
                  </h3>
                  {coach.headline?.trim() && <p className="home-coach-card__headline">{coach.headline.trim()}</p>}
                  {story && <p className="home-coach-card__text">{story}</p>}
                  {tags.length > 0 && <ul className="home-coach-card__tags" aria-label="Specialties">{tags.map((tag) => <li key={tag}>{tag}</li>)}</ul>}
                </div>
              </article>
            </li>
          )
        })}
      </ul>
      <button type="button" className="home-rail__arrow home-rail__arrow--next" aria-label="Next coaches" disabled={edges.end} onClick={() => move(1)}>
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M9 6l6 6-6 6" /></svg>
      </button>
    </div>
  )
}

/** Coach photo from the database, or initials on the blue panel when there is no photo or it fails to load. */
function CoachAvatar({ name, url }: { name: string; url: string | null }) {
  const [failed, setFailed] = useState(false)
  return (
    <span className="home-coach-card__avatar" aria-hidden="true">
      {url && !failed ? <img src={url} alt="" width="400" height="500" loading="lazy" onError={() => setFailed(true)} /> : coachInitials(name)}
    </span>
  )
}
