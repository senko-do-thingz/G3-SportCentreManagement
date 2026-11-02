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
      items: [{ classId: 2, sessionId: 7, className: 'Coached skills', sportName: 'Badminton', level: 'BEGINNER', coachName: 'Quan', date: '2026-11-03', startTime: '09:00', endTime: '10:00', freeSeats: 4, score: 35, reasons: ['Build skills goal: a coach-led session'] }],
    }) : json(200, []))
  renderApp('/member/training/recommendations')
  expect(await screen.findByText('Build skills goal: a coach-led session')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'View details and booking rules: Coached skills' })).toHaveAttribute('href', '/member/sessions/7')
  await userEvent.selectOptions(screen.getByLabelText('Training goal'), 'BUILD_SKILLS')
  await userEvent.click(screen.getByRole('button', { name: 'Find recommendations' }))
  expect(await screen.findByText(/RULE_BASED - Check package eligibility/)).toBeInTheDocument()
  expect(calls.some(call => call.path.includes('goal=BUILD_SKILLS'))).toBe(true)
  expect(calls.every(call => call.method === 'GET')).toBe(true)
})
