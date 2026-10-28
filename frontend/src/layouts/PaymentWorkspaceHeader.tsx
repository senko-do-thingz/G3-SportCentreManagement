import { useEffect, useRef, type ReactNode } from 'react'
import { Brand } from '../components/Brand'
import { ThemeToggle } from '../theme/ThemeToggle'
import { UserMenu } from './UserMenu'

interface PaymentWorkspaceHeaderProps {
  menuId: string
  menuOpen: boolean
  onMenuChange: (open: boolean) => void
  navigation?: ReactNode
}

/** The actual Home compact Brand, header primitives and ThemeToggle, with role-specific navigation. */
export function PaymentWorkspaceHeader({ menuId, menuOpen, onMenuChange, navigation }: PaymentWorkspaceHeaderProps) {
  const toggle = useRef<HTMLButtonElement>(null)
  const header = useRef<HTMLElement>(null)
  useEffect(() => {
    const element = header.current
    const parent = element?.parentElement
    if (!element || !parent) return
    const old = parent.style.getPropertyValue('--home-header-height')
    const measure = () => parent.style.setProperty('--home-header-height', `${element.getBoundingClientRect().height}px`)
    measure()
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure)
    observer?.observe(element)
    return () => {
      observer?.disconnect()
      if (old) parent.style.setProperty('--home-header-height', old)
      else parent.style.removeProperty('--home-header-height')
    }
  }, [])
  useEffect(() => {
    const close = (event: PointerEvent) => {
      const target = event.target as Node
      if (menuOpen && !header.current?.contains(target) && !document.getElementById(menuId)?.contains(target)) onMenuChange(false)
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [menuId, menuOpen, onMenuChange])
  return (
    <header ref={header} className="home-header home-header--solid payment-workspace-header" onKeyDown={(event) => {
      if (event.key === 'Escape' && menuOpen) {
        event.preventDefault()
        onMenuChange(false)
        toggle.current?.focus()
      }
    }}>
      <div className="home-wrap home-header__bar">
        <Brand compact />
        <button ref={toggle} type="button" className="home-menu-toggle" aria-controls={menuId}
          aria-expanded={menuOpen} onClick={() => onMenuChange(!menuOpen)}>Menu</button>
        <div className="home-account">
          <ThemeToggle className="home-theme" />
          <UserMenu />
        </div>
      </div>
      {navigation}
    </header>
  )
}
