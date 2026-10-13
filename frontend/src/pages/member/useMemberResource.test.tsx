import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SESSION_CHANGED_MESSAGE, StaleSessionError } from '../../api/errors'
import { useMemberResource, type ResourceLoader } from './useMemberResource'

/** FE-02 review round 1 (M4): loading, cancelling and retrying one member resource. */

interface Pending<T> {
  signal: AbortSignal
  resolve: (value: T) => void
  reject: (error: unknown) => void
}

/** A loader whose calls stay open until the test settles them, in any order. */
function controlledLoader<T>() {
  const calls: Pending<T>[] = []
  const load: ResourceLoader<T> = (signal) =>
    new Promise<T>((resolve, reject) => {
      calls.push({ signal, resolve, reject })
    })
  return { load, calls }
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('FE-02 useMemberResource', () => {
  it('TC-FE02-100 starts loading and becomes ready with the data', async () => {
    const { load, calls } = controlledLoader<string>()
    const { result } = renderHook(() => useMemberResource(load))
    expect(result.current[0]).toEqual({ status: 'loading' })

    await act(async () => calls[0].resolve('first'))
    expect(result.current[0]).toEqual({ status: 'ready', data: 'first' })
  })

  it('TC-FE02-101 unmount aborts the request and a late answer sets no state', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const { load, calls } = controlledLoader<string>()
    const { result, unmount } = renderHook(() => useMemberResource(load))
    unmount()
    expect(calls[0].signal.aborted).toBe(true)

    await act(async () => calls[0].resolve('too late'))
    expect(result.current[0]).toEqual({ status: 'loading' })
    expect(consoleError).not.toHaveBeenCalled()
  })

  it('TC-FE02-102 reload shows loading at once and a late answer of the old request is ignored', async () => {
    const { load, calls } = controlledLoader<string>()
    const { result } = renderHook(() => useMemberResource(load))
    await act(async () => calls[0].resolve('first'))

    act(() => result.current[1]())
    expect(result.current[0]).toEqual({ status: 'loading' })
    expect(calls).toHaveLength(2)

    act(() => result.current[1]())
    expect(calls[1].signal.aborted).toBe(true)
    await act(async () => calls[2].resolve('newest'))
    await act(async () => calls[1].resolve('older'))
    expect(result.current[0]).toEqual({ status: 'ready', data: 'newest' })
  })

  it('TC-FE02-103 a new loader resets to loading and never shows the old data', async () => {
    const first = controlledLoader<string>()
    const second = controlledLoader<string>()
    const { result, rerender } = renderHook(({ load }) => useMemberResource(load), {
      initialProps: { load: first.load },
    })
    await act(async () => first.calls[0].resolve('old data'))
    expect(result.current[0]).toEqual({ status: 'ready', data: 'old data' })

    rerender({ load: second.load })
    expect(result.current[0]).toEqual({ status: 'loading' })
    expect(first.calls[0].signal.aborted).toBe(true)
    await act(async () => second.calls[0].resolve('new data'))
    expect(result.current[0]).toEqual({ status: 'ready', data: 'new data' })
  })

  it('TC-FE02-104 a StaleSessionError ends in the error state, and Retry loads again', async () => {
    const { load, calls } = controlledLoader<string>()
    const { result } = renderHook(() => useMemberResource(load))
    await act(async () => calls[0].reject(new StaleSessionError()))

    const resource = result.current[0]
    expect(resource.status).toBe('error')
    expect(resource.status === 'error' && (resource.error as Error).message).toBe(SESSION_CHANGED_MESSAGE)

    act(() => result.current[1]())
    expect(result.current[0]).toEqual({ status: 'loading' })
    await act(async () => calls[1].resolve('after retry'))
    await waitFor(() => expect(result.current[0]).toEqual({ status: 'ready', data: 'after retry' }))
  })
})
