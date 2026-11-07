import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import type { BrowseSession } from '../../api/memberSessionsApi'
import type { MemberBookingRecord } from '../../api/memberBookingsApi'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MEMBER, MANAGER } from '../../test/fakeBackend'

export const browseSessionFixture: BrowseSession = { id: 5, sportId: 3, sportName: 'Basketball', classId: 1, className: 'Basketball Foundations', trainingFormat: 'COACH_LED', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', facilityId: 1, facilityName: 'Court A', sessionDate: '2026-10-16', startTime: '16:00:00', endTime: '17:00:00', capacity: 20, bookedCount: 2, freeSeats: 18, full: false, status: 'PUBLISHED' }

const confirmed: MemberBookingRecord = {
  id: 41, bookingCode: 'BK-41', sessionId: 5, sessionCode: '161026BK01' as MemberBookingRecord['sessionCode'], sessionDate: '2026-10-16', startTime: '16:00:00', endTime: '17:00:00',
  sportName: 'Basketball', sportId: 3, classId: 1, className: 'Basketball Foundations', coachId: 3, coachName: 'Quan Coach', facilityId: 1, facilityName: 'Court A',
  trainingType: 'COACH_LED', memberId: MEMBER.id, memberCode: 'MB-2', memberFullName: 'Taylor Nguyen', packageRegistrationId: 9, packageId: 4, packageName: 'Basketball 10',
  status: 'CONFIRMED', bookedAt: '2026-10-10T09:00:00', cancelledAt: null, cancelReason: null, cancelledById: null, cancelledByName: null, cancelledByRole: null,
}
const selfTraining: MemberBookingRecord = { ...confirmed, id: 42, bookingCode: 'BK-42', sessionId: 6, sessionCode: '141026SW01' as MemberBookingRecord['sessionCode'], sessionDate: '2026-10-14', startTime: '07:00:00', endTime: '08:00:00', className: null, sportName: 'Swimming', coachId: null, coachName: null, facilityName: 'Pool 1', trainingType: 'SELF_TRAINING' }
const cancelled: MemberBookingRecord = { ...confirmed, id: 43, bookingCode: 'BK-43', sessionId: 7, sessionCode: '161026BK02' as MemberBookingRecord['sessionCode'], className: 'Yoga Flow', status: 'CANCELLED', cancelledAt: '2026-10-11T10:00:00' }

function backend(bookings: unknown = [confirmed, selfTraining, cancelled], profile = MEMBER) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, profile)
    if (call.path === '/bookings/my') return json(200, bookings)
    return backendError(404, 'Unexpected test request')
  })
}
beforeEach(() => { vi.stubEnv('VITE_MANAGER_PREVIEW', 'false'); tokenStorage.startSession({ accessToken: 'member-test', refreshToken: 'refresh-test' }) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })

describe('My Schedule shows only the member own CONFIRMED bookings', () => {
  it('renders confirmed coach-led and self-training sessions with date, time, sport, coach, facility and Session ID', async () => {
    const api = backend()
    renderApp('/member/schedule?date=2026-10-16')
    expect(await screen.findByRole('heading', { name: 'My Schedule' })).toBeInTheDocument()
    const table = await screen.findByRole('table', { name: 'Weekly schedule' })
    expect(within(table).getByText('Basketball Foundations')).toBeInTheDocument()
    expect(within(table).getByText('16:00 - 17:00')).toBeInTheDocument()
    expect(within(table).getByText('Coach: Quan Coach')).toBeInTheDocument()
    expect(within(table).getByText('Court A')).toBeInTheDocument()
    expect(within(table).getByText('161026BK01')).toBeInTheDocument()
    expect(within(table).getByText('Swimming Self-training')).toBeInTheDocument()
    expect(within(table).getByText('Self-training - No assigned coach')).toBeInTheDocument()
    expect(within(table).getByText('Pool 1')).toBeInTheDocument()
    expect(screen.queryByText('Yoga Flow')).not.toBeInTheDocument()
    expect(screen.getAllByRole('columnheader')).toHaveLength(8)
    expect(screen.getByRole('link', { name: 'Basketball Foundations' })).toHaveAttribute('href', expect.stringContaining('/member/sessions/161026BK01?'))
    expect(api.calls.some((call) => call.path.startsWith('/sessions'))).toBe(false)
  })

  it('shows the latest session date and time returned by the server after a manager reschedule', async () => {
    backend([{ ...confirmed, sessionDate: '2026-10-15', startTime: '18:30:00', endTime: '19:30:00' }])
    renderApp('/member/schedule?date=2026-10-16')
    expect(await screen.findByText('18:30 - 19:30')).toBeInTheDocument()
    expect(screen.queryByText('16:00 - 17:00')).not.toBeInTheDocument()
  })

  it('tells a member with no booking at all that they have no schedule yet, with Find Classes and assistant help', async () => {
    backend([cancelled])
    renderApp('/member/schedule?date=2026-10-16')
    expect(await screen.findByText("You don't have any scheduled sessions yet.")).toBeInTheDocument()
    expect(screen.queryByText('No sessions scheduled for this week.')).not.toBeInTheDocument()
    const frame = document.querySelector('.schedule-content-frame') as HTMLElement
    expect(within(frame).getByRole('link', { name: 'Find Classes' })).toHaveAttribute('href', '/member/classes')
    expect(within(frame).getByRole('link', { name: 'Get Help Finding Classes' })).toHaveAttribute('href', '/member/assistant')
  })

  it('separates an empty week from having no bookings and navigates between weeks without refetching', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp('/member/schedule?date=2026-10-16')
    await screen.findByText('Basketball Foundations')
    expect(screen.getByLabelText('Choose week')).toHaveValue('2026-W42')
    await user.click(screen.getByRole('button', { name: 'Next week' }))
    expect(await screen.findByText('No sessions scheduled for this week.')).toBeInTheDocument()
    expect(screen.queryByText("You don't have any scheduled sessions yet.")).not.toBeInTheDocument()
    expect(screen.getByLabelText('Choose week')).toHaveValue('2026-W43')
    await user.click(screen.getByRole('button', { name: 'Previous week' }))
    expect(await screen.findByText('Basketball Foundations')).toBeInTheDocument()
    expect(api.calls.filter((call) => call.path === '/bookings/my')).toHaveLength(1)
  })

  it('distinguishes a server failure from an empty schedule and supports retry', async () => {
    let fail = true
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/bookings/my') {
        if (fail) { fail = false; return backendError(503, 'Schedule service unavailable') }
        return json(200, [])
      }
      return backendError(404, 'Unexpected test request')
    })
    const user = userEvent.setup()
    renderApp('/member/schedule?date=2026-10-16')
    expect(await screen.findByText('Schedule service unavailable')).toBeInTheDocument()
    expect(screen.queryByText("You don't have any scheduled sessions yet.")).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText("You don't have any scheduled sessions yet.")).toBeInTheDocument()
    expect(document.querySelector('.member-main:has(.member-schedule-theme)')).toBeInTheDocument()
  })

  it('never shows the previous account bookings after another tab switches members', async () => {
    let releaseFirst: (response: Response) => void = () => undefined
    const { calls } = installFakeFetch((call) => {
      const second = call.authorization === 'Bearer second-member'
      if (call.path === '/users/me') return json(200, second ? { ...MEMBER, id: 3, fullName: 'Second member' } : MEMBER)
      if (call.path === '/bookings/my') {
        if (second) return json(200, [])
        return new Promise<Response>((resolve) => { releaseFirst = resolve })
      }
      return json(200, [])
    })
    const user = userEvent.setup()
    renderApp('/member/schedule?date=2026-10-16')
    await waitFor(() => expect(calls.some((call) => call.path === '/bookings/my')).toBe(true))
    const oldSession = tokenStorage.getSessionId()
    await act(async () => {
      localStorage.setItem(tokenStorage.accessKey, 'second-member')
      localStorage.setItem(tokenStorage.refreshKey, 'second-refresh')
      localStorage.setItem(tokenStorage.sessionKey, 'second-session')
      window.dispatchEvent(new StorageEvent('storage', { key: tokenStorage.sessionKey, oldValue: oldSession, newValue: 'second-session' }))
    })
    expect(await screen.findByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
    await act(async () => releaseFirst(json(200, [confirmed])))
    await user.click(screen.getAllByRole('link', { name: /My Schedule/i })[0])
    expect(await screen.findByText("You don't have any scheduled sessions yet.")).toBeInTheDocument()
    expect(screen.queryByText('Basketball Foundations')).not.toBeInTheDocument()
    expect(calls.filter((call) => call.path === '/bookings/my' && call.authorization === 'Bearer second-member').length).toBeGreaterThan(0)
  })

  it('rejects manager access to the member schedule before any booking request', async () => {
    const api = backend(undefined, MANAGER)
    renderApp('/member/schedule')
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    await waitFor(() => expect(api.calls.every((call) => call.path === '/users/me')).toBe(true))
  })
})
