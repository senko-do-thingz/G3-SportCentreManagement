import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const questions = [
  { key: 'injuryOrMobility', text: 'Do you have an injury or mobility issue that affects how you exercise?' },
  { key: 'activityRestriction', text: 'Has a health professional advised you to limit strenuous activity?' },
  { key: 'exertionSymptoms', text: 'Do you feel dizziness or chest discomfort when you exercise hard?' },
  { key: 'coachShouldKnow', text: 'Is there a health concern your Coach should keep in mind?' },
  { key: 'wantsConsultation', text: 'Would you like to talk with a Coach before your first class?' },
]
const disclaimer = 'This questionnaire is not a medical assessment. The result is a training note only.'
const empty = { version: 'HQ-2026-11', questions, answers: {}, outcome: 'INCOMPLETE', shareWithCoach: false, saved: false,
  updatedAt: null, consentUpdatedAt: null, disclaimer }

afterEach(() => vi.unstubAllGlobals())

function member(handler: (call: RecordedCall) => Response | Promise<Response>) {
  tokenStorage.startSession({ accessToken: 'member', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : handler(call))
}

describe('Member Health & Training questionnaire (MW-02)', () => {
  it('starts optional and incomplete, saves chosen answers and consent, and shows the advisory note', async () => {
    const { calls } = member(call => {
      if (call.path === '/member/health-assessment' && call.method === 'PUT') {
        return json(200, { ...empty, answers: { injuryOrMobility: 'YES', wantsConsultation: 'NO' }, outcome: 'COACH_DISCUSSION_RECOMMENDED',
          shareWithCoach: true, saved: true, updatedAt: '2026-11-08T10:00:00', consentUpdatedAt: '2026-11-08T10:00:00' })
      }
      if (call.path === '/member/health-assessment') return json(200, empty)
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/profile/health')
    expect(await screen.findByRole('heading', { name: 'Health & Training' })).toBeInTheDocument()
    expect(await screen.findByText('Incomplete')).toBeInTheDocument()
    expect(screen.getByText(disclaimer)).toBeInTheDocument()
    expect(screen.getByText('Reception and managers cannot see your answers.')).toBeInTheDocument()

    await user.click(within(screen.getByRole('radiogroup', { name: questions[0].text })).getByLabelText('Yes'))
    await user.click(within(screen.getByRole('radiogroup', { name: questions[4].text })).getByLabelText('No'))
    await user.click(screen.getByLabelText(/Share a short summary/))
    await user.click(screen.getByRole('button', { name: 'Save answers' }))

    expect(await screen.findByText('Your answers were saved.')).toBeInTheDocument()
    expect(screen.getByText('Coach conversation recommended')).toBeInTheDocument()
    expect(screen.getByText('Coach sharing: On')).toBeInTheDocument()
    const put = calls.find(call => call.method === 'PUT')
    expect(put?.body).toEqual({ answers: { injuryOrMobility: 'YES', wantsConsultation: 'NO' }, shareWithCoach: true })
  })

  it('lets the member skip an answered question before saving', async () => {
    const { calls } = member(call => call.method === 'PUT' ? json(200, { ...empty, saved: true, updatedAt: '2026-11-08T10:00:00' })
      : json(200, { ...empty, answers: { coachShouldKnow: 'NO' }, saved: true, updatedAt: '2026-11-08T09:00:00' }))
    const user = userEvent.setup()
    renderApp('/member/profile/health')
    const group = await screen.findByRole('radiogroup', { name: questions[3].text })
    expect(within(group).getByLabelText('No')).toBeChecked()
    await user.click(within(group).getByRole('button', { name: 'Skip' }))
    expect(within(group).getByLabelText('No')).not.toBeChecked()
    await user.click(screen.getByRole('button', { name: 'Save answers' }))
    await screen.findByText('Your answers were saved.')
    expect(calls.find(call => call.method === 'PUT')?.body).toEqual({ answers: {}, shareWithCoach: false })
  })

  it('shows a server validation error and keeps the previous note', async () => {
    member(call => call.method === 'PUT' ? backendError(400, 'Answer must be YES, NO or PREFER_NOT_TO_ANSWER')
      : json(200, { ...empty, answers: { injuryOrMobility: 'NO' }, saved: true, updatedAt: '2026-11-08T09:00:00' }))
    const user = userEvent.setup()
    renderApp('/member/profile/health')
    await user.click(await screen.findByRole('button', { name: 'Save answers' }))
    expect(await screen.findByText('Answer must be YES, NO or PREFER_NOT_TO_ANSWER')).toBeInTheDocument()
    expect(screen.getByText('Incomplete')).toBeInTheDocument()
  })

  it('links to the questionnaire from My Profile', async () => {
    member(call => call.path === '/member/health-assessment' ? json(200, empty) : backendError(404, 'Not found'))
    renderApp('/member/profile')
    expect(await screen.findByRole('link', { name: 'Health & Training' })).toHaveAttribute('href', '/member/profile/health')
  })
})

describe('Coach health and training note (MW-02)', () => {
  const profile = { memberId: 42, memberName: 'Lan', memberCode: 'M42', mainGoal: 'Improve passing', currentLevel: 'BEGINNER' }
  function coach(summary: unknown) {
    tokenStorage.startSession({ accessToken: 'coach', refreshToken: 'refresh' })
    installFakeFetch(call => {
      if (call.path === '/users/me') return json(200, { ...MEMBER, role: 'COACH' })
      if (call.path === '/coach/training/classes') return json(200, [{ id: 1, name: 'Football', sportId: 1 }])
      if (call.path.endsWith('/classes/1/members')) return json(200, [{ bookingId: 1, memberId: 42, memberName: 'Lan' }])
      if (call.path.endsWith('/health-summary')) return json(200, summary)
      if (call.path.endsWith('/profile')) return json(200, profile)
      return json(200, { learner: profile, sessions: [], feedback: [], interpretation: 'Recorded' })
    })
    renderApp('/coach/training/progress?classId=1&memberId=42')
  }

  it('shows only the consented outcome, never individual answers', async () => {
    coach({ memberId: 42, shared: true, outcome: 'COACH_DISCUSSION_RECOMMENDED', consultationRequested: true, updatedAt: '2026-11-08T10:00:00', disclaimer })
    expect(await screen.findByText('Coach conversation recommended')).toBeInTheDocument()
    expect(screen.getByText('The member would like to talk with you before training.')).toBeInTheDocument()
    expect(screen.queryByText(questions[0].text)).not.toBeInTheDocument()
  })

  it('says nothing about health when the member has not shared', async () => {
    coach({ memberId: 42, shared: false, outcome: null, consultationRequested: null, updatedAt: null, disclaimer })
    expect(await screen.findByText('The member has not shared a health and training note.')).toBeInTheDocument()
    expect(screen.queryByText('Coach conversation recommended')).not.toBeInTheDocument()
  })
})
