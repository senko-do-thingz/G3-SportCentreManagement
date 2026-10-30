import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { recommendationsApi } from '../../api/recommendationsApi'
import { trainingApi } from '../../api/trainingApi'
import { CoachRecommendationsPage } from './CoachRecommendationsPage'

vi.mock('../../api/trainingApi', async (loadOriginal) => ({ ...(await loadOriginal<typeof import('../../api/trainingApi')>()), trainingApi: { coachMembers: vi.fn() } }))
vi.mock('../../api/recommendationsApi', async (loadOriginal) => ({ ...(await loadOriginal<typeof import('../../api/recommendationsApi')>()), recommendationsApi: { list: vi.fn(), generate: vi.fn(), review: vi.fn(), createDraft: vi.fn() } }))

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(trainingApi.coachMembers).mockResolvedValue([{ id: 2, memberCode: 'MB-002', name: 'Taylor Nguyen', activePlanCount: 0 }])
  vi.mocked(recommendationsApi.list).mockResolvedValue([])
})

describe('Flow 5 coach recommendation screen', () => {
  it('keeps generation disabled until consent and required context are provided', async () => {
    const user = userEvent.setup()
    render(<CoachRecommendationsPage />)
    const generate = await screen.findByRole('button', { name: 'Generate suggestions' })
    expect(generate).toBeDisabled()
    await user.type(screen.getByRole('textbox', { name: /Training goal/ }), 'Improve passing')
    await user.type(screen.getByRole('textbox', { name: /Current focus/ }), 'Ball control')
    await user.click(screen.getByRole('checkbox'))
    expect(generate).toBeEnabled()
    expect(screen.getByText(/Payment, contact and private staff-note data are excluded/)).toBeInTheDocument()
  })

  it('does not expose a member recommendation workspace', async () => {
    render(<CoachRecommendationsPage />)
    expect(await screen.findByRole('heading', { name: 'AI Workout Suggestions' })).toBeInTheDocument()
    expect(screen.getByText(/Audience/).closest('div')).toHaveTextContent('Coach only')
  })
})
