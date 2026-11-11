import '@fontsource/be-vietnam-pro/700.css'
import '@fontsource/be-vietnam-pro/800.css'
import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useMatch } from 'react-router-dom'
import { AccountSwitchNotice } from '../components/AccountSwitchNotice'
import { MemberAssistantLink } from '../pages/member/MemberAssistantPage'
import { FloatingSporiChat } from '../pages/member/FloatingSporiChat'
import { TrainingNavigation } from '../pages/member/TrainingNavigation'
import { findActiveItem, itemHref, MEMBER_AREA } from './navigation'
import { PaymentWorkspaceHeader } from './PaymentWorkspaceHeader'
import { WorkspaceHeader } from './WorkspaceHeader'
import './memberConceptB.css'

/** Member-only Concept B shell. Shared staff headers and their styles remain untouched. */
export function MemberLayout() {
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia?.('(min-width: 960px)').matches ?? window.innerWidth >= 960)
  const navigationRef = useRef<HTMLElement>(null)
  const current = findActiveItem(MEMBER_AREA, location.pathname)
  const paymentSummaryRoute = useMatch('/member/payments/:paymentId')
  const paymentStatusRoute = useMatch('/member/payments/:paymentId/status')
  const paymentQrRoute = useMatch('/member/payments/:paymentId/qr-demo')
  const paymentRoute = Boolean(paymentSummaryRoute || paymentStatusRoute || paymentQrRoute)
  const inTraining = location.pathname === '/member/training' || location.pathname.startsWith('/member/training/')
  const onAssistant = location.pathname === '/member/assistant'

  useEffect(() => {
    const media = window.matchMedia?.('(min-width: 960px)')
    if (!media) return
    const update = () => setIsDesktop(media.matches)
    update()
    media.addEventListener?.('change', update)
    return () => media.removeEventListener?.('change', update)
  }, [])

  useEffect(() => {
    if (!menuOpen || isDesktop) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    navigationRef.current?.querySelector<HTMLAnchorElement>('a[href]')?.focus()
    const keepFocusInNavigation = (event: KeyboardEvent) => {
      if (event.key !== 'Tab') return
      const links = Array.from(navigationRef.current?.querySelectorAll<HTMLAnchorElement>('a[href]') ?? [])
      const menuButton = document.querySelector<HTMLButtonElement>('.member-concept .workspace-header__menu-toggle')
      const focusable: HTMLElement[] = [...(menuButton ? [menuButton] : []), ...links]
      if (focusable.length === 0) return
      const currentIndex = focusable.indexOf(document.activeElement as HTMLElement)
      const nextIndex = event.shiftKey ? currentIndex - 1 : currentIndex + 1
      if (nextIndex < 0) {
        event.preventDefault()
        focusable.at(-1)?.focus()
      } else if (nextIndex >= focusable.length) {
        event.preventDefault()
        focusable[0]?.focus()
      }
    }
    document.addEventListener('keydown', keepFocusInNavigation)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', keepFocusInNavigation)
    }
  }, [isDesktop, menuOpen])

  const menuVisible = isDesktop || menuOpen
  const closeNavigation = () => {
    setMenuOpen(false)
    if (!isDesktop) document.querySelector<HTMLButtonElement>('.member-concept .workspace-header__menu-toggle')?.focus()
  }
  const navigation = (
    <aside
      ref={navigationRef}
      id="member-navigation"
      className="member-concept__sidebar"
      aria-label="Member workspace navigation"
      aria-hidden={!menuVisible}
    >
      <div className="member-concept__sidebar-label">Member workspace</div>
      <nav className="member-concept__nav" aria-label="Member navigation">
        {MEMBER_AREA.sections.map((section) => (
          <section className="member-concept__nav-section" key={section.heading} aria-label={section.heading}>
            {section.heading && section.heading.toLowerCase() !== MEMBER_AREA.workspaceLabel.toLowerCase() && <h2 className="member-concept__nav-heading">{section.heading}</h2>}
            <div className="member-concept__nav-links">
              {section.items.map((item) => (
                <NavLink
                  key={item.path || item.label}
                  to={itemHref(MEMBER_AREA, item)}
                  end={item.path === ''}
                  onClick={closeNavigation}
                  className={({ isActive }) => {
                    const trainingActive = item.path === 'training' && inTraining
                    return 'member-concept__nav-link' + (isActive || trainingActive ? ' member-concept__nav-link--active' : '')
                  }}
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          </section>
        ))}
      </nav>
      <div className="member-concept__sidebar-utility">
        <MemberAssistantLink className="member-concept__sidebar-assistant" label="Open center assistant" onNavigate={closeNavigation} />
      </div>
      <footer className="member-concept__sidebar-footer">
        <strong>Sportify Center</strong>
        <span>One center. More possibilities.</span>
      </footer>
    </aside>
  )

  const breadcrumb = (
    <nav className="workspace-header__context" aria-label="Breadcrumb">
      <div className="workspace-header__breadcrumb">
        <Link to="/">Sportify Center</Link>
        <span aria-current="page">{current?.title ?? 'Member workspace'}</span>
      </div>
    </nav>
  )
  const headerProps = {
    variant: 'member' as const,
    menuId: 'member-navigation',
    menuOpen,
    onMenuChange: setMenuOpen,
    menuLabel: 'member',
  }

  return (
    <div className={'member-layout member-concept' + (menuOpen ? ' member-layout--menu-open' : '') + (paymentRoute ? ' home-page payment-area' : '')}>
      {paymentRoute
        ? <PaymentWorkspaceHeader {...headerProps} />
        : <WorkspaceHeader {...headerProps} context={breadcrumb} />}
      {!isDesktop && menuOpen && (
        <button className="member-concept__scrim" type="button" aria-label="Close member navigation" onClick={closeNavigation} />
      )}
      <div className="member-concept__workspace">
        {navigation}
        <main className="member-main member-concept__main" id="main-content" tabIndex={-1}>
          <AccountSwitchNotice />
          <div className="member-concept__content">
            {inTraining && <TrainingNavigation />}
            <Outlet />
          </div>
        </main>
      </div>
      {!onAssistant && <FloatingSporiChat isDesktop={isDesktop} />}
    </div>
  )
}
