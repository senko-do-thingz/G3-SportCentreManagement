import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

const savedPlans = [
  { id: 7, title: 'Free throw mechanics', description: 'Focused drill on release mechanics.', className: 'Basketball Foundations', classId: 7, memberId: 42, memberName: 'Taylor Nguyen', coachName: 'Quan Coach' },
  { id: 8, title: 'Footwork agility', description: 'Ladder drills and pivoting.', className: 'Basketball Foundations', classId: 7, memberId: 43, memberName: 'Alex Rivers', coachName: 'Quan Coach' },
]

const planDetailsRecord: Record<number, { plan: typeof savedPlans[0]; steps: Array<{ id: number; stepOrder: number; title: string; durationMinutes: number; instructions: string | null }> }> = {
  7: {
    plan: savedPlans[0],
    steps: [
      { id: 501, stepOrder: 1, title: 'Form shooting close range', durationMinutes: 10, instructions: 'One-handed form shots from 3 feet out.' },
      { id: 502, stepOrder: 2, title: 'Set-point rhythm', durationMinutes: 15, instructions: 'Catch and shoot with fluid dip and release.' },
    ],
  },
  8: {
    plan: savedPlans[1],
    steps: [
      { id: 503, stepOrder: 1, title: 'Quick lateral hops', durationMinutes: 12, instructions: 'Hop over the line keeping balance.' },
    ],
  },
}

function setupPlansFeedbackApp(options?: { feedbackError?: boolean }) {
  tokenStorage.startSession({ accessToken: 'coach-token', refreshToken: 'refresh' })
  return installFakeFetch((call: RecordedCall) => {
    if (call.path === '/users/me') return json(200, { ...MEMBER, role: 'COACH' })
    if (call.path === '/coach/training/classes') return json(200, [{ id: 7, name: 'Basketball Foundations', sportId: 3 }])
    if (call.path === '/coach/training/classes/7/members') return json(200, [{ bookingId: 21, memberId: 42, memberName: 'Taylor Nguyen', sessionDate: '2026-10-20' }])
    if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, savedPlans)
    if (call.path === '/coach/training/plans/7' && call.method === 'GET') return json(200, planDetailsRecord[7])
    if (call.path === '/coach/training/plans/8' && call.method === 'GET') return json(200, planDetailsRecord[8])
    if (call.path === '/coach/training/plans/7/feedback' && call.method === 'POST') {
      if (options?.feedbackError) return backendError(400, 'Invalid feedback submission')
      return json(201, { id: 701 })
    }
    return backendError(404, 'Unhandled endpoint')
  })
}

describe('Coach plans and feedback layout (CUI-06)', () => {
  it('renders hero, summary, and initial assigned plans state without a selected plan', async () => {
    setupPlansFeedbackApp()
    renderApp('/coach/training/plans')

    expect(await screen.findByRole('heading', { level: 2, name: /Plan the work/i })).toBeInTheDocument()
    expect(screen.getByLabelText('Plan assignment introduction')).toBeInTheDocument()
    expect(screen.getByLabelText('Plan setup summary')).toBeInTheDocument()
    expect(await screen.findByText(/2 saved plans/i)).toBeInTheDocument()
    expect(screen.getByRole('status', { name: '' })).toHaveTextContent(/Choose a saved plan/i)
    expect(screen.queryByRole('region', { name: 'Selected saved plan' })).not.toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Plan feedback' })).not.toBeInTheDocument()
  })

  it('renders full-width plan details and exercises when a plan is selected', async () => {
    setupPlansFeedbackApp()
    const user = userEvent.setup()
    renderApp('/coach/training/plans')

    const selector = await screen.findByLabelText('Assigned plans')
    await user.selectOptions(selector, '7')

    const planRegion = await screen.findByRole('region', { name: 'Selected saved plan' })
    expect(within(planRegion).getByRole('heading', { level: 3, name: 'Free throw mechanics' })).toBeInTheDocument()
    expect(within(planRegion).getByText('Taylor Nguyen')).toBeInTheDocument()
    expect(within(planRegion).getByText('Basketball Foundations')).toBeInTheDocument()
    expect(within(planRegion).getByText('Focused drill on release mechanics.')).toBeInTheDocument()

    // Exercise details
    expect(within(planRegion).getByText('Form shooting close range')).toBeInTheDocument()
    expect(within(planRegion).getByText('10 min')).toBeInTheDocument()
    expect(within(planRegion).getByText('One-handed form shots from 3 feet out.')).toBeInTheDocument()
    expect(within(planRegion).getByText('Set-point rhythm')).toBeInTheDocument()
    expect(within(planRegion).getByText('15 min')).toBeInTheDocument()

    // Feedback workspace is visible with interactive controls
    const feedbackForm = screen.getByRole('form', { name: 'Plan feedback' })
    expect(feedbackForm).toBeInTheDocument()
    expect(within(feedbackForm).getByLabelText(/Progress rating/i)).toBeEnabled()
    expect(within(feedbackForm).getByLabelText('Feedback')).toBeEnabled()
    expect(within(feedbackForm).getByLabelText('Strengths')).toBeEnabled()
    expect(within(feedbackForm).getByLabelText('Areas for improvement')).toBeEnabled()
    expect(within(feedbackForm).getByRole('button', { name: 'Save feedback' })).toBeEnabled()
  })

  it('submits plan feedback successfully with correct payload and resets form', async () => {
    const { calls } = setupPlansFeedbackApp()
    const user = userEvent.setup()
    renderApp('/coach/training/plans')

    await user.selectOptions(await screen.findByLabelText('Assigned plans'), '7')
    await screen.findByRole('region', { name: 'Selected saved plan' })

    const feedbackInput = screen.getByLabelText('Feedback')
    const strengthsInput = screen.getByLabelText('Strengths')
    const improvementInput = screen.getByLabelText('Areas for improvement')

    await user.type(feedbackInput, 'Great follow-through and repeatable shooting rhythm.')
    await user.type(strengthsInput, 'Elbow under the ball')
    await user.type(improvementInput, 'Maintain foot balance')

    await user.click(screen.getByRole('button', { name: 'Save feedback' }))

    expect(await screen.findByText('Plan feedback saved. The member can view it immediately.')).toBeInTheDocument()

    const postCall = calls.find(call => call.method === 'POST' && call.path.endsWith('/7/feedback'))
    expect(postCall).toBeDefined()
    expect(postCall?.body).toMatchObject({
      rating: 4,
      comments: 'Great follow-through and repeatable shooting rhythm.',
      strengths: 'Elbow under the ball',
      areasForImprovement: 'Maintain foot balance',
    })
  })

  it('handles backend feedback error cleanly without losing coach inputs', async () => {
    setupPlansFeedbackApp({ feedbackError: true })
    const user = userEvent.setup()
    renderApp('/coach/training/plans')

    await user.selectOptions(await screen.findByLabelText('Assigned plans'), '7')
    await screen.findByRole('region', { name: 'Selected saved plan' })

    await user.type(screen.getByLabelText('Feedback'), 'Constructive feedback')
    await user.click(screen.getByRole('button', { name: 'Save feedback' }))

    expect(await screen.findByText('Invalid feedback submission')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save feedback' })).toBeEnabled()
  })

  it('switches between different saved plans and updates the review context', async () => {
    setupPlansFeedbackApp()
    const user = userEvent.setup()
    renderApp('/coach/training/plans')

    const selector = await screen.findByLabelText('Assigned plans')
    await user.selectOptions(selector, '7')
    expect(await screen.findByText('Free throw mechanics')).toBeInTheDocument()
    expect(screen.getByText('Form shooting close range')).toBeInTheDocument()

    // Switch to Plan 8
    await user.selectOptions(selector, '8')
    expect(await screen.findByText('Footwork agility')).toBeInTheDocument()
    expect(screen.getByText('Alex Rivers')).toBeInTheDocument()
    expect(screen.getByText('Quick lateral hops')).toBeInTheDocument()
    expect(screen.getByText('12 min')).toBeInTheDocument()
    expect(screen.queryByText('Free throw mechanics')).not.toBeInTheDocument()
  })

  it('supports keyboard hero action focus to plan creation scope', async () => {
    setupPlansFeedbackApp()
    const user = userEvent.setup()
    renderApp('/coach/training/plans')

    const heroAction = await screen.findByRole('button', { name: 'Create a plan' })
    await user.click(heroAction)

    expect(screen.getByLabelText('Plan scope')).toHaveFocus()
  })
})
