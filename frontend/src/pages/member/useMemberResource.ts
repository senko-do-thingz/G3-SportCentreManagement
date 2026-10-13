import { useCallback, useEffect, useState } from 'react'

export type Resource<T> =
  | { status: 'loading' }
  | { status: 'ready'; data: T }
  | { status: 'error'; error: unknown }

export type ResourceLoader<T> = (signal: AbortSignal) => Promise<T>

const LOADING = { status: 'loading' } as const

/** The answer of one load, tagged with the loader and attempt it belongs to. */
interface Settled<T> {
  load: ResourceLoader<T>
  attempt: number
  resource: Resource<T>
}

/**
 * Loads one resource for a member screen and exposes a reload function for the Retry button.
 *
 * load must be a stable reference: a module level function or a function wrapped in useCallback.
 * A new function on every render starts a new request on every render and the effect loops.
 *
 * - The request is cancelled when the screen unmounts, when reload is called and when load
 *   changes. A cancelled request never updates the state, so a late answer cannot overwrite a
 *   newer one and nothing is set after unmount.
 * - Every answer is tagged with the loader and the attempt it belongs to. While the current
 *   loader and attempt have no answer yet the hook returns loading, so after reload or after load
 *   changes the old data is never shown for the new load.
 * - A StaleSessionError (the signed in account changed while loading) ends in the error state
 *   with its message and a Retry. The role area is mounted again only when the user id changes;
 *   when the same account signs in again in another tab nothing remounts, so waiting for a
 *   remount could leave an endless spinner.
 */
export function useMemberResource<T>(load: ResourceLoader<T>): [Resource<T>, () => void] {
  const [attempt, setAttempt] = useState(0)
  const [settled, setSettled] = useState<Settled<T> | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) {
          setSettled({ load, attempt, resource: { status: 'ready', data } })
        }
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setSettled({ load, attempt, resource: { status: 'error', error } })
        }
      },
    )
    return () => controller.abort()
  }, [load, attempt])

  const reload = useCallback(() => {
    setAttempt((current) => current + 1)
  }, [])

  const resource: Resource<T> = settled && settled.load === load && settled.attempt === attempt ? settled.resource : LOADING
  return [resource, reload]
}
