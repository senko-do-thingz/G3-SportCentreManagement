import { NavLink } from 'react-router-dom'

/** Switches between the two Training screens, in the same style as the My Schedule tabs. */
export function MemberTrainingNav() {
  const tab = ({ isActive }: { isActive: boolean }) => `browse-tab${isActive ? ' browse-tab--active' : ''}`
  return (
    <nav className="browse-nav" aria-label="Training">
      <NavLink to="/member/training" end className={tab}>Plans and coach notes</NavLink>
      <NavLink to="/member/training/history" className={tab}>Attendance and results</NavLink>
    </nav>
  )
}
