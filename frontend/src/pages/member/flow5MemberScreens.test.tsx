import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => { vi.unstubAllGlobals(); localStorage.clear() })

const profile = { ...MEMBER, phone: '0901234567', dateOfBirth: '2000-01-01', address: null, mainGoal: 'BUILD_SKILLS', currentLevel: 'BEGINNER' }
const sports = [{ id: 1, code: 'FOOTBALL', name: 'Football', venueType: 'OUTDOOR', isActive: true }, { id: 2, code: 'BADMINTON', name: 'Badminton', venueType: 'INDOOR', isActive: true }]
const context = { memberName: 'Taylor', mainGoal: 'BUILD_SKILLS', currentLevel: 'BEGINNER', dateOfBirthKnown: true, preferredSportIds: [1],
  activePackages: [{ sportName: 'Football', packageName: 'Football 8', trainingFormat: 'COACH_LED', remainingSessions: 7, totalSessions: 8, endDate: '2026-11-30' }] }
const empty = { mode: 'RULE_BASED', ruleVersion: 'class-rules-v1', bookingEligibilityChecked: false, message: 'Preference matches only.', page: 0, size: 10, totalMatches: 0, items: [] }

it('F5-01 saves preferred sports and hands format and available days to the recommendations', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, profile)
    if (call.path === '/sports') return json(200, sports)
    if (call.path === '/member/training/context') return json(200, context)
    if (call.path === '/member/training/preferred-sports') return json(200, { ...context, preferredSportIds: (call.body as { sportIds: number[] }).sportIds })
    if (call.path === '/member/training/sport-levels') return json(200, [{ sportId: 1, sportName: 'Football', level: null }, { sportId: 2, sportName: 'Badminton', level: null }])
    if (call.path.startsWith('/member/training/recommendations')) return json(200, empty)
    if (call.path.startsWith('/member/training/saved-recommendations')) return json(200, [])
    return backendError(404, 'Not found')
  })
  const user = userEvent.setup()
  renderApp('/member/training/goals')
  expect(await screen.findByText('Coach-led / 7 of 8 sessions until 2026-11-30')).toBeInTheDocument()
  expect(screen.getByRole('checkbox', { name: 'Football' })).toBeChecked()
  await user.click(screen.getByRole('checkbox', { name: 'Badminton' }))
  await user.selectOptions(screen.getByLabelText('Training format'), 'COACH_LED')
  await user.click(screen.getByRole('checkbox', { name: 'Monday' }))
  await user.click(screen.getByRole('checkbox', { name: 'Thursday' }))
  await user.click(screen.getByRole('button', { name: 'Get recommendations' }))
  expect(await screen.findByRole('heading', { name: 'Class recommendations' })).toBeInTheDocument()
  expect(calls.find(call => call.path === '/member/training/preferred-sports')?.body).toEqual({ sportIds: [1, 2] })
  expect(calls.some(call => call.method === 'PUT' && call.path === '/users/me')).toBe(false)
  await waitFor(() => expect(calls.some(call => call.path.startsWith('/member/training/recommendations?') && call.path.includes('format=COACH_LED') && call.path.includes('days=MON%2CTHU'))).toBe(true))
  expect(screen.getByLabelText('Training format')).toHaveValue('COACH_LED')
  expect(screen.getByRole('checkbox', { name: 'Monday' })).toBeChecked()
})

it('F5-04 lists suitable sessions with booking state and package check, and links only bookable ones', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path === '/member/training/recommendations/classes/2/sessions') return json(200, {
      classId: 2, className: 'Football Fundamentals', sportName: 'Football', level: 'BEGINNER', coachName: 'Daniel', ageGroup: '16+',
      sessions: [
        { sessionId: 7, sessionCode: '051026FB01', date: '2026-11-05', startTime: '17:00:00', endTime: '18:30:00', trainingType: 'COACH_LED', facilityName: 'Pitch 1', freeSeats: 3, availability: 'AVAILABLE', packageCheck: 'ELIGIBLE' },
        { sessionId: 8, sessionCode: '121026FB01', date: '2026-11-12', startTime: '17:00:00', endTime: '18:30:00', trainingType: 'COACH_LED', facilityName: 'Pitch 1', freeSeats: 0, availability: 'FULL', packageCheck: 'ELIGIBLE' },
        { sessionId: 9, sessionCode: '191026FB01', date: '2026-11-19', startTime: '17:00:00', endTime: '18:30:00', trainingType: 'COACH_LED', facilityName: 'Pitch 1', freeSeats: 5, availability: 'OVERLAP', packageCheck: 'NO_SESSIONS_LEFT' },
      ],
      packages: [{ sportName: 'Football', packageName: 'Football 8', trainingFormat: 'COACH_LED', remainingSessions: 7, totalSessions: 8, endDate: '2026-11-30' }],
    })
    return backendError(404, 'Not found')
  })
  renderApp('/member/training/recommendations/classes/2')
  expect(await screen.findByText('Football Fundamentals / Daniel')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /^Book session/ })).toHaveAttribute('href', '/member/sessions/051026FB01')
  expect(screen.getAllByRole('link', { name: /^Book session/ })).toHaveLength(1)
  expect(screen.getAllByText('Full').length).toBeGreaterThan(0)
  expect(screen.getByText('Overlaps one of your bookings')).toBeInTheDocument()
  expect(screen.getByText('No sessions left')).toBeInTheDocument()
  expect(screen.getByText(/7 of 8 sessions available/)).toBeInTheDocument()
  expect(calls.every(call => call.method === 'GET')).toBe(true)
})
