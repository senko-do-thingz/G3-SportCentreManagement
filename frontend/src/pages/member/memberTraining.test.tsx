import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MEMBER_TRAINING_ROUTES } from '../../api/memberTrainingApi'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const when = { sessionDate: '2026-10-20', startTime: '09:00:00', endTime: '10:00:00', sportName: 'Basketball', className: 'Basketball Foundations', coachName: 'Daniel Smith', facilityName: 'Court A' }
const plan = { id: 1, title: 'Basketball fundamentals', summary: 'Build ball control.', audience: 'CLASS', coachName: 'Daniel Smith', revision: 2, publishedAt: '2026-10-20T09:00:00', steps: [{ order: 1, title: 'Dribbling warm-up', details: 'Ten minutes.' }] }
const note = { id: 1, planId: 1, sessionId: null, coachName: 'Daniel Smith', comment: 'Keep your head up.', createdAt: '2026-10-27T18:10:00' }
const pageOf = (content: unknown[]) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 })

type Overrides = Partial<Record<keyof typeof MEMBER_TRAINING_ROUTES, () => Response>>

function backend(overrides: Overrides = {}) {
  return installFakeFetch((call) => {
    const path = call.path.split('?')[0]
    if (path === '/users/me') return json(200, MEMBER)
    for (const key of Object.keys(MEMBER_TRAINING_ROUTES) as (keyof typeof MEMBER_TRAINING_ROUTES)[]) {
      if (path !== MEMBER_TRAINING_ROUTES[key]) continue
      const override = overrides[key]
      if (override) return override()
      if (key === 'plans') return json(200, [plan])
      if (key === 'notes') return json(200, [note])
      if (key === 'attendance') return json(200, pageOf([{ id: 1, ...when, sessionId: 9, state: 'ATTENDED', arrivedAt: '2026-10-20T08:55:00' }, { id: 2, ...when, sessionId: 10, state: 'UNRECORDED' }, { id: 3, ...when, sessionId: 11, state: 'ABSENT' }]))
      return json(200, pageOf([{ id: 5, ...when, sessionId: 9, headline: 'Free-throw accuracy improved', metrics: [{ label: 'Turnovers', value: 0, unit: null }, { label: 'Sprint time', value: null, unit: 's' }], comment: 'Steady session.', recordedAt: '2026-10-20T10:20:00' }]))
    }
    return backendError(404, 'Unexpected test request')
  })
}

beforeEach(() => { vi.stubEnv('VITE_MANAGER_PREVIEW', 'false'); tokenStorage.startSession({ accessToken: 'member-test', refreshToken: 'refresh-test' }) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })

describe('NX-14 member training plans and coach notes', () => {
  it('shows own plans and coach notes read-only, with no edit control', async () => {
    const api = backend()
    renderApp('/member/training')
    expect(await screen.findByText('Basketball fundamentals')).toBeInTheDocument()
    expect(screen.getByText('Dribbling warm-up')).toBeInTheDocument()
    expect(screen.getByText('Class plan')).toBeInTheDocument()
    expect(await screen.findByText('Keep your head up.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /edit|save|delete|add|publish/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(api.calls.every((call) => call.method === 'GET')).toBe(true)
    expect(api.calls.some((call) => /members\/\d+|memberId/.test(call.path))).toBe(false)
  })

  it('shows empty states for both lists', async () => {
    backend({ plans: () => json(200, []), notes: () => json(200, []) })
    renderApp('/member/training')
    expect(await screen.findByText('No training plan yet')).toBeInTheDocument()
    expect(await screen.findByText('No coach notes yet')).toBeInTheDocument()
  })

  it('keeps notes visible when plans fail, and retries the failed list', async () => {
    let fail = true
    backend({ plans: () => (fail ? backendError(503, 'Plan service unavailable') : json(200, [plan])) })
    const user = userEvent.setup()
    renderApp('/member/training')
    expect(await screen.findByText('Plan service unavailable')).toBeInTheDocument()
    expect(await screen.findByText('Keep your head up.')).toBeInTheDocument()
    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Basketball fundamentals')).toBeInTheDocument()
  })

  it('shows a forbidden answer as an error, not as an empty list', async () => {
    backend({ plans: () => backendError(403, 'You do not have permission to do this.') })
    renderApp('/member/training')
    expect(await screen.findByText('You do not have permission to do this.')).toBeInTheDocument()
    expect(screen.queryByText('No training plan yet')).not.toBeInTheDocument()
  })

  it('rejects a malformed server answer with a generic message', async () => {
    backend({ plans: () => json(200, [{ id: 1, title: '' }]) })
    renderApp('/member/training')
    expect(await screen.findByText('The server returned an unexpected training response. Please try again later.')).toBeInTheDocument()
  })
})

describe('NX-15 member attendance and training results', () => {
  it('lists attendance with Not recorded yet apart from Absent, using My Bookings date wording', async () => {
    const api = backend()
    renderApp('/member/history')
    const list = await screen.findByRole('list')
    expect(within(list).getAllByText('Basketball Foundations')).toHaveLength(3)
    expect(within(list).getByText('Attended')).toBeInTheDocument()
    expect(within(list).getByText('Not recorded yet')).toBeInTheDocument()
    expect(within(list).getByText('Absent')).toBeInTheDocument()
    expect(within(list).getAllByText(/20\/10\/2026, 09:00 - 10:00/)).toHaveLength(3)
    expect(api.calls.some((call) => /members\/\d+|memberId/.test(call.path))).toBe(false)
  })

  it('shows results with a recorded zero apart from a missing value', async () => {
    backend()
    const user = userEvent.setup()
    renderApp('/member/history')
    await user.click(await screen.findByRole('tab', { name: 'Training results' }))
    expect(await screen.findByText('Free-throw accuracy improved')).toBeInTheDocument()
    expect(screen.getByText('Turnovers').nextElementSibling).toHaveTextContent(/^0$/)
    expect(screen.getByText('Sprint time').nextElementSibling).toHaveTextContent('Not recorded')
  })

  it('shows empty and error states for attendance', async () => {
    backend({ attendance: () => json(200, pageOf([])) })
    const { unmount } = renderApp('/member/history')
    expect(await screen.findByText('No attendance yet')).toBeInTheDocument()
    unmount()
    backend({ attendance: () => backendError(503, 'History service unavailable') })
    renderApp('/member/history')
    expect(await screen.findByText('History service unavailable')).toBeInTheDocument()
    expect(screen.queryByText('No attendance yet')).not.toBeInTheDocument()
  })

  it('asks for the next page when the member pages through history', async () => {
    const rows = Array.from({ length: 10 }, (_, index) => ({ id: index + 1, ...when, sessionId: index + 1, state: 'ATTENDED' }))
    const api = backend({ attendance: () => json(200, { content: rows, page: 0, size: 10, totalElements: 12, totalPages: 2 }) })
    const user = userEvent.setup()
    renderApp('/member/history')
    await screen.findByText('Showing 1-10 of 12')
    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    expect(api.calls.some((call) => call.path.startsWith(`${MEMBER_TRAINING_ROUTES.attendance}?`) && call.path.includes('page=1'))).toBe(true)
  })

  it('hides an arrival line when the arrival time cannot be read', async () => {
    backend({ attendance: () => json(200, pageOf([{ id: 1, ...when, sessionId: 9, state: 'ATTENDED', arrivedAt: 'not-a-date' }])) })
    renderApp('/member/history')
    expect(await screen.findByText('Attended')).toBeInTheDocument()
    expect(screen.queryByText(/Arrived/)).not.toBeInTheDocument()
  })
})
