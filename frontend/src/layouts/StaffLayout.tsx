import { useRef, useState, type KeyboardEvent } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { AccountSwitchNotice } from '../components/AccountSwitchNotice'
import { useAuth } from '../auth/useAuth'
import { findActiveItem, itemHref, STAFF_AREAS } from './navigation'
import { WorkspaceHeaderBar } from './WorkspaceHeaderBar'

/** Workspace for RECEPTIONIST, MANAGER and COACH with role-specific navigation. */
export function StaffLayout() {
  const { role } = useAuth()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuToggleRef = useRef<HTMLButtonElement>(null)

  // RequireRole guarantees a staff role here; fall back to the manager menu for type safety only.
  const area = role && role !== 'MEMBER' ? STAFF_AREAS[role] : STAFF_AREAS.MANAGER
  const activeItem = findActiveItem(area, location.pathname)

  const handleSidebarKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape' && menuOpen) {
      setMenuOpen(false)
      menuToggleRef.current?.focus()
    }
  }

  return (
    <div className={`staff-layout${menuOpen ? ' staff-layout--menu-open' : ''}`}>
      <header className="workspace-header staff-layout__header">
        <WorkspaceHeaderBar />
      </header>
      <aside className="sidebar" aria-label={area.workspaceLabel} onKeyDown={handleSidebarKeyDown}>
        <div className="sidebar__top">
          <p className="sidebar__workspace">{area.workspaceLabel}</p>
          <button
            ref={menuToggleRef}
            type="button"
            className="sidebar__toggle"
            aria-expanded={menuOpen}
            aria-controls="staff-menu"
            aria-label={`${menuOpen ? 'Close' : 'Open'} ${area.workspaceLabel} navigation`}
            onClick={() => setMenuOpen((open) => !open)}
          >
            {menuOpen ? 'Close' : 'Menu'}
          </button>
        </div>
        <nav className="sidebar__nav" id="staff-menu" aria-label={`${area.workspaceLabel} navigation`}>
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
          <p className="sidebar__footer-text">One center. More possibilities.</p>
        </div>
      </aside>
      <div className="staff-main">
        <nav className="staff-topbar" aria-label="Breadcrumb">
          <ol className="staff-topbar__breadcrumb">
            <li><Link to="/">Sportify Center</Link></li>
            <li><span aria-current="page">{activeItem?.title ?? 'Workspace'}</span></li>
          </ol>
        </nav>
        <main className="staff-content" id="main-content">
          <AccountSwitchNotice />
          <Outlet />
        </main>
      </div>
    </div>
  )
}
