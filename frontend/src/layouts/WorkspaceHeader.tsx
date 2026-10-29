import { useEffect, useRef, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { ThemeToggle } from '../theme/ThemeToggle'
import { UserMenu } from './UserMenu'

export type WorkspaceHeaderVariant = 'member' | 'staff'

export interface WorkspaceHeaderProps {
  variant: WorkspaceHeaderVariant
  menuId: string
  menuOpen: boolean
  onMenuChange: (open: boolean) => void
  menuLabel?: string
  navigation?: ReactNode
  context?: ReactNode
  className?: string
}

/** Shared Home-branded header for authenticated role workspaces. */
export function WorkspaceHeader({
  variant,
  menuId,
  menuOpen,
  onMenuChange,
  menuLabel = variant === 'member' ? 'member' : 'workspace',
  navigation,
  context,
  className = '',
}: WorkspaceHeaderProps) {
  const headerRef = useRef<HTMLElement>(null)
  const menuToggleRef = useRef<HTMLButtonElement>(null)
  const location = useLocation()
  const routeKey = [location.pathname, location.search, location.hash].join('|')
  const previousRoute = useRef(routeKey)

  useEffect(() => {
    const header = headerRef.current
    const parent = header?.parentElement
    if (!header || !parent) return

    const previousHomeHeight = parent.style.getPropertyValue('--home-header-height')
    const previousWorkspaceHeight = parent.style.getPropertyValue('--workspace-header-height')
    const measure = () => {
      const height = Math.ceil(header.getBoundingClientRect().height) + 'px'
      parent.style.setProperty('--home-header-height', height)
      parent.style.setProperty('--workspace-header-height', height)
    }
    measure()

    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure)
    observer?.observe(header)
    return () => {
      observer?.disconnect()
      if (previousHomeHeight) parent.style.setProperty('--home-header-height', previousHomeHeight)
      else parent.style.removeProperty('--home-header-height')
      if (previousWorkspaceHeight) parent.style.setProperty('--workspace-header-height', previousWorkspaceHeight)
      else parent.style.removeProperty('--workspace-header-height')
    }
  }, [])

  useEffect(() => {
    if (previousRoute.current === routeKey) return
    previousRoute.current = routeKey
    onMenuChange(false)
  }, [onMenuChange, routeKey])

  useEffect(() => {
    if (!menuOpen) return

    const closeFromOutside = (event: PointerEvent) => {
      const target = event.target as Node
      const menu = document.getElementById(menuId)
      if (!headerRef.current?.contains(target) && !menu?.contains(target)) onMenuChange(false)
    }
    const closeWithEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      onMenuChange(false)
      menuToggleRef.current?.focus()
    }

    document.addEventListener('pointerdown', closeFromOutside)
    document.addEventListener('keydown', closeWithEscape)
    return () => {
      document.removeEventListener('pointerdown', closeFromOutside)
      document.removeEventListener('keydown', closeWithEscape)
    }
  }, [menuId, menuOpen, onMenuChange])

  const headerClasses = [
    'home-page',
    'home-header',
    'workspace-header',
    'workspace-header--' + variant,
    variant === 'member' ? 'member-layout__header' : 'staff-topbar',
    className,
  ].filter(Boolean).join(' ')

  return (
    <header ref={headerRef} className={headerClasses}>
      <div className="home-wrap home-header__bar workspace-header__bar member-header__bar">
        <Brand compact />
        {context}
        <button
          ref={menuToggleRef}
          type="button"
          className="home-menu-toggle workspace-header__menu-toggle"
          aria-controls={menuId}
          aria-expanded={menuOpen}
          aria-label={(menuOpen ? 'Close ' : 'Open ') + menuLabel + ' navigation'}
          onClick={() => onMenuChange(!menuOpen)}
        >
          {menuOpen ? 'Close' : 'Menu'}
        </button>
        <div className="home-account workspace-header__account">
          <ThemeToggle className="home-theme" />
          <UserMenu />
        </div>
      </div>
      {navigation}
    </header>
  )
}
