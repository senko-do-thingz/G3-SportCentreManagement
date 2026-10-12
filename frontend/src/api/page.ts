import type { PageResponse, SpringPage } from './types'

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
