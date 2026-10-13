import { useCallback, useEffect, useState } from 'react'
import { StaleSessionError } from '../../api/errors'

export type Resource<T> =
  | { status: 'loading' }
  | { status: 'ready'; data: T }
  | { status: 'error'; error: unknown }

/**
 * Loads one resource for a member screen and exposes a reload function for the Retry button.
 *
 * - The request is cancelled when the screen unmounts or a newer load starts, so a late answer
 *   never overwrites a newer one.
 * - A StaleSessionError (the signed in account changed while loading) is ignored: the whole role
 *   area is mounted again for the new account.
 * - State is only set from the async callbacks and from reload (an event handler), never
 *   synchronously inside the effect body.
 */
export function useMemberResource<T>(load: (signal: AbortSignal) => Promise<T>): [Resource<T>, () => void] {
  const [resource, setResource] = useState<Resource<T>>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) {
          setResource({ status: 'ready', data })
        }
      },
      (error: unknown) => {
        if (controller.signal.aborted || error instanceof StaleSessionError) {
          return
        }
        setResource({ status: 'error', error })
      },
    )
    return () => controller.abort()
  }, [load, attempt])

  const reload = useCallback(() => {
    setResource({ status: 'loading' })
    setAttempt((current) => current + 1)
  }, [])

  return [resource, reload]
}
