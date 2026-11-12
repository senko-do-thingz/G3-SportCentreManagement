import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const submitted = { id: 5, code: 'SUP-1005', category: 'MEMBERSHIP', subject: 'Discount', description: 'Does my discount apply?', status: 'OPEN',
  sessionCode: null, submittedByMe: true, createdAt: '2026-11-06T10:00:00', updatedAt: '2026-11-06T10:00:00', unreadReplies: 0, replies: [] }
const answered = { ...submitted, status: 'IN_PROGRESS', unreadReplies: 1,
  replies: [{ message: 'Your Gold card gives 5% off.', createdAt: '2026-11-06T11:00:00' }] }

afterEach(() => vi.unstubAllGlobals())

describe('Member support requests (Flow 6)', () => {
  it('shares the assistant answer with reception and keeps one key across a retry', async () => {
    tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
    let fail = true
    let created = false
    const { calls, fetchMock } = installFakeFetch(call => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path.includes('/consultation')) return json(200, {
        mode: 'AI_ASSISTED', status: 'ANSWERED', answer: 'Your active packages and card from your own records.', historyStored: false,
        sources: [{ kind: 'MY_PACKAGE', id: 3, title: 'Football 8', detail: 'Football: 7 of 8 sessions available.', path: '/member/membership' }],
      })
      if (call.path === '/member/support-requests' && call.method === 'POST') {
        if (fail) { fail = false; return backendError(500, 'Try again') }
        created = true; return json(201, submitted)
      }
      if (call.path === '/member/support-requests') return json(200, created ? [submitted] : [])
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/assistant')
    await user.type(await screen.findByRole('textbox', { name: /Your question/ }), 'gói của tôi còn bao nhiêu buổi?')
    await user.click(screen.getByRole('button', { name: 'Ask' }))
    expect(await screen.findByText('How this answer was found: AI understood your question.')).toBeInTheDocument()
    expect(screen.getByText('Source type: Your package record')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Contact reception about this answer' }))

    const include = await screen.findByLabelText('Include the assistant answer with my request')
    expect(include).toBeChecked()
    await user.type(screen.getByLabelText(/^Subject/), 'Discount')
    await user.type(screen.getByLabelText(/^Describe the issue/), 'Does my discount apply?')
    await user.click(screen.getByRole('button', { name: 'Submit request' }))
    await screen.findByText(/Try again/)
    await user.click(screen.getByRole('button', { name: 'Submit request' }))
    expect(await screen.findByText(/Your request SUP-1005 was sent to reception/)).toBeInTheDocument()

    const posts = calls.filter(call => call.method === 'POST' && call.path === '/member/support-requests')
    expect(posts).toHaveLength(2)
    expect(posts[1].body).toMatchObject({ category: 'MEMBERSHIP', subject: 'Discount', description: 'Does my discount apply?' })
    expect((posts[1].body as { assistantExcerpt: string }).assistantExcerpt).toContain('Question: gói của tôi còn bao nhiêu buổi?')
    const keys = fetchMock.mock.calls.filter(([url, init]) => init?.method === 'POST' && String(url).includes('/member/support-requests'))
      .map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/)
    expect(keys[1]).toBe(keys[0])
    expect(await screen.findByRole('button', { name: 'View SUP-1005' })).toBeInTheDocument()
  })

  it('does not send the assistant answer when the Member unticks it', async () => {
    tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
    const { calls } = installFakeFetch(call => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/member/support-requests' && call.method === 'POST') return json(201, submitted)
      if (call.path === '/member/support-requests') return json(200, [])
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/support')
    expect(screen.queryByLabelText('Include the assistant answer with my request')).not.toBeInTheDocument()
    await user.type(await screen.findByLabelText(/^Subject/), 'Locker')
    await user.type(screen.getByLabelText(/^Describe the issue/), 'Locker 12 is broken')
    await user.click(screen.getByRole('button', { name: 'Submit request' }))
    await screen.findByText(/was sent to reception/)
    const body = calls.find(call => call.method === 'POST')?.body as Record<string, unknown>
    expect(body).not.toHaveProperty('assistantExcerpt')
    expect(body.category).toBe('GENERAL')
  })

  it('lets the member reply to reopen a resolved request and shows their own message', async () => {
    tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
    let reopened = false
    const resolved = { ...answered, status: 'RESOLVED', unreadReplies: 0 }
    const { calls } = installFakeFetch(call => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/member/support-requests/5/replies' && call.method === 'POST') {
        reopened = true
        return json(200, { ...resolved, status: 'IN_PROGRESS' })
      }
      if (call.path === '/member/support-requests/5') return json(200, reopened
        ? { ...resolved, status: 'IN_PROGRESS', replies: [...resolved.replies, { message: 'It still fails.', createdAt: '2026-11-07T09:00:00', fromMe: true }] }
        : resolved)
      if (call.path === '/member/support-requests') return json(200, [{ ...resolved, replies: [] }])
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/support')
    await user.click(await screen.findByRole('button', { name: 'View SUP-1005' }))
    expect(await screen.findByText(/Replying within 7 days of the resolution reopens this request/)).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Still need help\? Reply to reopen/), 'It still fails.')
    await user.click(screen.getByRole('button', { name: 'Reply and reopen' }))
    await waitFor(() => expect(calls.some(call => call.method === 'POST' && call.path === '/member/support-requests/5/replies')).toBe(true))
    expect(await screen.findByText('It still fails.')).toBeInTheDocument()
    expect(screen.getByText('You')).toBeInTheDocument()
  })

  it('does not offer a reply on a closed request', async () => {
    tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
    const closed = { ...answered, status: 'CLOSED', unreadReplies: 0 }
    installFakeFetch(call => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/member/support-requests/5') return json(200, closed)
      if (call.path === '/member/support-requests') return json(200, [{ ...closed, replies: [] }])
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/support')
    await user.click(await screen.findByRole('button', { name: 'View SUP-1005' }))
    expect(await screen.findByText(/This request is closed/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Send message|Reply and reopen/ })).not.toBeInTheDocument()
  })

  it('shows reception replies and marks them as read', async () => {
    tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
    let read = false
    const { calls } = installFakeFetch(call => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/member/support-requests/5/read') { read = true; return new Response(null, { status: 204 }) }
      if (call.path === '/member/support-requests/5') return json(200, read ? { ...answered, unreadReplies: 0 } : answered)
      if (call.path === '/member/support-requests') return json(200, [read ? { ...answered, unreadReplies: 0, replies: [] } : { ...answered, replies: [] }])
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/support')
    expect(await screen.findByText('1 new reply')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'View SUP-1005' }))
    expect(await screen.findByText('Your Gold card gives 5% off.')).toBeInTheDocument()
    await waitFor(() => expect(calls.some(call => call.method === 'PUT' && call.path === '/member/support-requests/5/read')).toBe(true))
    expect(screen.queryByText(/internal/i)).not.toBeInTheDocument()
  })
})
