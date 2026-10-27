import { useEffect, useRef, useState } from 'react'
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
  const pointerType = useRef('')

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
          <a href="#main-content" aria-current="page" onClick={close}>Home</a>
          {groups.map((group) => {
            const expanded = submenu === group.label
            const id = `home-submenu-${group.label}`
            return <div className="home-nav__group" key={group.label}
              onPointerEnter={(event) => { if (event.pointerType === 'mouse' && group.children.length) setSubmenu(group.label) }}
              onPointerLeave={(event) => { if (event.pointerType === 'mouse') setSubmenu((current) => current === group.label ? null : current) }}
              onPointerDown={(event) => { pointerFocus.current = true; pointerType.current = event.pointerType }}
              onFocus={(event) => {
                if (!pointerFocus.current && !event.currentTarget.contains(event.relatedTarget as Node) && group.children.length) setSubmenu(group.label)
              }}
              onBlur={(event) => {
                pointerFocus.current = false
                if (!event.currentTarget.contains(event.relatedTarget as Node)) setSubmenu((current) => current === group.label ? null : current)
              }}>
              {group.children.length > 0 ? <button type="button" className="home-nav__toggle" aria-expanded={expanded} aria-controls={id}
                onClick={() => {
                  setSubmenu(pointerFocus.current && pointerType.current === 'mouse' ? group.label : expanded ? null : group.label)
                  pointerFocus.current = false
                }}
                onKeyDown={(event) => {
                  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
                  event.preventDefault()
                  setSubmenu(group.label)
                  const first = event.key === 'ArrowDown'
                  requestAnimationFrame(() => {
                    const links = header.current?.querySelector(`#${id}`)?.querySelectorAll<HTMLAnchorElement>('a')
                    links?.[first ? 0 : links.length - 1]?.focus()
                  })
                }}>{group.label}</button> : <a href={group.href} onClick={close}>{group.label}</a>}
              {group.children.length > 0 && <ul id={id} className="home-nav__submenu" hidden={!expanded} aria-label={`${group.label} destinations`}>
                <li><a href={group.href} onClick={close}>All {group.label.toLowerCase()}</a></li>
                {group.children.map((child) => <li key={child.href}><a href={child.href} onClick={close}>{child.label}</a></li>)}
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
