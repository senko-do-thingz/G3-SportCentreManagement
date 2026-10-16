import { ApiError } from '../../api/errors'
import { ErrorState } from '../../components/ui'
import { ForbiddenPage } from '../public/ForbiddenPage'

export function BrowseError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return error instanceof ApiError && error.status === 403 ? <ForbiddenPage /> : <ErrorState error={error} onRetry={onRetry} />
}
