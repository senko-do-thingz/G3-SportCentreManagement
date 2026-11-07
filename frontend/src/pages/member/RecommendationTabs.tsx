import { Link, useLocation } from 'react-router-dom'
import { buttonClassName } from '../../components/ui'

/** Flow 5 Member tabs from the design: My goals, Recommendations, Saved recommendations. */
export function RecommendationTabs() {
  const location = useLocation()
  const tab = location.pathname.startsWith('/member/training/goals') ? 'goals'
    : location.pathname === '/member/training/recommendations' && location.hash === '#saved' ? 'saved'
      : location.pathname === '/member/training/recommendations' ? 'recommendations' : ''
  const link = (id: string, to: string, label: string) => <Link to={to} aria-current={tab === id ? 'page' : undefined}
    className={buttonClassName(tab === id ? 'primary' : 'secondary', 'sm')}>{label}</Link>
  return <nav className="training-actions" aria-label="Training recommendations">
    {link('goals', '/member/training/goals', 'My goals')}
    {link('recommendations', '/member/training/recommendations', 'Recommendations')}
    {link('saved', '/member/training/recommendations#saved', 'Saved recommendations')}
  </nav>
}
