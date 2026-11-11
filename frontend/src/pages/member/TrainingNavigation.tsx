import { useEffect, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'

const primaryLinks = [
  { path: '', label: 'Overview', end: true },
  { path: 'classes', label: 'My classes' },
  { path: 'plans', label: 'Training plan' },
  { path: 'results', label: 'Progress' },
  { path: 'feedback', label: 'Coach feedback' },
] as const

const additionalLinks = [
  { path: 'questions', label: 'Ask your coach' },
  { path: 'attendance', label: 'Attendance history' },
  { path: 'recommendations', label: 'Class recommendations' },
  { path: 'goals', label: 'Training preferences' },
] as const

const additionalPaths = additionalLinks.map(link => `/member/training/${link.path}`)
const mobileLinks = [
  { path: '', label: 'Overview', end: true },
  { path: 'classes', label: 'My classes' },
  { path: 'questions', label: 'Ask your coach' },
  { path: 'plans', label: 'Training plans' },
  { path: 'feedback', label: 'Coach feedback' },
  { path: 'attendance', label: 'Attendance history' },
  { path: 'results', label: 'Training results' },
  { path: 'recommendations', label: 'Class recommendations' },
  { path: 'goals', label: 'Training preferences' },
] as const

function isInAdditionalTools(pathname: string): boolean {
  return additionalPaths.some(path => pathname === path || pathname.startsWith(`${path}/`))
}

function TrainingNavLink({ path, label, end }: { path: string; label: string; end?: boolean }) {
  return (
    <NavLink to={path ? `/member/training/${path}` : '/member/training'} end={end}>
      {label}
    </NavLink>
  )
}

/** Keep the most-used training areas visible and group secondary routes without removing them. */
export function TrainingNavigation() {
  const { pathname } = useLocation()
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia?.('(min-width: 960px)').matches ?? window.innerWidth >= 960)
  const additionalActive = isInAdditionalTools(pathname)

  useEffect(() => {
    const media = window.matchMedia?.('(min-width: 960px)')
    if (!media) return
    const update = () => setIsDesktop(media.matches)
    update()
    media.addEventListener?.('change', update)
    return () => media.removeEventListener?.('change', update)
  }, [])

  return (
    <nav className="training-navigation" aria-label="Training navigation">
      {isDesktop ? (
        <>
          <div className="training-navigation__primary">
            {primaryLinks.map(item => <TrainingNavLink key={item.path || 'overview'} {...item} />)}
          </div>
          <details className="training-navigation__more" open={additionalActive}>
            <summary aria-current={additionalActive ? 'page' : undefined}>
              More tools <span aria-hidden="true">+</span>
            </summary>
            <div className="training-navigation__more-menu" role="group" aria-label="Additional training tools">
              {additionalLinks.map(item => <TrainingNavLink key={item.path} {...item} />)}
            </div>
          </details>
        </>
      ) : mobileLinks.map(item => <TrainingNavLink key={item.path || 'overview'} {...item} />)}
    </nav>
  )
}
