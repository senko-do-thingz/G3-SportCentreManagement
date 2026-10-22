import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { memberSessionsApi } from '../../api/memberSessionsApi'
import { ApiError } from '../../api/errors'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MEMBER, MANAGER } from '../../test/fakeBackend'

export const browseSessionFixture = { id: 5, sportId: 3, sportName: 'Basketball', classId: 1, className: 'Basketball Foundations', trainingFormat: 'COACH_LED', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', facilityId: 1, facilityName: 'Court A', sessionDate: '2026-10-16', startTime: '16:00:00', endTime: '17:00:00', capacity: 20, bookedCount: 2, freeSeats: 18, full: false, status: 'PUBLISHED' }
function backend(profile = MEMBER) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, profile)
    if (call.path.startsWith('/sessions?')) return json(200, { content: [browseSessionFixture], page: 0, size: 100, totalElements: 1, totalPages: 1 })
    return backendError(404, 'Unexpected test request')
  })
}
beforeEach(() => { vi.stubEnv('VITE_MANAGER_PREVIEW', 'false'); tokenStorage.startSession({ accessToken: 'member-test', refreshToken: 'refresh-test' }) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })
describe('FE-08 member calendar', () => {
  it('distinguishes server failure from an empty schedule and supports retry', async () => {
    backend()
    vi.spyOn(memberSessionsApi, 'all').mockRejectedValueOnce(new ApiError(503, 'Schedule service unavailable')).mockResolvedValueOnce([])
    const user = userEvent.setup()
    renderApp('/member/schedule?date=2026-10-16')
    expect(await screen.findByText('Schedule service unavailable')).toBeInTheDocument()
    expect(screen.queryByText('No sessions this week')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No sessions this week')).toBeInTheDocument()
  })
  it('renders a seven-day timetable and reads the complete week without extra filter lookups', async () => {
    const api = backend()
    renderApp('/member/schedule?date=2026-10-16')
    expect(await screen.findByText('Basketball Foundations')).toBeInTheDocument()
    expect(screen.getByText('18 spots left')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Weekly session timetable' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Weekly schedule' })).toBeInTheDocument()
    expect(screen.getByText('Coach: Quan Coach')).toBeInTheDocument()
    expect(screen.getByText('Court A')).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader')).toHaveLength(8)
    expect(api.calls.some((call) => call.path.includes('from=2026-10-12') && call.path.includes('to=2026-10-18') && call.path.includes('size=100'))).toBe(true)
    expect(api.calls.some((call) => call.path === '/sports')).toBe(false)
    expect(api.calls.some((call) => call.path.startsWith('/coaches') || call.path.startsWith('/manager'))).toBe(false)
  })
  it('navigates by week, updates the URL, and fetches the selected Monday-to-Sunday range', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp('/member/schedule?date=2026-10-16')
    await screen.findByText('Basketball Foundations')
    expect(screen.getByLabelText('Choose week')).toHaveValue('2026-W42')
    await user.click(screen.getByRole('button', { name: 'Next week' }))
    await waitFor(() => expect(api.calls.some((call) => call.path.includes('from=2026-10-19') && call.path.includes('to=2026-10-25'))).toBe(true))
    expect(screen.getByLabelText('Choose week')).toHaveValue('2026-W43')
    expect(screen.queryByLabelText('Sport')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Training format')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Choose week'), { target: { value: '2026-W44' } })
    await waitFor(() => expect(api.calls.some((call) => call.path.includes('from=2026-10-26') && call.path.includes('to=2026-11-01'))).toBe(true))
  })
  it('rejects manager access to member schedule before browse requests', async () => {
    const api = backend(MANAGER)
    renderApp('/member/schedule')
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(api.calls.every((call) => call.path === '/users/me')).toBe(true)
  })
})
