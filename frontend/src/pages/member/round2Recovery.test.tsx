import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { registration, sport, sportPackage } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'

const session = { id: 501, sportId: 1, sportName: 'Football', classId: null, className: null, trainingFormat: 'SELF_TRAINING', level: null, coachId: null, coachName: null, facilityId: null, facilityName: null, sessionDate: '2026-10-20', startTime: '17:00:00', endTime: '18:00:00', capacity: 5, bookedCount: 1, freeSeats: 4, full: false, status: 'PUBLISHED' }
beforeEach(() => {
  vi.stubEnv('VITE_MANAGER_PREVIEW', 'false')
  tokenStorage.startSession({ accessToken: 'round2-member', refreshToken: 'round2-refresh' })
})
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })

describe('DEF-001 DEF-002 resource errors retain routes and recover', () => {
  it.each(['sessionCount', 'sessionMinutes'])('DEF-001 contract review rejects null %s and recovers after a valid response', async (field) => {
    let broken = true
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/sports') return json(200, [sport()])
      if (call.path === '/packages') return json(200, [broken ? { ...sportPackage(), [field]: null } : sportPackage()])
      if (call.path === '/membership-cards/my') return json(200, [])
      return backendError(404, 'Unexpected synthetic request')
    })
    const user = userEvent.setup()
    renderApp('/member/packages')
    expect(await screen.findByRole('alert')).toHaveTextContent('unexpected response')
    expect(screen.getByRole('heading', { name: 'Sport Packages' })).toBeInTheDocument()
    expect(screen.queryByText(/No session limit/)).not.toBeInTheDocument()
    broken = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('list', { name: 'Sport packages' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
  it.each(['packages', 'sports', 'sessions'])('%s null row retains an alert, keyboard Retry and successful recovery', async (resource) => {
    let broken = true
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/sports') return json(200, resource === 'sports' && broken ? [null] : [sport()])
      if (call.path === '/packages') return json(200, resource === 'packages' && broken ? [null] : [sportPackage()])
      if (call.path.startsWith('/sessions?')) return json(200, { content: broken ? [null] : [session], page: 0, size: 20, totalElements: 1, totalPages: 1 })
      if (call.path === '/membership-cards/my') return json(200, [])
      return backendError(404, 'Unexpected synthetic request')
    })
    const user = userEvent.setup()
    renderApp(resource === 'sessions' ? '/member/classes' : '/member/packages')
    await screen.findAllByRole('alert')
    expect(screen.getByRole('heading', { name: resource === 'sessions' ? 'Find Classes' : 'Sport Packages' })).toBeInTheDocument()
    const retry = screen.getAllByRole('button', { name: 'Try again' }).at(-1)!
    retry.focus()
    expect(retry).toHaveFocus()
    broken = false
    await user.keyboard('{Enter}')
    if (resource === 'sessions') expect(await screen.findByText('1 matching sessions')).toBeInTheDocument()
    else expect(await screen.findByRole('list', { name: 'Sport packages' })).toBeInTheDocument()
  })
})

describe('DEF-003 package registration submission', () => {
  it('uses business today for default/min, rejects yesterday, preserves server rejection and retries tomorrow', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-19T18:00:00Z'))
    const submissions: unknown[] = []
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/packages') return json(200, [sportPackage()])
      if (call.path === '/membership-cards/my' || call.path === '/packages/registrations/my') return json(200, [])
      if (call.path === '/packages/registrations' && call.method === 'POST') {
        submissions.push(call.body)
        return submissions.length === 1 ? backendError(400, 'Start date cannot be in the past') : json(201, registration({ status: 'PENDING_PAYMENT', paymentId: 41 }))
      }
      return backendError(404, 'Unexpected synthetic request')
    })
    const user = userEvent.setup()
    renderApp('/member/packages/4')
    const input = await screen.findByLabelText(/Start date/)
    expect(input).toHaveValue('2026-10-20')
    expect(input).toHaveAttribute('min', '2026-10-20')
    const confirm = screen.getByRole('button', { name: /Confirm registration/i })
    await waitFor(() => expect(confirm).toBeEnabled())
    await user.clear(input); await user.type(input, '2026-10-19'); await user.click(confirm)
    expect(submissions).toHaveLength(0)
    await user.clear(input); await user.type(input, '2026-10-20'); await user.click(confirm)
    expect(await screen.findByText('Start date cannot be in the past')).toBeInTheDocument()
    await user.clear(input); await user.type(input, '2026-10-21'); await user.click(confirm)
    await waitFor(() => expect(submissions).toHaveLength(2))
    expect(submissions[1]).toMatchObject({ packageId: 4, startDate: '2026-10-21', channel: 'ONLINE' })
  })
  it('refreshes the minimum and validation after Vietnam midnight without a timer', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-12-31T16:59:59Z'))
    installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER) : call.path === '/packages' ? json(200, [sportPackage()]) : json(200, []))
    const user = userEvent.setup()
    renderApp('/member/packages/4')
    const input = await screen.findByLabelText(/Start date/)
    expect(input).toHaveValue('2026-12-31')
    vi.setSystemTime(new Date('2026-12-31T17:00:00Z'))
    await act(async () => { input.focus() })
    expect(input).toHaveAttribute('min', '2027-01-01')
    await user.click(screen.getByRole('button', { name: /Confirm registration/i }))
    expect(input).toHaveAttribute('aria-invalid', 'true')
  })
})
