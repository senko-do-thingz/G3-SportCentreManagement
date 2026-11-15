import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { parseSessionId } from '../../api/sessionIdentity'
import { CoachTimeGridSchedule } from './CoachTimeGridSchedule'
import type { CoachSessionRecord } from '../../api/coachScheduleApi'

const mockSessionA: CoachSessionRecord = {
  id: 101,
  classId: 1,
  className: 'Badminton Foundations',
  sportId: 2,
  sportName: 'Badminton',
  coachId: 5,
  coachName: 'Tran Minh Quan',
  facilityId: 10,
  facilityName: 'Court 1',
  sessionDate: '2026-11-12',
  startTime: '08:00:00',
  endTime: '09:30:00',
  capacity: 20,
  bookedCount: 5,
  status: 'PUBLISHED',
  sessionId: parseSessionId('121126BM01'),
  trainingType: 'COACH_LED',
}

const mockSessionB: CoachSessionRecord = {
  id: 102,
  classId: 1,
  className: 'Badminton Foundations',
  sportId: 2,
  sportName: 'Badminton',
  coachId: 5,
  coachName: 'Tran Minh Quan',
  facilityId: 10,
  facilityName: 'Court 1',
  sessionDate: '2026-11-12',
  startTime: '08:30:00',
  endTime: '10:00:00',
  capacity: 20,
  bookedCount: 3,
  status: 'PUBLISHED',
  sessionId: parseSessionId('121126BM02'),
  trainingType: 'COACH_LED',
}

const mockSessionC: CoachSessionRecord = {
  id: 103,
  classId: 2,
  className: 'Tennis Advanced',
  sportId: 3,
  sportName: 'Tennis',
  coachId: 5,
  coachName: 'Tran Minh Quan',
  facilityId: 11,
  facilityName: 'Tennis Court A',
  sessionDate: '2026-11-13',
  startTime: '14:00:00',
  endTime: '15:30:00',
  capacity: 10,
  bookedCount: 2,
  status: 'PUBLISHED',
  sessionId: parseSessionId('131126TE01'),
  trainingType: 'COACH_LED',
}

describe('CoachTimeGridSchedule component', () => {
  const dates = [
    '2026-11-09',
    '2026-11-10',
    '2026-11-11',
    '2026-11-12',
    '2026-11-13',
    '2026-11-14',
    '2026-11-15',
  ]

  it('renders time gutter hours from 06:00 to 22:00', () => {
    render(
      <CoachTimeGridSchedule
        dates={dates}
        sessions={[]}
        selectedSession={null}
        onSelectSession={vi.fn()}
        today="2026-11-12"
      />
    )

    expect(screen.getByText('06:00')).toBeInTheDocument()
    expect(screen.getByText('12:00')).toBeInTheDocument()
    expect(screen.getByText('21:00')).toBeInTheDocument()
  })

  it('renders conflicting overlapping sessions side-by-side with conflict badges', () => {
    render(
      <CoachTimeGridSchedule
        dates={dates}
        sessions={[mockSessionA, mockSessionB]}
        selectedSession={null}
        onSelectSession={vi.fn()}
        today="2026-11-12"
      />
    )

    const buttons = screen.getAllByRole('button', { name: /Badminton Foundations/ })
    expect(buttons).toHaveLength(2)

    // Ca 1: 08:00 - 09:30 (bat dau sau 06:00 dung 120 phut -> top = 120 * 1.6 = 192px)
    expect(buttons[0]).toHaveStyle({ top: '192px' })
    // Ca 2: 08:30 - 10:00 (bat dau sau 06:00 dung 150 phut -> top = 150 * 1.6 = 240px)
    expect(buttons[1]).toHaveStyle({ top: '240px' })

    // Ca hai ca bi trung nhau phai co the canh bao CONFLICT
    const conflictTags = screen.getAllByText('CONFLICT')
    expect(conflictTags).toHaveLength(2)
  })

  it('renders isolated non-conflicting session without conflict badge', () => {
    render(
      <CoachTimeGridSchedule
        dates={dates}
        sessions={[mockSessionC]}
        selectedSession={null}
        onSelectSession={vi.fn()}
        today="2026-11-12"
      />
    )

    const button = screen.getByRole('button', { name: /Tennis Advanced/ })
    expect(button).toBeInTheDocument()
    // 14:00 cach 06:00 la 8 tieng = 480 phut -> top = 480 * 1.6 = 768px
    expect(button).toHaveStyle({ top: '768px' })
    expect(screen.queryByText('CONFLICT')).not.toBeInTheDocument()
  })

  it('triggers onSelectSession callback when clicked', async () => {
    const handleSelect = vi.fn()
    const user = userEvent.setup()

    render(
      <CoachTimeGridSchedule
        dates={dates}
        sessions={[mockSessionA]}
        selectedSession={null}
        onSelectSession={handleSelect}
        today="2026-11-12"
      />
    )

    await user.click(screen.getByRole('button', { name: /Badminton Foundations/ }))
    expect(handleSelect).toHaveBeenCalledWith(mockSessionA)
  })
})
