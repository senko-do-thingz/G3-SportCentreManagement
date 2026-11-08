import { describe, expect, it } from 'vitest'
import { HOME_FAQ, HOME_NOTICES } from './homeContent'

// Public copy must match implemented features: Flow 5 recommendations, Flow 6 assistant and support requests.
describe('home copy', () => {
  it('does not claim implemented member features are unavailable', () => {
    for (const notice of Object.values(HOME_NOTICES)) expect(notice.text).not.toMatch(/not available yet/i)
    expect(HOME_NOTICES.reception.text).toMatch(/Contact reception/)
    expect(HOME_NOTICES.assistant.text).toMatch(/center assistant/)
    expect(HOME_NOTICES.recommendations.text).toMatch(/Recommendations/)
  })

  it('describes the membership card benefit as the tier discount on 30-day and 90-day packages only', () => {
    const card = HOME_FAQ.find((item) => item.question === 'What does a membership card give me?')
    expect(card?.answer).toMatch(/30-day and 90-day/)
    expect(card?.answer).not.toMatch(/perk/i)
  })
})
