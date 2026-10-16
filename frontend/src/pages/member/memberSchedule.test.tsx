import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MEMBER, MANAGER } from '../../test/fakeBackend'

export const browseSessionFixture = { id: 5, sportId: 3, sportName: 'Basketball', classId: 1, className: 'Basketball Foundations', trainingFormat: 'COACH_LED', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', facilityId: 1, facilityName: 'Court A', sessionDate: '2026-10-16', startTime: '16:00:00', endTime: '17:00:00', capacity: 20, bookedCount: 2, freeSeats: 18, full: false, status: 'PUBLISHED' }
function backend(profile = MEMBER) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, profile)
    if (call.path === '/sports') return json(200, [{ id: 3, code: 'BASKETBALL', name: 'Basketball', venueType: 'INDOOR' }])
    if (call.path.startsWith('/sessions?')) return json(200, { content: [browseSessionFixture], page: 0, size: 100, totalElements: 1, totalPages: 1 })
    return backendError(404, 'Unexpected test request')
  })
}
beforeEach(() => { vi.stubEnv('VITE_MANAGER_PREVIEW', 'false'); tokenStorage.startSession({ accessToken: 'member-test', refreshToken: 'refresh-test' }) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })
describe('FE-08 member calendar', () => {
  it('renders all seven days and reads the complete week with member-specific fields', async () => {
    const api = backend()
    renderApp('/member/schedule?date=2026-10-16')
    expect(await screen.findByText('Basketball Foundations')).toBeInTheDocument()
    expect(screen.getByText('18 spots left')).toBeInTheDocument()
    expect(screen.getAllByRole('region', { name: /\d{2}\/10\/2026/ })).toHaveLength(7)
    expect(api.calls.some((call) => call.path.includes('from=2026-10-12') && call.path.includes('to=2026-10-18') && call.path.includes('size=100'))).toBe(true)
    expect(api.calls.some((call) => call.path.startsWith('/coaches') || call.path.startsWith('/manager'))).toBe(false)
  })
  it('switches day/week and filters through URL-driven API queries', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp('/member/schedule?date=2026-10-16')
    await screen.findByText('Basketball Foundations')
    await user.selectOptions(screen.getByLabelText('Schedule view'), 'day')
    await waitFor(() => expect(api.calls.some((call) => call.path.includes('from=2026-10-16') && call.path.includes('to=2026-10-16'))).toBe(true))
    await user.selectOptions(screen.getByLabelText('Sport'), '3')
    await user.selectOptions(screen.getByLabelText('Training format'), 'SELF_TRAINING')
    await waitFor(() => expect(api.calls.some((call) => call.path.includes('sportId=3') && call.path.includes('format=SELF_TRAINING'))).toBe(true))
  })
  it('rejects manager access to member schedule before browse requests', async () => {
    const api = backend(MANAGER)
    renderApp('/member/schedule')
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(api.calls.every((call) => call.path === '/users/me')).toBe(true)
  })
})
