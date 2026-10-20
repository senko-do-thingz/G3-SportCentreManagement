import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { AccountSwitchNotice } from '../components/AccountSwitchNotice'
import { useAuth } from '../auth/useAuth'
import { Brand } from '../components/Brand'
import { findActiveItem, itemHref, STAFF_AREAS } from './navigation'
import { UserMenu } from './UserMenu'
import { AppearanceControl } from '../theme/AppearanceControl'

/**
 * Workspace for RECEPTIONIST, MANAGER and COACH: side navigation with the menu of the signed in
 * role, a top bar with breadcrumb and user (Figma F1-01, F1-15, F2-13).
 */
export function StaffLayout() {
  const { role } = useAuth()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)

  // RequireRole guarantees a staff role here; fall back to the manager menu for type safety only.
  const area = role && role !== 'MEMBER' ? STAFF_AREAS[role] : STAFF_AREAS.MANAGER
  const activeItem = findActiveItem(area, location.pathname)

  return (
    <div className={`staff-layout${menuOpen ? ' staff-layout--menu-open' : ''}`}>
      <aside className="sidebar" aria-label={area.workspaceLabel}>
        <div className="sidebar__brand">
          <Brand />
          <button
            type="button"
            className="sidebar__toggle"
            aria-expanded={menuOpen}
            aria-controls="staff-menu"
            onClick={() => setMenuOpen((open) => !open)}
          >
            Menu
          </button>
        </div>
        <nav className="sidebar__nav" id="staff-menu">
          <p className="sidebar__workspace">{area.workspaceLabel}</p>
          {area.sections.map((section, sectionIndex) => (
            <div className="sidebar__section" key={section.heading ?? sectionIndex}>
              {section.heading && <p className="sidebar__heading">{section.heading}</p>}
              {section.items.map((item) => (
                <NavLink
                  key={item.label}
                  to={itemHref(area, item)}
                  end={item.path === ''}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) => `sidebar__link${isActive ? ' sidebar__link--active' : ''}`}
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar__footer">
          <p className="sidebar__footer-title">Sportify Center</p>
          <p className="sidebar__footer-text">One center - Six sports</p>
        </div>
      </aside>
      <div className="staff-main">
        <header className="staff-topbar">
          <p className="staff-topbar__breadcrumb">
            Sportify Center / <span>{activeItem?.title ?? 'Workspace'}</span>
          </p>
          <div className="header-actions"><AppearanceControl /><UserMenu /></div>
        </header>
        <main className="staff-content" id="main-content">
          <AccountSwitchNotice />
          <Outlet />
        </main>
      </div>
    </div>
  )
}
