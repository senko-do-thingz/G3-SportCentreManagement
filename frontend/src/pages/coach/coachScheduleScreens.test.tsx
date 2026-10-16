import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const COACH = { id: 8, email: 'coach@sportify.demo', fullName: 'Quan Coach', phone: '0901234508', role: 'COACH', status: 'ACTIVE' }
const RECEPTIONIST = { id: 9, email: 'reception@sportify.demo', fullName: 'Jamie Tran', phone: '0901234599', role: 'RECEPTIONIST', status: 'ACTIVE' }
const session = {
  id: 5, classId: 7, className: 'Basketball Foundations', sportId: 3, sportName: 'Basketball', coachId: 8, coachName: 'Quan Coach',
  facilityId: 2, facilityName: 'Court A', sessionDate: '2026-10-20', startTime: '17:00:00', endTime: '18:00:00',
  capacity: 20, bookedCount: 2, status: 'PUBLISHED',
}
const roster = [{
  bookingId: 21, memberCode: 'MB-0248', memberName: 'Taylor Nguyen', memberPhone: '0901234567',
  packageRegistrationId: 11, packageId: 4, packageName: 'Basketball package', bookedAt: '2026-10-17T10:00:00',
}]

function setup(profile = COACH, handler?: (call: RecordedCall) => Response | undefined) {
  tokenStorage.startSession({ accessToken: 'staff-access', refreshToken: 'staff-refresh' })
  const calls = installFakeFetch((call) => {
    const override = handler?.(call)
    if (override) return override
    if (call.path === '/users/me') return json(200, profile)
    if (call.path.startsWith('/coach/sessions?')) return json(200, [session])
    if (call.path === '/coach/sessions/5/roster') return json(200, roster)
    return backendError(404, `Unexpected test request: ${call.method} ${call.path}`)
  })
  return calls
}

afterEach(() => vi.unstubAllGlobals())

describe('FE-10 coach schedule and roster screens', () => {
  it('shows the coach-owned weekly sessions without a caller-supplied coach filter', async () => {
    const { calls } = setup()
    renderApp('/coach?week=2026-10-19')

    expect(await screen.findByRole('heading', { name: 'My Teaching Schedule' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Basketball Foundations' })).toBeInTheDocument()
    expect(screen.getByText('Sessions this week').parentElement).toHaveTextContent('1')
    expect(screen.getByText('Classes assigned').parentElement).toHaveTextContent('1')
    expect(screen.getByRole('link', { name: 'View roster' })).toHaveAttribute('href', '/coach/roster?sessionId=5&week=2026-10-19')
    expect(calls.some((call) => call.path === '/coach/sessions?from=2026-10-19&to=2026-10-25')).toBe(true)
    expect(calls.some((call) => call.path.includes('coachId='))).toBe(false)
  })

  it('loads only the selected owned session roster, hides phone numbers and prints the roster', async () => {
    const { calls } = setup()
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    const user = userEvent.setup()
    renderApp('/coach/roster?sessionId=5&week=2026-10-19')

    expect(await screen.findByText('Taylor Nguyen')).toBeInTheDocument()
    expect(screen.getByText('MB-0248')).toBeInTheDocument()
    expect(screen.getByText('Basketball package')).toBeInTheDocument()
    expect(screen.queryByText('0901234567')).not.toBeInTheDocument()
    expect(screen.getByText(/This roster shows member names/)).toBeInTheDocument()
    expect(calls.filter((call) => call.path === '/coach/sessions/5/roster')).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Print roster' }))
    expect(print).toHaveBeenCalledTimes(1)
  })

  it('shows the roster empty state for a session with no confirmed bookings', async () => {
    setup(COACH, (call) => call.path === '/coach/sessions/5/roster' ? json(200, []) : undefined)
    renderApp('/coach/roster?sessionId=5&week=2026-10-19')
    expect(await screen.findByText('No confirmed bookings')).toBeInTheDocument()
  })

  it('keeps the selected session and retries a roster request after an API error', async () => {
    let attempts = 0
    setup(COACH, (call) => {
      if (call.path !== '/coach/sessions/5/roster') return undefined
      attempts += 1
      return attempts === 1 ? backendError(503, 'Roster temporarily unavailable') : json(200, roster)
    })
    const user = userEvent.setup()
    renderApp('/coach/roster?sessionId=5&week=2026-10-19')

    expect(await screen.findByText('Roster temporarily unavailable')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Basketball Foundations' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Taylor Nguyen')).toBeInTheDocument()
    expect(attempts).toBe(2)
  })

  it('surfaces schedule failures with retry and never fetches an arbitrary roster before ownership is known', async () => {
    let attempts = 0
    const { calls } = setup(COACH, (call) => {
      if (!call.path.startsWith('/coach/sessions?')) return undefined
      attempts += 1
      return attempts === 1 ? backendError(503, 'Schedule temporarily unavailable') : json(200, [session])
    })
    const user = userEvent.setup()
    renderApp('/coach/roster?sessionId=999&week=2026-10-19')
    expect(await screen.findByText('Schedule temporarily unavailable')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText(/Session is not in this week's schedule/)).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/coach/sessions/999/roster')).toBe(false)
  })

  it('blocks coaches from receptionist routes and receptionists from coach routes', async () => {
    setup(COACH)
    const coach = renderApp('/reception/bookings')
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    coach.unmount()

    setup(RECEPTIONIST)
    renderApp('/coach')
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
  })
})
