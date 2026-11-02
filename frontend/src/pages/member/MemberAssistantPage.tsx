import type { ComponentType } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { assistantEnabled } from '../../config/env'
import { safeMemberReturnTo } from './assistantNavigation'

/** Integration contract for Phat's chat screen. Authentication is enforced by the parent route. */
export interface MemberAssistantChatProps {
  returnTo: string
}

export function MemberAssistantLink({ onNavigate }: { onNavigate?: () => void }) {
  const location = useLocation()
  if (!assistantEnabled()) return null
  const active = location.pathname === '/member/assistant'
  const returnTo = `${location.pathname}${location.search}${location.hash}`
  const href = active ? `${location.pathname}${location.search}` : `/member/assistant?returnTo=${encodeURIComponent(returnTo)}`
  return (
    <Link
      className={'workspace-header__nav-link' + (active ? ' workspace-header__nav-link--active' : '')}
      aria-current={active ? 'page' : undefined}
      onClick={onNavigate}
      to={href}
    >
      Open AI Assistant
    </Link>
  )
}

export function MemberAssistantPage({ Chat }: { Chat?: ComponentType<MemberAssistantChatProps> }) {
  const [params] = useSearchParams()
  const returnTo = safeMemberReturnTo(params.get('returnTo'))
  return (
    <div className="training-page">
      <Link to={returnTo}>Back to previous page</Link>
      <PageHeader title="AI Assistant" />
      {Chat ? <Chat returnTo={returnTo} /> : (
        <EmptyState
          title="Chat is not connected yet"
          description="AI Assistant chat is awaiting integration. Return to continue viewing your training information."
        />
      )}
    </div>
  )
}
