import { NavLink, Outlet, useMatch } from 'react-router-dom'
import { useState } from 'react'
import { AccountSwitchNotice } from '../components/AccountSwitchNotice'
import { areaItems, itemHref, MEMBER_AREA } from './navigation'
import { PaymentWorkspaceHeader } from './PaymentWorkspaceHeader'
import { WorkspaceHeader } from './WorkspaceHeader'

/** Member workspace with a Home-branded header and role-specific tab navigation. */
export function MemberLayout() {
  const summary = useMatch('/member/payments/:paymentId')
  const status = useMatch('/member/payments/:paymentId/status')
  const qrDemo = useMatch('/member/payments/:paymentId/qr-demo')
  const paymentRoute = Boolean(summary || status || qrDemo)
  const [menuOpen, setMenuOpen] = useState(false)

  const navigation = (
    <nav
      className={'home-nav workspace-header__nav' + (menuOpen ? ' workspace-header__nav--open' : '')}
      aria-label="Member navigation"
      id="member-navigation"
    >
      {areaItems(MEMBER_AREA).map((item) => (
        <NavLink
          key={item.label}
          to={itemHref(MEMBER_AREA, item)}
          end={item.path === ''}
          onClick={() => setMenuOpen(false)}
          className={({ isActive }) => 'workspace-header__nav-link' + (isActive ? ' workspace-header__nav-link--active' : '')}
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  )

  const headerProps = {
    variant: 'member' as const,
    menuId: 'member-navigation',
    menuOpen,
    onMenuChange: setMenuOpen,
    menuLabel: 'member',
    navigation,
  }

  return (
    <div className={'member-layout' + (paymentRoute ? ' home-page payment-area' : '')}>
      {paymentRoute ? <PaymentWorkspaceHeader {...headerProps} /> : <WorkspaceHeader {...headerProps} />}
      <main className="member-main container" id="main-content">
        <AccountSwitchNotice />
        <Outlet />
      </main>
    </div>
  )
}
