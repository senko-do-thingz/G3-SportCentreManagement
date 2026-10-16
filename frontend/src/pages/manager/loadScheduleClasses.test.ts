import { afterEach, describe, expect, it, vi } from 'vitest'
import { managerClassesApi, type ManagerClass } from '../../api/managerClassesApi'
import { MAX_SCHEDULE_CLASS_OPTIONS, MAX_SCHEDULE_CLASS_PAGES, SCHEDULE_CLASS_PAGE_SIZE, loadScheduleClasses } from './loadScheduleClasses'

const firstClass: ManagerClass = { id: 5, code: 'CL-005', name: 'Badminton foundations', sportId: 1, sportName: 'Badminton', ageGroupId: 1, ageGroupLabel: 'Adults', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', maxMembers: 20, upcomingSessions: 1, active: true, createdAt: '', updatedAt: '' }
const secondClass: ManagerClass = { ...firstClass, id: 6, code: 'CL-006', name: 'Badminton advanced' }
function page(content: ManagerClass[], current: number, totalPages: number) {
  return { content, page: current, size: SCHEDULE_CLASS_PAGE_SIZE, totalElements: (totalPages - 1) * SCHEDULE_CLASS_PAGE_SIZE + 1, totalPages }
}

afterEach(() => vi.restoreAllMocks())

describe('schedule class selector pagination', () => {
  it('loads every page while forwarding the screen abort signal', async () => {
    const signal = new AbortController().signal
    const list = vi.spyOn(managerClassesApi, 'list')
      .mockResolvedValueOnce(page([firstClass], 0, 2))
      .mockResolvedValueOnce(page([secondClass], 1, 2))

    await expect(loadScheduleClasses(signal)).resolves.toEqual([firstClass, secondClass])
    expect(list).toHaveBeenNthCalledWith(1, { size: SCHEDULE_CLASS_PAGE_SIZE }, signal)
    expect(list).toHaveBeenNthCalledWith(2, { size: SCHEDULE_CLASS_PAGE_SIZE, page: 1 }, signal)
  })

  it('stops before requesting another page when the screen is aborted', async () => {
    const controller = new AbortController()
    const list = vi.spyOn(managerClassesApi, 'list').mockImplementationOnce(async () => {
      controller.abort()
      return page([firstClass], 0, 2)
    })

    await expect(loadScheduleClasses(controller.signal)).rejects.toMatchObject({ name: 'AbortError' })
    expect(list).toHaveBeenCalledTimes(1)
  })

  it('surfaces a later page failure so the schedule can be retried', async () => {
    const signal = new AbortController().signal
    const list = vi.spyOn(managerClassesApi, 'list')
      .mockResolvedValueOnce(page([firstClass], 0, 2))
      .mockRejectedValueOnce(new Error('Class page 2 failed.'))

    await expect(loadScheduleClasses(signal)).rejects.toThrow('Class page 2 failed.')
    expect(list).toHaveBeenCalledTimes(2)
  })

  it('rejects an oversized list instead of silently omitting classes', async () => {
    const signal = new AbortController().signal
    const list = vi.spyOn(managerClassesApi, 'list').mockResolvedValueOnce(page([firstClass], 0, MAX_SCHEDULE_CLASS_PAGES + 1))

    await expect(loadScheduleClasses(signal)).rejects.toThrow(MAX_SCHEDULE_CLASS_OPTIONS.toLocaleString())
    expect(list).toHaveBeenCalledTimes(1)
  })
})
