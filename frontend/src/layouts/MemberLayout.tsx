import { useRef, useState, type KeyboardEvent } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { AccountSwitchNotice } from '../components/AccountSwitchNotice'
import { areaItems, itemHref, MEMBER_AREA } from './navigation'
import { WorkspaceHeaderBar } from './WorkspaceHeaderBar'

/** Member area with Home-style workspace navigation and keyboard-accessible mobile menu. */
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
      className="home-menu-toggle workspace-header__menu-toggle"
      aria-expanded={menuOpen}
      aria-controls="member-navigation"
      aria-label={menuOpen ? 'Close member navigation' : 'Open member navigation'}
      onClick={() => setMenuOpen((open) => !open)}
    >
      {menuOpen ? 'Close' : 'Menu'}
    </button>
  )

  const navigation = (
    <nav
      className={menuOpen ? 'home-nav workspace-header__nav workspace-header__nav--open' : 'home-nav workspace-header__nav'}
      id="member-navigation"
      aria-label="Member navigation"
    >
      {areaItems(MEMBER_AREA).map((item) => (
        <NavLink
          key={item.label}
          to={itemHref(MEMBER_AREA, item)}
          end={item.path === ''}
          onClick={() => setMenuOpen(false)}
          className={({ isActive }) => isActive ? 'workspace-header__nav-link workspace-header__nav-link--active' : 'workspace-header__nav-link'}
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  )

  return (
    <div className="member-layout">
      <header className="workspace-header" onKeyDown={handleHeaderKeyDown}>
        <WorkspaceHeaderBar menuToggle={menuToggle} navigation={navigation} />
      </header>
      <main className="member-main container" id="main-content">
        <AccountSwitchNotice />
        <Outlet />
      </main>
    </div>
  )
}
