import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { trainingApi } from '../../api/trainingApi'
import { CoachTrainingPage } from './CoachTrainingPage'
import { MemberTrainingPage } from './MemberTrainingPage'
import { ReceptionTrainingAttendancePage } from './ReceptionTrainingAttendancePage'

vi.mock('../../api/trainingApi', async (loadOriginal) => {
  const original = await loadOriginal<typeof import('../../api/trainingApi')>()
  return { ...original, trainingApi: {
    coachMembers: vi.fn(), coachClasses: vi.fn(), coachPlans: vi.fn(), savePlan: vi.fn(), publishPlan: vi.fn(),
    coachResults: vi.fn(), saveResult: vi.fn(), correctResult: vi.fn(), sessionAttendance: vi.fn(),
    saveAttendance: vi.fn(), correctAttendance: vi.fn(), memberPlans: vi.fn(), memberResults: vi.fn(),
    memberAttendance: vi.fn(), receptionAttendance: vi.fn(),
  } }
})

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(trainingApi.coachMembers).mockResolvedValue([{ id: 2, memberCode: 'MB-002', name: 'Taylor Nguyen', activePlanCount: 0 }])
  vi.mocked(trainingApi.coachClasses).mockResolvedValue([{ id: 7, name: 'Basketball Foundations', sportName: 'Basketball' }])
  vi.mocked(trainingApi.coachPlans).mockResolvedValue([])
  vi.mocked(trainingApi.coachResults).mockResolvedValue([])
  vi.mocked(trainingApi.memberPlans).mockResolvedValue([])
  vi.mocked(trainingApi.memberResults).mockResolvedValue([])
  vi.mocked(trainingApi.memberAttendance).mockResolvedValue([])
})

describe('Flow 4 screens', () => {
  it('lets a coach choose an individual or class audience', async () => {
    const user = userEvent.setup()
    render(<CoachTrainingPage />)
    await screen.findByRole('heading', { name: 'No training plans' })
    await user.click(screen.getByRole('button', { name: 'Create plan' }))
    expect(screen.getByRole('combobox', { name: /Plan audience/ })).toHaveValue('MEMBER')
    expect(screen.getByRole('combobox', { name: /Member/ })).toBeInTheDocument()
    await user.selectOptions(screen.getByRole('combobox', { name: /Plan audience/ }), 'CLASS')
    expect(screen.getByRole('combobox', { name: /Class/ })).toHaveValue('7')
  })

  it('keeps member plans, results and attendance as separate private views', async () => {
    const user = userEvent.setup()
    render(<MemberTrainingPage />)
    expect(await screen.findByRole('heading', { name: 'No training plan yet' })).toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: 'Results & feedback' }))
    expect(await screen.findByRole('heading', { name: 'No training results yet' })).toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: 'Attendance' }))
    expect(await screen.findByRole('heading', { name: 'No attendance records yet' })).toBeInTheDocument()
  })

  it('does not allow receptionist attendance submission without a booking id', () => {
    render(<ReceptionTrainingAttendancePage />)
    expect(screen.getByRole('button', { name: 'Record attendance' })).toBeDisabled()
    expect(screen.getByText(/does not create a booking or deduct a visit a second time/i)).toBeInTheDocument()
  })
})
