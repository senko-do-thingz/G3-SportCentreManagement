import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { Role } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { formatDateTime } from '../utils/dateDisplay'
import { INBOX_CHANGED_EVENT, INBOX_PATHS, INBOX_POLL_MS, loadInbox, markInboxRead, type InboxEntry, type InboxFeed } from './headerInboxData'

const RECENT_LIMIT = 5

function EnvelopeIcon() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">
      <rect x="3" y="5" width="18" height="14" rx="2" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M4 7l8 6 8-6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** Shared envelope inbox shown beside the avatar for every signed-in role. */
export function HeaderInbox() {
  const { role } = useAuth()
  if (!role) return null
  return <HeaderInboxForRole key={role} role={role} />
}

function HeaderInboxForRole({ role }: { role: Role }) {
  const [feed, setFeed] = useState<InboxFeed | null>(null)
  const [failed, setFailed] = useState(false)
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const panelId = useId()
  const navigate = useNavigate()
  const controllerRef = useRef<AbortController | null>(null)

  const load = useCallback(() => {
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    loadInbox(role, controller.signal).then(
      next => { if (!controller.signal.aborted) { setFeed(next); setFailed(false) } },
      () => { if (!controller.signal.aborted) setFailed(true) },
    )
  }, [role])

  useEffect(() => {
    load()
    const timer = window.setInterval(load, INBOX_POLL_MS)
    window.addEventListener(INBOX_CHANGED_EVENT, load)
    return () => { window.clearInterval(timer); window.removeEventListener(INBOX_CHANGED_EVENT, load); controllerRef.current?.abort() }
  }, [load])

  useEffect(() => {
    if (!open) return
    const closeOutside = (event: PointerEvent) => { if (!rootRef.current?.contains(event.target as Node)) setOpen(false) }
    const closeEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      setOpen(false)
      buttonRef.current?.focus()
    }
    document.addEventListener('pointerdown', closeOutside)
    document.addEventListener('keydown', closeEscape)
    return () => { document.removeEventListener('pointerdown', closeOutside); document.removeEventListener('keydown', closeEscape) }
  }, [open])

  const toggle = () => { if (!open) load(); setOpen(!open) }
  const choose = async (entry: InboxEntry) => {
    if (!entry.read) {
      try { await markInboxRead(role, entry.id) } catch { /* the badge simply stays unread */ }
      window.dispatchEvent(new Event(INBOX_CHANGED_EVENT))
    }
    if (entry.link) { setOpen(false); navigate(entry.link) }
  }

  const unread = feed?.unread ?? 0
  const recent = feed?.entries.slice(0, RECENT_LIMIT) ?? []
  return (
    <div className="header-inbox" ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        className="header-inbox__button"
        aria-label={unread ? `Inbox, ${unread} unread` : 'Inbox'}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={toggle}
      >
        <EnvelopeIcon />
        {unread > 0 && <span className="header-inbox__dot" aria-hidden="true" />}
      </button>
      <div id={panelId} className="header-inbox__panel" role="region" aria-label="Recent notifications" hidden={!open}>
        <p className="header-inbox__title">Inbox</p>
        {failed && !feed ? <p className="header-inbox__empty" role="status">Notifications are unavailable right now.</p>
          : !feed ? <p className="header-inbox__empty" role="status">Loading notifications...</p>
          : <>
            {unread === 0 && <p className="header-inbox__empty" role="status">No new notifications.</p>}
            {recent.length > 0 && <ul className="header-inbox__list">{recent.map(entry => (
              <li key={entry.id}>
                <button type="button" className={'header-inbox__item' + (entry.read ? '' : ' header-inbox__item--unread')} onClick={() => void choose(entry)}>
                  <span className="header-inbox__item-title">{entry.title}</span>
                  <span className="header-inbox__item-text">{entry.content}</span>
                  <span className="header-inbox__item-time">{formatDateTime(entry.createdAt)}{entry.read ? '' : ' · Unread'}</span>
                </button>
              </li>
            ))}</ul>}
          </>
        <Link className="header-inbox__all" to={INBOX_PATHS[role]} onClick={() => setOpen(false)}>View all</Link>
      </div>
    </div>
  )
}
