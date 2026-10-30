import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../../auth/useAuth'
import { Card } from '../../../components/ui'
import { buttonClassName } from '../../../components/ui/buttonClassName'
import { assistantEnabled } from '../../../config/env'
import { ASSISTANT_PATH, useAssistantChoice } from './assistantPreference'

/**
 * Opt-in entry point to the member assistant (NX-31). It is a plain link card: it does not load
 * the assistant, call any service or touch booking and payment, and it renders nothing when the
 * feature is off or the member chose to turn the assistant off. The current page is passed in the
 * router state so the assistant page can send the member back to it.
 */
export function AssistantEntry() {
  const { user } = useAuth()
  const location = useLocation()
  const [choice] = useAssistantChoice(user?.id)
  if (!assistantEnabled() || !user || choice === 'off') return null
  const optedIn = choice === 'on'
  return (
    <Card title="Assistant (optional)" aria-label="Assistant">
      <p className="assistant-entry__text">
        {optedIn
          ? 'Ask a question about your own membership and training.'
          : 'An optional helper for questions about your own membership and training. You choose whether to use it.'}
      </p>
      <Link
        to={ASSISTANT_PATH}
        state={{ from: location.pathname + location.search }}
        className={buttonClassName(optedIn ? 'primary' : 'secondary', 'md')}
      >
        {optedIn ? 'Open assistant' : 'Learn more'}
      </Link>
    </Card>
  )
}
