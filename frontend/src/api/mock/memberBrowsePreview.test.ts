import { describe, expect, it } from 'vitest'
import { memberBrowsePreview } from './memberBrowsePreview'
import type { Sport } from '../packagesApi'
const sports = [{ id: 3, code: 'BASKETBALL', name: 'Basketball' }] as Sport[]
describe('read-only member browse preview', () => {
  it('provides each eligibility state and a coachless self-training occurrence', async () => {
    const first = await memberBrowsePreview('/sessions/801', {}, sports, null)!.json()
    expect(first.eligible).toBe(true)
    expect((await memberBrowsePreview('/sessions/802', {}, sports, null)!.json()).session.coachId).toBeNull()
    expect((await memberBrowsePreview('/sessions/803', {}, sports, null)!.json()).reason).toBe('FULL')
    expect((await memberBrowsePreview('/sessions/809', {}, sports, null)!.json()).reason).toBe('CAPACITY_UNAVAILABLE')
  })
  it('never handles writes and can simulate empty/error states', async () => {
    expect(memberBrowsePreview('/sessions', { method: 'POST' }, sports, null)).toBeUndefined()
    expect((await memberBrowsePreview('/sessions', {}, sports, 'empty')!.json()).content).toEqual([])
    expect(memberBrowsePreview('/sessions', {}, sports, 'error')!.status).toBe(503)
  })
})
