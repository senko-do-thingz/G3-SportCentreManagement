import { useRef, useState, type KeyboardEvent } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { AccountSwitchNotice } from '../components/AccountSwitchNotice'
import { areaItems, itemHref, MEMBER_AREA } from './navigation'
import { WorkspaceHeaderBar } from './WorkspaceHeaderBar'

/** Member area: shared workspace header with a role-specific, keyboard-accessible tab menu. */
export function MemberLayout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const menuToggleRef = useRef<HTMLButtonElement>(null)

  const handleHeaderKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape' && menuOpen) {
      setMenuOpen(false)
      menuToggleRef.current?.focus()
    }
  }

  const menuToggle = (
    <button
      ref={menuToggleRef}
      type="button"
      className="workspace-header__menu-toggle"
      aria-expanded={menuOpen}
      aria-controls="member-navigation"
      aria-label={`${menuOpen ? 'Close' : 'Open'} member navigation`}
      onClick={() => setMenuOpen((open) => !open)}
    >
      {menuOpen ? 'Close' : 'Menu'}
    </button>
  )

  return (
    <div className="member-layout">
      <header className="workspace-header member-layout__header" onKeyDown={handleHeaderKeyDown}>
        <WorkspaceHeaderBar menuToggle={menuToggle} />
        <nav
          className={`member-nav${menuOpen ? ' member-nav--open' : ''}`}
          id="member-navigation"
          aria-label="Member navigation"
        >
          <div className="member-nav__list container">
            {areaItems(MEMBER_AREA).map((item) => (
              <NavLink
                key={item.label}
                to={itemHref(MEMBER_AREA, item)}
                end={item.path === ''}
                onClick={() => setMenuOpen(false)}
                className={({ isActive }) => `member-nav__link${isActive ? ' member-nav__link--active' : ''}`}
              >
                {item.label}
              </NavLink>
            ))}
          </div>
        </nav>
      </header>
      <main className="member-main container" id="main-content">
        <AccountSwitchNotice />
        <Outlet />
      </main>
    </div>
  )
}
