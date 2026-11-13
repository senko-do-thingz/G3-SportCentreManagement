import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useMatch } from 'react-router-dom'
import { AccountSwitchNotice } from '../components/AccountSwitchNotice'
import { useAuth } from '../auth/useAuth'
import { findActiveItem, itemHref, STAFF_AREAS } from './navigation'
import { PaymentWorkspaceHeader } from './PaymentWorkspaceHeader'
import { WorkspaceHeader } from './WorkspaceHeader'
import { WorkflowSurface } from '../components/WorkflowSurface'
import { FloatingSporiChat } from '../pages/member/FloatingSporiChat'

/** Coach, reception and manager workspaces share a Home-branded header and role-aware sidebar. */
export function StaffLayout() {
  const { role } = useAuth()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const refundRoute = useMatch('/reception/refunds/*')
  const paymentWorkspaceRoute = Boolean(refundRoute) || location.pathname === '/reception/payments'
  const workflowRoute = location.pathname === '/manager/schedule'
    || /^\/(coach|reception)\/training(?:\/|$)/.test(location.pathname)
    || /^\/reception\/payments\/[^/]+\/(qr-display|receipt)$/.test(location.pathname)

  // RequireRole guarantees a staff role here; fall back to the manager menu for type safety only.
  const area = role && role !== 'MEMBER' ? STAFF_AREAS[role] : STAFF_AREAS.MANAGER
  const activeItem = findActiveItem(area, location.pathname)
  const menuId = 'staff-menu'

  const breadcrumb = (
    <nav className="workspace-header__context" aria-label="Breadcrumb">
      <ol className="workspace-header__breadcrumb">
        <li><Link to="/">Sportify Center</Link></li>
        <li><span aria-current="page">{activeItem?.title ?? 'Workspace'}</span></li>
      </ol>
    </nav>
  )

  return (
    <div className={'staff-layout' + (menuOpen ? ' staff-layout--menu-open' : '') + (paymentWorkspaceRoute ? ' payment-area' : '')}>
      {paymentWorkspaceRoute
        ? <PaymentWorkspaceHeader menuId={menuId} menuOpen={menuOpen} onMenuChange={setMenuOpen} menuLabel={area.workspaceLabel} />
        : <WorkspaceHeader variant="staff" menuId={menuId} menuOpen={menuOpen} onMenuChange={setMenuOpen} menuLabel={area.workspaceLabel} context={breadcrumb} />}
      <aside className="sidebar" aria-label={area.workspaceLabel}>
        <div className="sidebar__top">
          <p className="sidebar__workspace">{area.workspaceLabel}</p>
        </div>
        <nav className="sidebar__nav" id={menuId} aria-label={area.workspaceLabel + ' navigation'}>
          {area.sections.map((section, sectionIndex) => (
            <div className="sidebar__section" key={section.heading ?? sectionIndex}>
              {section.heading && <p className="sidebar__heading">{section.heading}</p>}
              {section.items.map((item) => (
                <NavLink
                  key={item.label}
                  to={itemHref(area, item)}
                  end={item.path === ''}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) => 'sidebar__link' + (isActive ? ' sidebar__link--active' : '')}
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
        <main className="staff-content" id="main-content">
          <AccountSwitchNotice />
          {workflowRoute ? <WorkflowSurface><Outlet /></WorkflowSurface> : <Outlet />}
        </main>
      </div>
      {/* Same Spori chat as Members; the server limits answers to this staff role's data. */}
      <FloatingSporiChat />
    </div>
  )
}
