import { managerClassesApi, type ManagerClass } from '../../api/managerClassesApi'

export const SCHEDULE_CLASS_PAGE_SIZE = 100
export const MAX_SCHEDULE_CLASS_PAGES = 100
export const MAX_SCHEDULE_CLASS_OPTIONS = SCHEDULE_CLASS_PAGE_SIZE * MAX_SCHEDULE_CLASS_PAGES

export async function loadScheduleClasses(signal: AbortSignal): Promise<ManagerClass[]> {
  const first = await managerClassesApi.list({ size: SCHEDULE_CLASS_PAGE_SIZE }, signal)
  signal.throwIfAborted()

  if (first.totalPages > MAX_SCHEDULE_CLASS_PAGES) {
    throw new Error(`The schedule selector supports up to ${MAX_SCHEDULE_CLASS_OPTIONS.toLocaleString()} classes. Contact the team to narrow the class lookup.`)
  }

  const items = [...first.content]
  for (let page = 1; page < first.totalPages; page += 1) {
    signal.throwIfAborted()
    const next = await managerClassesApi.list({ size: SCHEDULE_CLASS_PAGE_SIZE, page }, signal)
    signal.throwIfAborted()
    items.push(...next.content)
  }
  return items
}
