import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../test/fakeBackend'
import { renderApp } from '../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

const notice = { id: 5, title: 'Booked session schedule changed', content: 'Review the current schedule.', type: 'SESSION_CHANGED', read: false, createdAt: '2026-11-12T08:00:00' }

function setup(role: string, path: string, routes: (path: string, method: string) => Response | null) {
  tokenStorage.startSession({ accessToken: 'inbox-test', refreshToken: 'refresh' })
  const recorder = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, { ...MEMBER, role })
    return routes(call.path, call.method) ?? json(200, [])
  })
  renderApp(path)
  return recorder
}

describe('Header inbox', () => {
  it('shows a Member unread dot (no number) beside the avatar and marks read without other side effects', async () => {
    let read = false
    const { calls } = setup('MEMBER', '/member/support', (path, method) => {
      if (path === '/member/notifications/5/read' && method === 'PUT') { read = true; return new Response(null, { status: 204 }) }
      if (path === '/member/notifications') return json(200, { notices: [{ ...notice, read }], reminders: [], expiryWindowDays: 7 })
      return null
    })
    const user = userEvent.setup()
    const button = await screen.findByRole('button', { name: 'Inbox, 1 unread' })
    expect(button.closest('.workspace-header__account')?.querySelector('.user-menu')).not.toBeNull()
    expect(button.querySelector('.header-inbox__dot')).not.toBeNull()
    expect(button.textContent).not.toMatch(/\d/)
    await user.click(button)
    const panel = screen.getByRole('region', { name: 'Recent notifications' })
    await user.click(within(panel).getByRole('button', { name: /Booked session schedule changed/ }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Inbox' })).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Inbox' }).querySelector('.header-inbox__dot')).toBeNull()
    expect(within(panel).getByRole('link', { name: 'View all' })).toHaveAttribute('href', '/member/notifications')
    expect(calls.filter(call => call.method === 'PUT').map(call => call.path)).toEqual(['/member/notifications/5/read'])
    await user.keyboard('{Escape}')
    expect(panel).not.toBeVisible()
    expect(screen.getByRole('button', { name: 'Inbox' })).toHaveFocus()
  })

  it('uses the Receptionist task inbox in the header and no longer in the sidebar', async () => {
    const { calls } = setup('RECEPTIONIST', '/reception/support', path => {
      if (path === '/reception/inbox?openOnly=true') return json(200, { items: [{ id: 3, title: 'Support request SUP-1', content: 'Open member support request', createdAt: '2026-11-12T08:00:00', read: false, sourceType: 'SUPPORT_REQUEST', sourceId: 1, sourceStatus: 'OPEN', open: true, link: '/reception/support' }], unreadOpen: 1, open: 1 })
      return null
    })
    expect(await screen.findByRole('button', { name: 'Inbox, 1 unread' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /^Inbox/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Support requests' })).toBeInTheDocument()
    expect(calls.some(call => call.path.startsWith('/staff/notifications') || call.path.startsWith('/member/notifications'))).toBe(false)
  })

  it.each(['MANAGER', 'COACH'])('reads only the %s own staff notifications and shows an empty state', async role => {
    const { calls } = setup(role, role === 'MANAGER' ? '/manager' : '/coach', path => {
      if (path === '/staff/notifications') return json(200, [])
      if (path.startsWith('/reception') || path.startsWith('/member/notifications')) return backendError(403, 'Forbidden')
      return null
    })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Inbox' }))
    expect(await screen.findByText('No new notifications.')).toBeInTheDocument()
    expect(calls.some(call => call.path.startsWith('/reception/inbox') || call.path.startsWith('/member/notifications'))).toBe(false)
  })
})
