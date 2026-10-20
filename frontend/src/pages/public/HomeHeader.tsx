import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { homePathFor } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { Brand } from '../../components/Brand'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { UserMenu } from '../../layouts/UserMenu'

const LINKS = [
  { label: 'Home', href: '/' },
  { label: 'Sports', href: '#sports' },
  { label: 'Schedule', href: '#classes' },
  { label: 'Coaches', href: '#coaches' },
  { label: 'Packages', href: '#packages' },
]

export function HomeHeader() {
  const { status, user } = useAuth()
  const [open, setOpen] = useState(false)
  const toggle = useRef<HTMLButtonElement>(null)
  return (
    <header className="home-header home-container">
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
        {status === 'authenticated' ? (
          <>
            <Link className={buttonClassName('secondary', 'sm')} to={homePathFor(user)}>My workspace</Link>
            <UserMenu />
          </>
        ) : status === 'loading' ? (
          <span role="status">Restoring your session...</span>
        ) : (
          <>
            <Link className={buttonClassName('secondary', 'md')} to="/login">Log in</Link>
            <Link className={buttonClassName('primary', 'md')} to="/register">Join now</Link>
          </>
        )}
      </div>
    </header>
  )
}
