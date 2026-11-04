import { useEffect, useState, type ReactNode } from 'react'
import { ErrorState } from '../../components/ui/ErrorState'
import { PageLoader } from '../../components/ui/Spinner'

type Loader<T> = (signal: AbortSignal) => Promise<T>
type LoadState<T> = { load: Loader<T> } & ({ data: T } | { error: unknown })

/** Share loading, cancellation and retry behavior across the read-only training screens. */
export function TrainingData<T>({ load, children }: { load: Loader<T>; children: (data: T) => ReactNode }) {
  const [state, setState] = useState<LoadState<T> | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then(
      data => { if (!controller.signal.aborted) setState({ load, data }) },
      error => { if (!controller.signal.aborted) setState({ load, error }) },
    )
    return () => controller.abort()
  }, [load, attempt])

  // A new loader must never display the previous resource while its request is pending.
  if (!state || state.load !== load) return <PageLoader label="Loading training data" />
  if ('data' in state) return children(state.data)

  return (
    <ErrorState
      title="Unable to load training data"
      error={state.error}
      retryLabel="Retry"
      onRetry={() => {
        setState(null)
        setAttempt(value => value + 1)
      }}
    />
  )
}
