import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { homePathFor } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { Brand } from '../../components/Brand'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { UserMenu } from '../../layouts/UserMenu'
import { ThemeToggle } from '../../theme/ThemeToggle'

export interface HomeNavGroup {
  label: string
  href: string
  children: { label: string; href: string }[]
}

export function HomeHeader({ groups = [] }: { groups?: HomeNavGroup[] }) {
  const { status, user } = useAuth()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const [submenu, setSubmenu] = useState<string | null>(null)
  const [scrolled, setScrolled] = useState(false)
  const toggle = useRef<HTMLButtonElement>(null)
  const header = useRef<HTMLElement>(null)
  const sentinel = useRef<HTMLSpanElement>(null)
  const pointerFocus = useRef(false)

  useEffect(() => {
    const element = header.current
    const marker = sentinel.current
    if (!element || !marker) return
    const measure = () => element.parentElement?.style.setProperty('--home-header-height', `${element.getBoundingClientRect().height}px`)
    measure()
    const resize = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure)
    resize?.observe(element)
    const observer = typeof IntersectionObserver === 'undefined' ? null : new IntersectionObserver(([entry]) => setScrolled(!entry.isIntersecting && entry.boundingClientRect.top < 0))
    observer?.observe(marker)
    return () => { resize?.disconnect(); observer?.disconnect() }
  }, [])

  useEffect(() => {
    const close = (event: PointerEvent) => {
      if (!header.current?.contains(event.target as Node)) { setOpen(false); setSubmenu(null) }
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [])

  const route = `${location.pathname}${location.search}${location.hash}`
  const [previousRoute, setPreviousRoute] = useState(route)
  if (previousRoute !== route) {
    setPreviousRoute(route)
    setOpen(false)
    setSubmenu(null)
  }
  function close() { setOpen(false); setSubmenu(null) }
  function followSection(event: MouseEvent<HTMLAnchorElement>) {
    const target = document.getElementById(event.currentTarget.hash.slice(1))
    if (target) { target.tabIndex = -1; target.focus({ preventScroll: true }) }
    close()
  }

  return (
    <>
    <span ref={sentinel} className="home-header-sentinel" aria-hidden="true" />
    <header ref={header} className={`home-header${scrolled || open ? ' home-header--solid' : ''}`}>
      <div className="home-wrap home-header__bar">
        <Brand compact />
        <button
          ref={toggle}
          type="button"
          className="home-menu-toggle"
          aria-controls="home-navigation"
          aria-expanded={open}
          onClick={() => { setOpen((value) => !value); setSubmenu(null) }}
        >
          Menu
        </button>
        <nav
          id="home-navigation"
          aria-label="Home navigation"
          className={`home-nav${open ? ' home-nav--open' : ''}`}
          onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget as Node)) close() }}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault()
              if (submenu) {
                const trigger = header.current?.querySelector<HTMLButtonElement>(`[aria-controls="home-submenu-${submenu}"]`)
                setSubmenu(null)
                trigger?.focus()
              } else if (open) { close(); toggle.current?.focus() }
            }
          }}
        >
          <Link to="/" aria-current="page" onClick={close}>Home</Link>
          {groups.map((group) => {
            const expanded = submenu === group.label
            const id = `home-submenu-${group.label}`
            return <div className="home-nav__group" key={group.label}
              onPointerEnter={(event) => { if (event.pointerType === 'mouse' && group.children.length) setSubmenu(group.label) }}
              onPointerLeave={(event) => { if (event.pointerType === 'mouse') setSubmenu((current) => current === group.label ? null : current) }}
              onPointerDown={() => { pointerFocus.current = true }}
              onFocus={(event) => {
                if (!pointerFocus.current && !event.currentTarget.contains(event.relatedTarget as Node) && group.children.length) setSubmenu(group.label)
              }}
              onBlur={(event) => {
                pointerFocus.current = false
                if (!event.currentTarget.contains(event.relatedTarget as Node)) setSubmenu((current) => current === group.label ? null : current)
              }}>
              <div className="home-nav__parent">
                <a href={group.href} onClick={followSection}>{group.label}</a>
                {group.children.length > 0 && <button type="button" className="home-nav__toggle" aria-label={`Toggle ${group.label} destinations`} aria-expanded={expanded} aria-controls={id}
                  onClick={() => { pointerFocus.current = false; setSubmenu(expanded ? null : group.label) }}>
                  <span aria-hidden="true">{expanded ? '-' : '+'}</span>
                </button>}
              </div>
              {group.children.length > 0 && <ul id={id} className="home-nav__submenu" hidden={!expanded} aria-label={`${group.label} destinations`}>
                {group.children.map((child) => <li key={child.href}><a href={child.href} onClick={followSection}>{child.label}</a></li>)}
              </ul>}
            </div>
          })}
        </nav>
        <div className="home-account">
          <ThemeToggle className="home-theme" />
          {status === 'authenticated' ? (
            <>
              <Link className={buttonClassName('secondary', 'sm')} to={homePathFor(user)}>My workspace</Link>
              <UserMenu />
            </>
          ) : status === 'loading' ? (
            <span role="status">Restoring your session...</span>
          ) : status === 'error' ? (
            <Link className={buttonClassName('secondary', 'md')} to="/login">Recover session</Link>
          ) : (
            <>
              <Link className={buttonClassName('secondary', 'md')} to="/login" title="Log in to your existing account">Log in</Link>
              <Link className={buttonClassName('primary', 'md')} to="/register" title="Create a new member account">Join now</Link>
            </>
          )}
        </div>
      </div>
    </header>
    </>
  )
}
