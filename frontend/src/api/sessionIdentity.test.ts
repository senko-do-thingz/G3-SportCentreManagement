import { describe, expect, it, vi, afterEach } from 'vitest'
import { parseSessionId, optionalSessionId, sessionIdText } from './sessionIdentity'
import { memberSessionsApi } from './memberSessionsApi'
import { staffTrainingApi } from './staffTrainingApi'
import { api } from './client'

afterEach(() => vi.restoreAllMocks())

describe('canonical public Session ID', () => {
  it.each(['031126FB01', '290200TE99', '010100SW01', '311299BK01', '031126BM02', '031126VB03'])('accepts %s', value => {
    expect(parseSessionId(value)).toBe(value)
  })
  it.each([5, '5', '031126fb01', '310226FB01', '290201TE01', '031126XX01', '031126BM00', '031126BM100', '031126BM01 ', null])('rejects malformed or surrogate identity %s', value => {
    expect(() => parseSessionId(value)).toThrow('Session ID')
  })
  it('keeps omitted legacy identity unavailable instead of inventing an alias', () => {
    expect(optionalSessionId(undefined)).toBeUndefined()
    expect(sessionIdText(undefined)).toBe('Session ID unavailable')
    expect(sessionIdText('5')).toBe('Session ID unavailable')
    expect(sessionIdText('031126FB01')).toBe('031126FB01')
  })
  it('looks up a public code and retains legacy numeric detail navigation', async () => {
    const response = { session: { id: 5, sessionId: '031126FB01', sportId: 1, sportName: 'Football', trainingFormat: 'SELF_TRAINING', status: 'PUBLISHED', classId: null, coachId: null, facilityId: 2, className: null, coachName: null, facilityName: 'Pitch', level: null, sessionDate: '2026-11-03', startTime: '09:00', endTime: '10:00', capacity: 10, bookedCount: 0, freeSeats: 10, full: false }, memberContext: false, alreadyBooked: null, eligible: null, bookingStatus: null, reason: null, suitableRegistrations: [] }
    const request = vi.spyOn(api, 'get').mockResolvedValue(response)
    expect((await memberSessionsApi.detailByCode(parseSessionId('031126FB01'))).session.sessionId).toBe('031126FB01')
    expect(request).toHaveBeenCalledWith('/sessions/by-code/031126FB01', { signal: undefined })
    await memberSessionsApi.detail(5)
    expect(request).toHaveBeenLastCalledWith('/sessions/5', { signal: undefined })
  })
  it('uses code roster routes and retains numeric booking IDs in attendance writes', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValue([])
    const post = vi.spyOn(api, 'post').mockResolvedValue({ id: 8 })
    const signal = new AbortController().signal
    await staffTrainingApi.coachRoster('031126BM01', signal)
    expect(get).toHaveBeenCalledWith('/coach/training/sessions/by-code/031126BM01/members', { signal })
    await staffTrainingApi.coachAttendance(12, { status: 'PRESENT', notes: null })
    expect(post).toHaveBeenCalledWith('/coach/training/attendance', { bookingId: 12, status: 'PRESENT', notes: null })
  })
})
