import { NavLink } from 'react-router-dom'

export function MemberScheduleNav() {
  return <nav className="browse-nav" aria-label="Classes and schedule"><NavLink to="/member/schedule" className={({ isActive }) => `browse-tab${isActive ? ' browse-tab--active' : ''}`}>View schedule</NavLink><NavLink to="/member/classes" className={({ isActive }) => `browse-tab${isActive ? ' browse-tab--active' : ''}`}>Find classes</NavLink><span className="browse-tab browse-tab--disabled" aria-disabled="true">My bookings (coming soon)</span></nav>
}
