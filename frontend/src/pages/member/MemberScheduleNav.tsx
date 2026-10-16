import { NavLink } from 'react-router-dom'
import { tokenStorage } from '../../api/tokenStorage'
import { managerPreviewEnabled } from '../../config/env'

export function MemberScheduleNav() {
  return <>{managerPreviewEnabled() && tokenStorage.getAccessToken() === 'preview-MEMBER' && <p className="notice notice--warning" role="status">Frontend member preview - sample data only. Browsing never changes backend data.</p>}<nav className="browse-nav" aria-label="Classes and schedule"><NavLink to="/member/schedule" className={({ isActive }) => `browse-tab${isActive ? ' browse-tab--active' : ''}`}>View schedule</NavLink><NavLink to="/member/classes" className={({ isActive }) => `browse-tab${isActive ? ' browse-tab--active' : ''}`}>Find classes</NavLink><NavLink to="/member/bookings" className={({ isActive }) => `browse-tab${isActive ? ' browse-tab--active' : ''}`}>My Bookings</NavLink></nav></>
}
