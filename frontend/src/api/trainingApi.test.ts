import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import { trainingApi, type SavePlanInput, type SaveResultInput } from './trainingApi'

vi.mock('./client', () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))

beforeEach(() => vi.clearAllMocks())

describe('Flow 4 training API', () => {
  it('uses owner-scoped member and coach read endpoints', async () => {
    vi.mocked(api.get).mockResolvedValue([])
    const signal = new AbortController().signal
    await trainingApi.coachMembers(signal)
    await trainingApi.coachClasses(signal)
    await trainingApi.coachPlans(8, signal)
    await trainingApi.memberPlans(signal)
    await trainingApi.memberResults(signal)
    await trainingApi.memberAttendance(signal)
    expect(api.get).toHaveBeenNthCalledWith(1, '/coach/training/members', { signal })
    expect(api.get).toHaveBeenNthCalledWith(2, '/coach/training/classes', { signal })
    expect(api.get).toHaveBeenNthCalledWith(3, '/coach/training/plans', { query: { memberId: 8 }, signal })
    expect(api.get).toHaveBeenNthCalledWith(4, '/members/me/training/plans', { signal })
    expect(api.get).toHaveBeenNthCalledWith(5, '/members/me/training/results', { signal })
    expect(api.get).toHaveBeenNthCalledWith(6, '/members/me/training/attendance', { signal })
  })

  it('creates individual and class plans and sends revision guards on edits and publish', async () => {
    vi.mocked(api.post).mockResolvedValue({})
    vi.mocked(api.put).mockResolvedValue({})
    const individual: SavePlanInput = { targetType: 'MEMBER', memberId: 4, classId: null, title: 'Personal plan', goal: 'Improve form', startDate: '2026-11-01', endDate: null, exercises: [] }
    const classPlan: SavePlanInput = { ...individual, targetType: 'CLASS', memberId: null, classId: 9, title: 'Class plan', expectedRevision: 3 }
    await trainingApi.savePlan(individual)
    await trainingApi.savePlan(classPlan, 12)
    await trainingApi.publishPlan(12, 3)
    expect(api.post).toHaveBeenNthCalledWith(1, '/coach/training/plans', individual)
    expect(api.put).toHaveBeenCalledWith('/coach/training/plans/12', classPlan)
    expect(api.post).toHaveBeenNthCalledWith(2, '/coach/training/plans/12/publish', { expectedRevision: 3 })
  })

  it('separates initial result and attendance writes from audited corrections', async () => {
    vi.mocked(api.post).mockResolvedValue({})
    vi.mocked(api.put).mockResolvedValue({})
    const result: SaveResultInput = { sessionId: 20, memberId: 4, attendance: 'PRESENT', summary: 'Completed', coachFeedback: 'Good control', metrics: [], expectedRevision: 2, correctionReason: 'Corrected timing' }
    await trainingApi.saveResult(result)
    await trainingApi.correctResult(30, result)
    await trainingApi.saveAttendance(20, [{ bookingId: 40, status: 'PRESENT' }])
    await trainingApi.correctAttendance(50, 'LATE', 'Arrival time verified', 2)
    expect(api.post).toHaveBeenCalledWith('/coach/training/results', result)
    expect(api.put).toHaveBeenCalledWith('/coach/training/results/30', result)
    expect(api.put).toHaveBeenCalledWith('/coach/training/sessions/20/attendance', { rows: [{ bookingId: 40, status: 'PRESENT' }] })
    expect(api.put).toHaveBeenCalledWith('/coach/training/attendance/50', { status: 'LATE', reason: 'Arrival time verified', expectedRevision: 2 })
  })

  it('records receptionist self-training attendance without accepting an actor id', async () => {
    vi.mocked(api.post).mockResolvedValue({})
    await trainingApi.receptionAttendance(77, 'PRESENT')
    expect(api.post).toHaveBeenCalledWith('/bookings/self-training/attendance', { bookingId: 77, status: 'PRESENT' })
  })

  it('rejects non-array collection responses', async () => {
    vi.mocked(api.get).mockResolvedValue({ content: [] })
    await expect(trainingApi.memberPlans()).rejects.toThrow('unexpected training response')
  })
})
