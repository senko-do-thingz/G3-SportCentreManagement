import { api } from './client'
import { StaleSessionError } from './errors'
import { UNEXPECTED_RESPONSE_MESSAGE, type RequestSignal } from './memberApi'
import { tokenStorage } from './tokenStorage'

interface Item {
  id: number
  memberId: number
  status: string
}

export interface PendingSource<T extends Item> {
  path: string
  readRows: (value: unknown) => Item[]
  details: (memberId: number, options: RequestSignal) => Promise<T[]>
}

const PAGE_SIZE = 100
const DETAIL_CONCURRENCY = 4
const CHANGED_QUEUE = 'Pending requests changed while loading. Refresh the queue and try again.'

/** A complete oldest-first load, with fixed page counts and a shared per-load worker bound. */
export async function loadPendingQueues<T extends Item>(sources: PendingSource<T>[], options: RequestSignal): Promise<T[][]> {
  const controller = new AbortController()
  const forwardAbort = () => controller.abort(options.signal?.reason)
  const epoch = tokenStorage.getEpoch()
  options.signal?.addEventListener('abort', forwardAbort, { once: true })
  if (options.signal?.aborted) forwardAbort()

  const check = () => {
    if (epoch !== tokenStorage.getEpoch()) throw new StaleSessionError()
    controller.signal.throwIfAborted()
  }
  const guarded = async <R,>(work: () => Promise<R>): Promise<R> => {
    try { check(); return await work() } catch (error) { controller.abort(); throw error }
  }

  try {
    // Two queue kinds may page concurrently; hydration starts after both paging plans complete.
    const queues = await Promise.all(sources.map((source) => guarded(async () => {
      const rows: Item[] = []
      const ids = new Set<number>()
      let snapshot: { size: number; totalPages: number; totalElements: number } | null = null
      let page = 0
      do {
        check()
        const value = await api.get<unknown>(source.path, { signal: controller.signal, query: { page, size: PAGE_SIZE } })
        check()
        if (!value || typeof value !== 'object') throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
        const answer = value as Record<string, unknown>
        const { number, size, totalPages, totalElements } = answer
        if (number !== page || typeof size !== 'number' || !Number.isSafeInteger(size) || size < 1 || size > PAGE_SIZE
          || typeof totalPages !== 'number' || !Number.isSafeInteger(totalPages) || totalPages < 0
          || typeof totalElements !== 'number' || !Number.isSafeInteger(totalElements) || totalElements < 0
          || totalPages !== Math.ceil(totalElements / size)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
        if (snapshot && (snapshot.size !== size || snapshot.totalPages !== totalPages || snapshot.totalElements !== totalElements)) {
          throw new Error(CHANGED_QUEUE)
        }
        snapshot = { size, totalPages, totalElements }
        const content = source.readRows(answer.content)
        if (content.length !== Math.min(size, Math.max(0, totalElements - page * size))) throw new Error(CHANGED_QUEUE)
        for (const row of content) {
          if (ids.has(row.id)) throw new Error(CHANGED_QUEUE)
          ids.add(row.id); rows.push(row)
        }
        page += 1
      } while (page < snapshot.totalPages)
      return rows
    })))

    const details = sources.map(() => new Map<number, T[]>())
    const jobs = queues.flatMap((rows, source) => [...new Set(rows.map((row) => row.memberId))].map((memberId) => ({ source, memberId })))
    let next = 0
    await Promise.all(Array.from({ length: Math.min(DETAIL_CONCURRENCY, jobs.length) }, () => guarded(async () => {
      while (next < jobs.length) {
        check()
        const job = jobs[next++]
        const items = await sources[job.source].details(job.memberId, { signal: controller.signal })
        check()
        details[job.source].set(job.memberId, items)
      }
    })))
    check()
    return queues.map((rows, source) => rows.flatMap((row) => {
      const item = details[source].get(row.memberId)?.find((item) => item.id === row.id && item.memberId === row.memberId)
      if (!item) throw new Error('Pending request details are unavailable. Refresh the queue and try again.')
      // A paid or cancelled item is stale, rather than an incomplete lookup.
      return item.status === 'PENDING_PAYMENT' ? [item] : []
    }))
  } finally {
    controller.abort()
    options.signal?.removeEventListener('abort', forwardAbort)
  }
}
