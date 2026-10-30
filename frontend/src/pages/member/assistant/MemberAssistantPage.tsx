import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../../auth/useAuth'
import { Button, Card, PageHeader } from '../../../components/ui'
import { buttonClassName } from '../../../components/ui/buttonClassName'
import { assistantEnabled } from '../../../config/env'
import { AssistantHost } from './AssistantHost'
import { safeReturnPath, useAssistantChoice } from './assistantPreference'

/**
 * Member assistant page (NX-31, Flow 6). The route lives under /member, so the existing
 * role gate already sends a signed-out visitor to Log in and shows Access denied to every other
 * role before this component renders. The page adds three things around the chat slot:
 * the feature switch, an explicit opt-in screen and a safe way back to where the member came from.
 * It does not call any service and never touches booking or payment.
 */
export function MemberAssistantPage() {
  const { user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [choice, setChoice] = useAssistantChoice(user?.id)
  const back = safeReturnPath((location.state as { from?: unknown } | null)?.from) ?? '/member'

  if (!assistantEnabled()) return <Navigate to="/member" replace />

  return (
    <div className="page">
      <PageHeader
        eyebrow="Optional"
        title="Member Assistant"
        description="A helper for questions about your own membership and training."
        actions={<Link to={back} className={buttonClassName('secondary', 'md')}>Back</Link>}
      />
      {choice === 'on' ? (
        <>
          <AssistantHost />
          <div className="assistant-actions">
            <Button
              variant="ghost"
              onClick={() => {
                setChoice('off')
                navigate(back, { replace: true })
              }}
            >
              Turn off the assistant
            </Button>
          </div>
        </>
      ) : (
        <Card title="Before you start" aria-label="Before you start">
          <ul className="assistant-points">
            <li>The assistant only answers questions about your own account.</li>
            <li>It is separate from booking and payment: it does not book sessions, take payments or change anything.</li>
            <li>It is optional. You can turn it off at any time and nothing else in the member area depends on it.</li>
          </ul>
          <div className="assistant-actions">
            <Button onClick={() => setChoice('on')}>Turn on the assistant</Button>
            <Link to={back} className={buttonClassName('ghost', 'md')}>Not now</Link>
          </div>
          {choice === 'off' && <p className="assistant-note">You turned the assistant off. You can turn it on again here.</p>}
        </Card>
      )}
    </div>
  )
}
