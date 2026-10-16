import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BrowseSession, EligibilityReason, SessionDetail } from '../../api/memberSessionsApi'
import { coachProfilesApi } from '../../api/coachProfilesApi'
import { ApiError } from '../../api/errors'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MEMBER, MANAGER } from '../../test/fakeBackend'

const session: BrowseSession = { id: 5, sportId: 3, sportName: 'Basketball', classId: 1, className: 'Basketball Foundations', trainingFormat: 'COACH_LED', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', facilityId: 1, facilityName: 'Court A', sessionDate: '2026-10-16', startTime: '16:00:00', endTime: '17:00:00', capacity: 20, bookedCount: 2, freeSeats: 18, full: false, status: 'PUBLISHED' }
const detail: SessionDetail = { session, memberContext: true, alreadyBooked: false, bookingStatus: null, eligible: true, reason: null, suitableRegistrations: [{ registrationId: 1, registrationCode: 'REG-001', packageId: 2, packageName: 'Basketball package', sportId: 3, trainingFormat: 'COACH_LED', startDate: '2026-10-01', endDate: '2026-12-01', remainingSessions: 5 }] }
function backend(value: SessionDetail | number = detail, profile = MEMBER) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, profile)
    if (call.path === '/sessions/5') return typeof value === 'number' ? backendError(value, 'Session not found') : json(200, value)
    if (call.path === '/coaches/3') return json(200, { id: 3, name: 'Quan Coach', headline: null, bio: null, yearsExperience: null, specialties: null, sports: [{ id: 3, name: 'Basketball' }], certifications: [{ id: 1, name: 'Sports Coach Certificate', issuer: 'Federation', verified: false, issuedOn: '2024-01-01', expiresOn: '2025-01-01' }], upcomingSessions: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }, email: 'private@example.com' })
    return backendError(404, 'Unexpected test request')
  })
}
beforeEach(() => { vi.stubEnv('VITE_MANAGER_PREVIEW', 'false'); tokenStorage.startSession({ accessToken: 'member-test', refreshToken: 'refresh-test' }) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })
describe('FE-08 session detail', () => {
  it.each<[EligibilityReason, string]>([['FULL', 'Session Fully Booked'], ['NO_PACKAGE', 'No Eligible Sport Package'], ['NO_SESSIONS_LEFT', 'No Eligible Sport Package'], ['PACKAGE_EXPIRED_BY_THEN', 'Package Does Not Cover This Date'], ['PACKAGE_NOT_STARTED_BY_THEN', 'Package Has Not Started'], ['ALREADY_BOOKED', 'Already Booked'], ['CAPACITY_UNAVAILABLE', 'Availability Cannot Be Confirmed']])('renders backend %s state and never books', async (reason, heading) => {
    const api = backend({ ...detail, eligible: false, reason, suitableRegistrations: [] })
    renderApp('/member/sessions/5?returnTo=%2Fmember%2Fclasses%3Flevel%3DBEGINNER')
    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Book session/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to results' })).toHaveAttribute('href', '/member/classes?level=BEGINNER')
    if (reason === 'NO_PACKAGE' || reason === 'NO_SESSIONS_LEFT') expect(screen.getByRole('link', { name: 'View suitable sport packages' })).toHaveAttribute('href', '/member/packages?sport=3&format=COACH_LED')
    expect(api.calls.every((call) => call.method === 'GET')).toBe(true)
  })
  it('provides an honest booking boundary rather than a dead link or booking mutation', async () => {
    const api = backend()
    renderApp('/member/sessions/5')
    expect(await screen.findByRole('button', { name: 'Book session (coming soon)' })).toBeDisabled()
    expect(screen.getByText(/No booking has been created/)).toBeInTheDocument()
    expect(api.calls.some((call) => call.path.startsWith('/coaches'))).toBe(false)
  })
  it('hides coach UI and never requests a profile for self-training even with a coach ID', async () => {
    const api = backend({ ...detail, session: { ...session, trainingFormat: 'SELF_TRAINING' }, suitableRegistrations: [{ ...detail.suitableRegistrations[0], trainingFormat: 'SELF_TRAINING' }] })
    renderApp('/member/sessions/5')
    expect(await screen.findByRole('heading', { name: 'Self-training access' })).toBeInTheDocument()
    expect(screen.queryByText('Quan Coach')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'View coach profile' })).not.toBeInTheDocument()
    expect(api.calls.some((call) => call.path.startsWith('/coaches'))).toBe(false)
  })
  it('loads public profile only when opened, shows real certificate status and restores focus on Escape', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp('/member/sessions/5')
    const button = await screen.findByRole('button', { name: 'View coach profile' })
    expect(api.calls.some((call) => call.path.startsWith('/coaches'))).toBe(false)
    await user.click(button)
    const modal = screen.getByRole('dialog', { name: 'Coach Profile' })
    expect(await within(modal).findByText('Sports Coach Certificate')).toBeInTheDocument()
    expect(within(modal).getByText('Not verified')).toBeInTheDocument()
    expect(within(modal).getByText('Experience not provided')).toBeInTheDocument()
    expect(within(modal).getByText(/Expired/)).toBeInTheDocument()
    expect(screen.queryByText('private@example.com')).not.toBeInTheDocument()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(button).toHaveFocus()
  })
  it('shows hidden-session 404 without converting it into package ineligibility', async () => {
    backend(404)
    renderApp('/member/sessions/5')
    expect(await screen.findByRole('heading', { name: 'Session Unavailable' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'View suitable sport packages' })).not.toBeInTheDocument()
  })
  it('keeps session detail usable when the public coach profile is private or unavailable', async () => {
    backend()
    vi.spyOn(coachProfilesApi, 'get').mockRejectedValueOnce(new ApiError(404, 'Coach profile is unavailable'))
    const user = userEvent.setup()
    renderApp('/member/sessions/5')
    await user.click(await screen.findByRole('button', { name: 'View coach profile' }))
    expect(await screen.findByText('Coach profile is unavailable')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Close profile' }))
    expect(screen.getByRole('heading', { name: 'Basketball Foundations' })).toBeInTheDocument()
  })
  it.each(['/member/schedule', '/member/classes', '/member/sessions/5'])('guards %s from anonymous users', async (path) => {
    tokenStorage.clear()
    const api = backend(detail, MANAGER)
    renderApp(path)
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(api.calls).toHaveLength(0)
  })
  it.each(['MANAGER', 'RECEPTIONIST', 'COACH'].flatMap((role) => ['/member/schedule', '/member/classes', '/member/sessions/5'].map((path) => [role, path])))('guards %s from %s member route', async (role, path) => {
    const api = backend(detail, { ...MANAGER, role })
    renderApp(path)
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(api.calls.every((call) => call.path === '/users/me')).toBe(true)
  })
})
