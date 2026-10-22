import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { homePathFor } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { Brand } from '../../components/Brand'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { UserMenu } from '../../layouts/UserMenu'
import { useTheme } from '../../theme/useTheme'

const LINKS = [
  { label: 'Home', href: '/' },
  { label: 'Sports', href: '#sports' },
  { label: 'Coaches', href: '#coaches' },
  { label: 'Packages', href: '#packages' },
  { label: 'FAQ', href: '#faq' },
]

export function HomeHeader() {
  const { status, user } = useAuth()
  const [open, setOpen] = useState(false)
  const toggle = useRef<HTMLButtonElement>(null)
  return (
    <header className="home-header">
      <div className="home-wrap home-header__bar">
        <Brand compact />
        <button
          ref={toggle}
          type="button"
          className="home-menu-toggle"
          aria-controls="home-navigation"
          aria-expanded={open}
          onClick={() => setOpen((value) => !value)}
        >
          Menu
        </button>
        <nav
          id="home-navigation"
          aria-label="Home navigation"
          className={`home-nav${open ? ' home-nav--open' : ''}`}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              setOpen(false)
              toggle.current?.focus()
            }
          }}
        >
          {LINKS.map((link) => link.href === '/' ? (
            <Link key={link.label} to="/" aria-current="page" onClick={() => setOpen(false)}>{link.label}</Link>
          ) : (
            <a key={link.label} href={link.href} onClick={() => { setOpen(false); toggle.current?.focus() }}>{link.label}</a>
          ))}
        </nav>
        <div className="home-account">
          <ThemeToggle />
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
  )
}

/** Light and dark switch. The icon shows the current mode (sun for light, moon for dark); the label names the action. */
function ThemeToggle() {
  const { resolved, setPreference } = useTheme()
  const next = resolved === 'dark' ? 'light' : 'dark'
  return (
    <button type="button" className="home-theme" aria-label={`Switch to ${next} mode`} title={`${resolved === 'dark' ? 'Dark' : 'Light'} mode. Switch to ${next} mode`} onClick={() => setPreference(next)}>
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" focusable="false">
        {resolved === 'dark'
          ? <path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5Z" />
          : <><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></>}
      </svg>
    </button>
  )
}
