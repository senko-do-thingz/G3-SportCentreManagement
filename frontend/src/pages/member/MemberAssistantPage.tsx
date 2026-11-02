import { useCallback, useState, type ComponentType, type FormEvent } from 'react'
import { classLearningApi } from '../../api/classLearningApi'
import { Button, Input, Card } from '../../components/ui'
import { TrainingData } from './TrainingData'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { safeMemberReturnTo } from './assistantNavigation'

/** Integration contract for Phat's chat screen. Authentication is enforced by the parent route. */
export interface MemberAssistantChatProps {
  returnTo: string
}

export function MemberAssistantLink({ onNavigate }: { onNavigate?: () => void }) {
  const location = useLocation()
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
      {Chat ? <Chat returnTo={returnTo} /> : <CatalogGuidance />}

    </div>
  )
}

function CatalogGuidance() {
  const [query, setQuery] = useState<string | null>(null)
  const load = useCallback((signal: AbortSignal) => classLearningApi.guidance(query ?? '', signal), [query])
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setQuery(String(new FormData(event.currentTarget).get('query')).trim())
  }
  return <div className="training-form"><EmptyState title="AI is not configured" description="This page searches center data only. It does not generate AI advice. Ask your coach for supplementary exercise guidance." />
    <form onSubmit={submit} className="training-form"><Input label="Search classes by sport or level" name="query" maxLength={200} required /><Button type="submit">Search catalog</Button></form>
    <Link to="/member/training/questions">Ask your coach</Link>
    {query !== null && <TrainingData load={load}>{data => <Card title="Catalog search results"><p>{data.message}</p><ul>{data.classes.map(item => <li key={item.id}>{item.name} - {item.sportName} - {item.level ?? 'All levels'}</li>)}</ul><Link to="/member/classes">Browse class sessions</Link></Card>}</TrainingData>}
  </div>
}
