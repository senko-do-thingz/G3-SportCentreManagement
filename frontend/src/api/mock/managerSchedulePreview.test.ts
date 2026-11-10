import { describe, expect, it } from 'vitest'
import { schedulePreviewResponse } from './managerSchedulePreview'
import { financePreviewResponse } from './managerFinancePreview'
import { parseSessionId } from '../sessionIdentity'
import { addDays, centerNow } from '../../pages/manager/scheduleModel'
import type { ManagerSession } from '../managerSessionsApi'
import type { ManagerFinanceOverview, ManagerRevenueReport } from '../managerFinanceApi'

describe('isolated manager schedule and finance preview', () => {
  it('creates sessions with valid 10-character sessionId codes and rejects an overlapping batch without partial writes', async () => {
    const date = addDays(centerNow().date, 14)
    const body = { date, startTime: '13:00:00', endTime: '14:00:00', facilityId: 1, coachId: 3, capacity: 10, trainingType: 'COACH_LED' }
    const first = schedulePreviewResponse('/manager/classes/1/sessions', { method: 'POST', body }, [], false)!
    expect(first.status).toBe(201)
    const created = await first.json() as ManagerSession[]
    expect(created[0].sessionId).toBeDefined()
    expect(parseSessionId(created[0].sessionId)).toBe(created[0].sessionId)
    expect(created[0].sportName).toBe('Basketball')
    const overlap = schedulePreviewResponse('/manager/classes/1/sessions', { method: 'POST', body }, [], false)!
    expect(overlap.status).toBe(409)
    const error = await overlap.json()
    expect(error.details.conflicts[0]).toMatchObject({ sessionId: created[0].id, date, conflictType: 'BOTH' })
    const listed = await schedulePreviewResponse('/manager/sessions', { query: { from: date, to: date } }, [], false)!.json()
    expect(listed).toHaveLength(1)
  })

  it('provides valid 10-character session codes for attendance overview and backfill preview', async () => {
    const overviewRes = schedulePreviewResponse('/manager/training/attendance/overview', { method: 'GET', query: {} }, [], false)!
    expect(overviewRes.status).toBe(200)
    const overview = await overviewRes.json() as { sessions: { sessionCode: string }[] }
    expect(overview.sessions.length).toBeGreaterThan(0)
    for (const s of overview.sessions) {
      expect(parseSessionId(s.sessionCode)).toBe(s.sessionCode)
    }

    const backfillRes = schedulePreviewResponse('/manager/training/attendance/backfill', { method: 'GET' }, [], false)!
    expect(backfillRes.status).toBe(200)
    const backfill = await backfillRes.json() as { bookings: { sessionCode: string }[] }
    expect(backfill.bookings.length).toBeGreaterThan(0)
    for (const b of backfill.bookings) {
      expect(parseSessionId(b.sessionCode)).toBe(b.sessionCode)
    }
  })

  it('reconciles finance overview, transactions, and revenue reports across months and groupBy modes', async () => {
    for (const [month, from, to, expectedPaid, expectedCount] of [
      ['2026-10', '2026-10-01', '2026-10-31', 16110000, 12],
      ['2026-11', '2026-11-01', '2026-11-30', 4260000, 5],
    ] as const) {
      const overview = await financePreviewResponse('/manager/finance/overview', { method: 'GET', query: { month } }, false)!.json() as ManagerFinanceOverview
      expect(overview.month).toBe(month)
      expect(overview.paidRevenue).toBe(expectedPaid)
      expect(overview.paidTransactions).toBe(expectedCount)

      const txPage = await financePreviewResponse('/manager/finance/transactions', { method: 'GET', query: { from, to, status: 'SUCCESS', size: 50 } }, false)!.json() as { content: { amount: number; settlementType: string; method: string }[]; totalElements: number }
      expect(txPage.totalElements).toBe(expectedCount)
      expect(txPage.content.reduce((sum, row) => sum + row.amount, 0)).toBe(expectedPaid)
      expect(txPage.content.every((row) => row.settlementType === 'ACTUAL')).toBe(true)
      expect(txPage.content.every((row) => ['BANK_TRANSFER', 'CASH'].includes(row.method))).toBe(true)

      for (const groupBy of ['ITEM_TYPE', 'METHOD', 'SPORT'] as const) {
        const report = await financePreviewResponse('/manager/finance/revenue', { method: 'GET', query: { from, to, groupBy } }, false)!.json() as ManagerRevenueReport
        expect(report.groupBy).toBe(groupBy)
        expect(report.totalRevenue).toBe(expectedPaid)
        expect(report.totalTransactions).toBe(expectedCount)
        expect(report.rows.reduce((sum, row) => sum + row.revenue, 0)).toBe(expectedPaid)
        expect(report.rows.reduce((sum, row) => sum + row.transactions, 0)).toBe(expectedCount)
      }
    }

    const byMethod = await financePreviewResponse('/manager/finance/revenue', { method: 'GET', query: { from: '2026-11-01', to: '2026-11-30', groupBy: 'METHOD' } }, false)!.json() as ManagerRevenueReport
    expect(byMethod.rows.map((r) => r.label)).toEqual(['Bank transfer', 'Cash'])

    const bySport = await financePreviewResponse('/manager/finance/revenue', { method: 'GET', query: { from: '2026-11-01', to: '2026-11-30', groupBy: 'SPORT' } }, false)!.json() as ManagerRevenueReport
    expect(bySport.rows.map((r) => r.label)).toContain('Swimming')
    expect(bySport.rows.map((r) => r.label)).toContain('Football')
  })
})
