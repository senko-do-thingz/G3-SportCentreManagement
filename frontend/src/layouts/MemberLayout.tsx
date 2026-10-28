import { NavLink, Outlet, useMatch } from 'react-router-dom'
import { useState } from 'react'
import { AccountSwitchNotice } from '../components/AccountSwitchNotice'
import { Brand } from '../components/Brand'
import { areaItems, itemHref, MEMBER_AREA } from './navigation'
import { UserMenu } from './UserMenu'
import { AppearanceControl } from '../theme/AppearanceControl'
import { PaymentWorkspaceHeader } from './PaymentWorkspaceHeader'

/** Member area: brand and user on top, tab navigation below (Figma F1-07, F1-09). */
export function MemberLayout() {
  const summary = useMatch('/member/payments/:paymentId')
  const status = useMatch('/member/payments/:paymentId/status')
  const qrDemo = useMatch('/member/payments/:paymentId/qr-demo')
  const paymentRoute = Boolean(summary || status || qrDemo)
  const [menuOpen, setMenuOpen] = useState(false)
  const navigation = (
    <nav className={`member-nav${paymentRoute ? ` payment-workspace-navigation${menuOpen ? ' payment-workspace-navigation--open' : ''}` : ''}`} aria-label="Member navigation" id="member-navigation">
      <div className="member-nav__list container">
        {areaItems(MEMBER_AREA).map((item) => (
          <NavLink key={item.label} to={itemHref(MEMBER_AREA, item)} end={item.path === ''}
            onClick={() => setMenuOpen(false)} className={({ isActive }) => `member-nav__link${isActive ? ' member-nav__link--active' : ''}`}>
            {item.label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
  return (
    <div className={`member-layout${paymentRoute ? ' home-page payment-area' : ''}`}>
      {paymentRoute ? <PaymentWorkspaceHeader menuId="member-navigation" menuOpen={menuOpen} onMenuChange={setMenuOpen} navigation={navigation} /> : <header className="member-layout__header">
        <div className="member-header__bar container">
          <Brand />
          <div className="header-actions"><AppearanceControl /><UserMenu /></div>
        </div>
        {navigation}
      </header>}
      <main className="member-main container" id="main-content">
        <AccountSwitchNotice />
          <Outlet />
      </main>
    </div>
  )
}
