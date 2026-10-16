import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'

const session = { id: 5, sportId: 3, sportName: 'Basketball', classId: 1, className: 'Basketball Foundations', trainingFormat: 'COACH_LED', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', facilityId: 1, facilityName: 'Court A', sessionDate: '2026-10-16', startTime: '16:00:00', endTime: '17:00:00', capacity: 20, bookedCount: 2, freeSeats: 18, full: false, status: 'PUBLISHED' }
function backend(many = false) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path === '/sports') return json(200, [{ id: 3, code: 'BASKETBALL', name: 'Basketball' }])
    if (call.path.startsWith('/sessions?')) {
      const query = new URLSearchParams(call.path.split('?')[1]), page = Number(query.get('page')), size = Number(query.get('size'))
      const rows = many ? Array.from({ length: 101 }, (_, index) => ({ ...session, id: index + 1, coachId: index === 100 ? 8 : 3, coachName: index === 100 ? 'Later Page Coach' : session.coachName })) : [session]
      return json(200, { content: rows.slice(page * size, (page + 1) * size), page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) })
    }
    return backendError(404, 'Unexpected test request')
  })
}
beforeEach(() => { vi.stubEnv('VITE_MANAGER_PREVIEW', 'false'); tokenStorage.startSession({ accessToken: 'member-test', refreshToken: 'refresh-test' }) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })
describe('FE-08 find classes', () => {
  it('restores URL filters, combines changes and preserves a local return URL', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp('/member/classes?from=2026-10-16&to=2026-11-15&sportId=3&level=BEGINNER')
    await screen.findByText('Basketball Foundations')
    expect(screen.getByLabelText('Level')).toHaveValue('BEGINNER')
    await user.selectOptions(screen.getByLabelText('Training format'), 'COACH_LED')
    await screen.findByRole('option', { name: 'Quan Coach' })
    await user.selectOptions(screen.getByLabelText('Coach'), '3')
    await waitFor(() => expect(api.calls.some((call) => call.path.includes('coachId=3') && call.path.includes('format=COACH_LED') && call.path.includes('level=BEGINNER') && call.path.includes('size=20'))).toBe(true))
    const target = screen.getByRole('link', { name: 'View session' }).getAttribute('href')!
    expect(new URLSearchParams(target.split('?')[1]).get('returnTo')).toContain('coachId=3')
    expect(api.calls.some((call) => call.path.startsWith('/coaches'))).toBe(false)
  })
  it('does not request sessions for reversed dates', async () => {
    const api = backend()
    renderApp('/member/classes?from=2026-10-20&to=2026-10-19')
    expect(await screen.findByText('From date must not be after to date.')).toBeInTheDocument()
    expect(api.calls.some((call) => call.path.startsWith('/sessions'))).toBe(false)
  })
  it('loads coach options beyond the first 100 rows and retains a deep-linked coach', async () => {
    const api = backend(true)
    renderApp('/member/classes?from=2026-10-16&to=2026-11-15&coachId=99')
    expect(await screen.findByRole('option', { name: 'Later Page Coach' })).toBeInTheDocument()
    expect(screen.getByLabelText('Coach')).toHaveValue('99')
    expect(screen.getByRole('option', { name: 'Coach #99 (selected)' })).toBeInTheDocument()
    expect(api.calls.some((call) => call.path.includes('page=1') && call.path.includes('size=100') && !call.path.includes('coachId'))).toBe(true)
  })
})
