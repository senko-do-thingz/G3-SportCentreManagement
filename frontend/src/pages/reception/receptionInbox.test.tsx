import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

const topUp = { id: 11, title: 'Verify wallet top-up TU-000004', content: 'Member MEM-0002 requested 200,000 VND. Check the bank statement.',
  createdAt: '2026-11-08T10:00:00', read: false, sourceType: 'WALLET_TOPUP', sourceId: 4, sourceStatus: 'PENDING', open: true, link: '/reception/wallet/topups' }

describe('Reception inbox (MW-07)', () => {
  it('shows the unread badge, opens the task, and marking read keeps the task open', async () => {
    let read = false
    const { calls } = (() => {
      tokenStorage.startSession({ accessToken: 'token', refreshToken: 'refresh' })
      return installFakeFetch(call => {
        if (call.path === '/users/me') return json(200, { ...MEMBER, role: 'RECEPTIONIST' })
        if (call.path === '/reception/inbox/11/read') { read = true; return json(200, { ...topUp, read: true }) }
        if (call.path.startsWith('/reception/inbox')) return json(200, { items: [{ ...topUp, read }], unreadOpen: read ? 0 : 1, open: 1 })
        return backendError(404, 'Not found')
      })
    })()
    const user = userEvent.setup()
    renderApp('/reception/inbox')
    expect(await screen.findByRole('button', { name: 'Inbox, 1 unread' })).toBeInTheDocument()
    const item = (await within(screen.getByRole('main')).findByText('Verify wallet top-up TU-000004')).closest('li') as HTMLElement
    expect(within(item).getByText('Open - PENDING')).toBeInTheDocument()
    const stats = screen.getByRole('list', { name: 'Inbox summary' })
    expect(within(stats).getByText('Open tasks (newest 100 notices)').previousElementSibling).toHaveTextContent('1')
    expect(within(stats).getByText('Unread open (newest 100 notices)').previousElementSibling).toHaveTextContent('1')
    expect(within(item).getByRole('link', { name: 'Open task' })).toHaveAttribute('href', '/reception/wallet/topups')
    await user.click(within(item).getByRole('button', { name: 'Mark as read' }))
    const after = (await within(screen.getByRole('main')).findByText('Verify wallet top-up TU-000004')).closest('li') as HTMLElement
    expect(await within(after).findByText('Open - PENDING')).toBeInTheDocument()
    expect(within(after).queryByRole('button', { name: 'Mark as read' })).not.toBeInTheDocument()
    expect(calls.some(c => c.method === 'PUT' && c.path === '/reception/inbox/11/read')).toBe(true)
    expect(calls.some(c => c.path.includes('/topups/4/confirm'))).toBe(false)
  })

  it('shows an empty state when nothing is open', async () => {
    tokenStorage.startSession({ accessToken: 'token', refreshToken: 'refresh' })
    installFakeFetch(call => call.path === '/users/me' ? json(200, { ...MEMBER, role: 'RECEPTIONIST' })
      : json(200, { items: [], unreadOpen: 0, open: 0 }))
    renderApp('/reception/inbox')
    expect(await screen.findByText('Nothing to do')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Inbox' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Inbox' })).not.toBeInTheDocument()
  })
})
