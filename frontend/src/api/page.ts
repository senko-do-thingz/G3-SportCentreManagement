import type { PageResponse, SpringPage } from './types'
import { ApiError } from './errors'
import { isNonnegativeInteger, isPositiveId, isRecord } from './responseValidation'

/** Strict read boundary for session browsing; other existing page consumers retain their contract. */
export function parsePage<T>(source: unknown, parseRow: (row: unknown) => T): PageResponse<T> {
  const fail = () => { throw new ApiError(0, 'Session results could not be read. Refresh and try again.') }
  if (!isRecord(source) || !Array.isArray(source.content)) return fail()
  const page = source.page ?? source.number
  if (!isNonnegativeInteger(page) || !isPositiveId(source.size)
    || !isNonnegativeInteger(source.totalElements) || !isNonnegativeInteger(source.totalPages)
    || ('page' in source && 'number' in source && source.page !== source.number)
    || source.totalPages !== Math.ceil(source.totalElements / source.size)
    || !Number.isSafeInteger(page * source.size)
    || source.content.length !== Math.min(source.size, Math.max(0, source.totalElements - page * source.size))) return fail()
  return { content: source.content.map(parseRow), page, size: source.size, totalElements: source.totalElements, totalPages: source.totalPages }
}

/** Converts either paged shape returned by the backend into PageResponse. */
export function normalizePage<T>(source: PageResponse<T> | SpringPage<T>): PageResponse<T> {
  const page = 'page' in source ? source.page : source.number
  return {
    content: source.content ?? [],
    page: page ?? 0,
    size: source.size ?? source.content?.length ?? 0,
    totalElements: source.totalElements ?? 0,
    totalPages: source.totalPages ?? 0,
  }
}
