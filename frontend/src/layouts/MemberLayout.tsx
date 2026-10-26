import { NavLink, Outlet } from 'react-router-dom'
import { AccountSwitchNotice } from '../components/AccountSwitchNotice'
import { Brand } from '../components/Brand'
import { areaItems, itemHref, MEMBER_AREA } from './navigation'
import { UserMenu } from './UserMenu'
import { ThemeToggle } from '../theme/ThemeToggle'

/** Member area: brand and user on top, tab navigation below (Figma F1-07, F1-09). */
export function MemberLayout() {
  return (
    <div className="member-layout">
      <header className="member-layout__header">
        <div className="member-header__bar container">
          <Brand />
          <div className="header-actions"><ThemeToggle className="member-theme" /><UserMenu /></div>
        </div>
        <nav className="member-nav" aria-label="Member navigation">
          <div className="member-nav__list container">
            {areaItems(MEMBER_AREA).map((item) => (
              <NavLink
                key={item.label}
                to={itemHref(MEMBER_AREA, item)}
                end={item.path === ''}
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
