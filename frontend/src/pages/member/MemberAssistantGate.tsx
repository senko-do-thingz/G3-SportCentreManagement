import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { Button, Card } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { PageHeader } from '../../components/ui/PageHeader'
import { assistantEnabled } from '../../config/env'
import { safeMemberReturnTo } from './assistantNavigation'
import { useAssistantChoice } from './assistantPreference'
import { MemberAssistantPage } from './MemberAssistantPage'

/**
 * Route element of /member/assistant (NX-31). The parent route already sends a signed-out visitor
 * to Log in and shows Access denied to every role except Member, so this gate only adds what the
 * member sees first:
 * - the feature switch (VITE_ASSISTANT_ENABLED); when it is off the route goes to the member home;
 * - an explicit opt-in screen, remembered per account in this browser, and a Turn off button;
 * - the existing MemberAssistantPage (chat slot and safe return link) once the member opted in.
 * It calls no service and never touches booking or payment.
 */
export function MemberAssistantGate() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [choice, setChoice] = useAssistantChoice(user?.id)
  const returnTo = safeMemberReturnTo(params.get('returnTo'))

  if (!assistantEnabled()) return <Navigate to="/member" replace />

  if (choice === 'on') {
    return (
      <>
        <MemberAssistantPage />
        <div className="assistant-actions">
          <Button
            variant="ghost"
            onClick={() => {
              setChoice('off')
              navigate(returnTo, { replace: true })
            }}
          >
            Turn off the assistant
          </Button>
        </div>
      </>
    )
  }

  return (
    <div className="training-page">
      <Link to={returnTo}>Back to previous page</Link>
      <PageHeader title="AI Assistant" description="An optional helper for questions about your own membership and training." />
      <Card title="Before you start" aria-label="Before you start">
        <ul className="assistant-points">
          <li>The assistant only answers questions about your own account.</li>
          <li>It is separate from booking and payment: it does not book sessions, take payments or change anything.</li>
          <li>It is optional. You can turn it off at any time and nothing else in the member area depends on it.</li>
        </ul>
        <div className="assistant-actions">
          <Button onClick={() => setChoice('on')}>Turn on the assistant</Button>
          <Link to={returnTo} className={buttonClassName('ghost', 'md')}>Not now</Link>
        </div>
        {choice === 'off' && <p className="assistant-note">You turned the assistant off. You can turn it on again here.</p>}
      </Card>
    </div>
  )
}
