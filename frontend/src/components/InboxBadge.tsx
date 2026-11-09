import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { receptionInboxApi } from '../api/walletApi'

/** MW-07: unread open tasks for the signed-in Receptionist, refreshed every 30 seconds while the page is open. */
export const INBOX_POLL_MS = 30000
/** Fired by the inbox page after it loads or changes, so the badge never shows a stale count. */
export const INBOX_CHANGED_EVENT = 'sportify:reception-inbox-changed'

export function InboxBadge() {
  const [count, setCount] = useState<number | null>(null)
  useEffect(() => {
    let controller = new AbortController()
    const load = () => {
      controller.abort()
      controller = new AbortController()
      receptionInboxApi.get(true, controller.signal).then((inbox) => setCount(inbox.unreadOpen), () => undefined)
    }
    load()
    const timer = window.setInterval(load, INBOX_POLL_MS)
    window.addEventListener(INBOX_CHANGED_EVENT, load)
    return () => { window.clearInterval(timer); window.removeEventListener(INBOX_CHANGED_EVENT, load); controller.abort() }
  }, [])
  return (
    <Link className="inbox-badge" to="/reception/inbox" aria-label={count ? `Inbox, ${count} unread open tasks` : 'Inbox'}>
      Inbox{count ? <span className="inbox-badge__count" aria-hidden="true">{count}</span> : null}
    </Link>
  )
}
