import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
afterEach(() => vi.unstubAllGlobals())
it('submits supported preferences, explains provenance and links to real session details', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER)
    : call.path.startsWith('/member/training/recommendations') ? json(200, {
      mode: 'RULE_BASED', ruleVersion: 'class-rules-v1', bookingEligibilityChecked: false, message: 'Check package eligibility in session details.', page: 0, size: 10, totalMatches: 1,
      items: [{ classId: 2, sessionId: 7, sessionCode: '031126BM01', className: 'Coached skills', sportName: 'Badminton', level: 'BEGINNER', coachName: 'Quan', date: '2026-11-03', startTime: '09:00', endTime: '10:00', freeSeats: 4, score: 35, reasons: ['Build skills goal: a coach-led session'] }],
    }) : json(200, []))
  renderApp('/member/training/recommendations')
  expect(await screen.findByText('Build skills goal: a coach-led session')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'View details and booking rules: Coached skills - 031126BM01' })).toHaveAttribute('href', '/member/sessions/031126BM01')
  await userEvent.selectOptions(screen.getByLabelText('Training goal'), 'BUILD_SKILLS')
  await userEvent.click(screen.getByRole('button', { name: 'Find recommendations' }))
  expect(await screen.findByText(/RULE_BASED - Check package eligibility/)).toBeInTheDocument()
  expect(calls.some(call => call.path.includes('goal=BUILD_SKILLS'))).toBe(true)
  expect(calls.every(call => call.method === 'GET')).toBe(true)
})

it('DEF-311-01 keeps an incomplete date from applying the previous recommendation range', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER)
    : call.path.startsWith('/member/training/recommendations') ? json(200, {
      mode: 'RULE_BASED', ruleVersion: 'class-rules-v1', bookingEligibilityChecked: false, message: 'Preference matches only.', page: 0, size: 10, totalMatches: 0, items: [],
    }) : json(200, []))
  renderApp('/member/training/recommendations')
  await screen.findByText('No matching available classes')
  const initialRequests = calls.filter(call => call.path.startsWith('/member/training/recommendations')).length
  const from = screen.getByLabelText('Available from date')
  await userEvent.clear(from)
  await userEvent.type(from, '11/11/')
  await userEvent.click(screen.getByRole('button', { name: 'Find recommendations' }))
  expect(await screen.findByText('Enter a valid date in dd/MM/yyyy format.')).toBeInTheDocument()
  expect(calls.filter(call => call.path.startsWith('/member/training/recommendations'))).toHaveLength(initialRequests)
})

const recommendation = { classId: 2, sessionId: 7, sessionCode: '031126BM01', className: 'Coached skills', sportName: 'Badminton', level: 'ADVANCED', coachName: 'Quan', date: '2026-11-03', startTime: '09:00', endTime: '10:00', freeSeats: 4, score: 40,
  reasons: ['Published upcoming session with 4 available places', 'Matches your saved Badminton level: ADVANCED', 'Fits the 16+ age group'] }
const recommendationsResponse = { mode: 'RULE_BASED', ruleVersion: 'class-rules-v1', bookingEligibilityChecked: false, message: 'Preference matches only.', page: 0, size: 10, totalMatches: 1, items: [recommendation] }

it('saves a recommendation as a bookmark with its reasons and never books', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  let saved: unknown[] = []
  const { calls } = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path.startsWith('/member/training/recommendations')) return json(200, recommendationsResponse)
    if (call.path === '/member/training/saved-recommendations' && call.method === 'POST') {
      saved = [{ id: 5, classId: 2, className: 'Coached skills', sportName: 'Badminton', level: 'ADVANCED', coachName: 'Quan', reason: 'Matches your saved Badminton level: ADVANCED; Fits the 16+ age group', savedAt: '2026-11-02T10:00:00',
        nextSession: { sessionId: 7, sessionCode: '031126BM01', date: '2026-11-03', startTime: '09:00:00', endTime: '10:00:00', freeSeats: 4 } }]
      return json(201, saved[0])
    }
    if (call.path === '/member/training/saved-recommendations') return json(200, saved)
    return json(200, [])
  })
  renderApp('/member/training/recommendations')
  expect(await screen.findByText('No saved recommendations')).toBeInTheDocument()
  await userEvent.click(await screen.findByRole('button', { name: 'Save recommendation: Coached skills' }))
  expect(await screen.findByText('Coached skills saved to your recommendations. Nothing was booked.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Saved: Coached skills' })).toBeDisabled()
  expect(calls.find(call => call.method === 'POST')?.body).toEqual({ classId: 2, reason: 'Matches your saved Badminton level: ADVANCED; Fits the 16+ age group' })
  expect(await screen.findByRole('link', { name: /^Next session: Coached skills/ })).toHaveAttribute('href', '/member/sessions/031126BM01')
  expect(screen.getByText('Why saved: Matches your saved Badminton level: ADVANCED; Fits the 16+ age group')).toBeInTheDocument()
  expect(calls.some(call => call.path.startsWith('/bookings'))).toBe(false)
})

it('removes a saved recommendation and shows when no session is available', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  let saved: unknown[] = [{ id: 9, classId: 3, className: 'Morning swim', sportName: 'Swimming', level: 'BEGINNER', coachName: null, reason: null, savedAt: '2026-11-01T08:00:00', nextSession: null }]
  const { calls } = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path === '/member/training/saved-recommendations/9' && call.method === 'DELETE') { saved = []; return new Response(null, { status: 204 }) }
    if (call.path === '/member/training/saved-recommendations') return json(200, saved)
    if (call.path.startsWith('/member/training/recommendations')) return json(200, { ...recommendationsResponse, totalMatches: 0, items: [] })
    return json(200, [])
  })
  renderApp('/member/training/recommendations')
  expect(await screen.findByText(/^No upcoming session you can book right now/)).toBeInTheDocument()
  expect(screen.getByText('Swimming - Beginner - Self-training')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Remove saved recommendation: Morning swim' }))
  expect(await screen.findByText('No saved recommendations')).toBeInTheDocument()
  expect(calls.some(call => call.method === 'DELETE' && call.path === '/member/training/saved-recommendations/9')).toBe(true)
})
