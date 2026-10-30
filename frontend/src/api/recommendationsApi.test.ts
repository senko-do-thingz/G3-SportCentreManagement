import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import { recommendationsApi, type RecommendationInput, type RecommendationReviewInput } from './recommendationsApi'

vi.mock('./client', () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())

describe('Flow 5 recommendation API', () => {
  it('uses coach-only endpoints and never accepts a coach actor id', async () => {
    vi.mocked(api.get).mockResolvedValue([])
    await recommendationsApi.list()
    expect(api.get).toHaveBeenCalledWith('/coach/ai-recommendations', { signal: undefined })
  })

  it('sends bounded consented generation input', async () => {
    vi.mocked(api.post).mockResolvedValue({})
    const input: RecommendationInput = { memberId: 2, goal: 'Ball control', fitnessLevel: 'BEGINNER', currentFocus: 'Passing', sessionDurationMinutes: 60, consentConfirmed: true }
    await recommendationsApi.generate(input)
    expect(api.post).toHaveBeenCalledWith('/coach/ai-recommendations', input)
  })

  it('requires an explicit review decision and revision before creating a draft', async () => {
    vi.mocked(api.put).mockResolvedValue({})
    vi.mocked(api.post).mockResolvedValue({})
    const review: RecommendationReviewInput = { exercises: [], coachAdjustments: 'Reduce intensity', decision: 'APPROVE', expectedRevision: 4 }
    await recommendationsApi.review(9, review)
    await recommendationsApi.createDraft(9, 5)
    expect(api.put).toHaveBeenCalledWith('/coach/ai-recommendations/9/review', review)
    expect(api.post).toHaveBeenCalledWith('/coach/ai-recommendations/9/training-plan-draft', { expectedRevision: 5 })
  })
})
