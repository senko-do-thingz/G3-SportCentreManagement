import { useContext, useEffect, useRef, useState, type FormEvent } from 'react'
import { AuthContext } from '../../auth/authContext'
import { trainingGuidanceApi, type Consultation } from '../../api/trainingGuidanceApi'
import { Button, Input, Card } from '../../components/ui'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { safeMemberReturnTo } from './assistantNavigation'
import { useTrainingAction } from '../training/trainingFormState'
import { ActionMessage } from '../training/TrainingFormFields'

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
      Open center assistant
    </Link>
  )
}

// One example per supported theme: schedules, center services and general exercise.
const ASSISTANT_EXAMPLES = [
  'Which classes are available in the next two weeks?',
  'What are the package prices?',
  'How should I warm up before practice?',
] as const

const SOURCE_KIND_LABELS: Record<string, string> = {
  CLASS_SESSION: 'Live session catalog',
  SPORT_PACKAGE: 'Live package catalog',
  PRODUCT_GUIDANCE: 'Booking guidance',
  CURATED_FAQ: 'Curated center FAQ (fixed text)',
  HUMAN_COACH: 'Human coach',
}
const sourceKindLabel = (kind: string) => SOURCE_KIND_LABELS[kind] ?? 'Center information'

export function MemberAssistantPage() {
  const auth = useContext(AuthContext)
  return <CenterConsultationWorkspace key={auth?.user?.id ?? 'anonymous'} />
}

function CenterConsultationWorkspace() {
  const [params] = useSearchParams()
  const returnTo = safeMemberReturnTo(params.get('returnTo'))
  const [optIn, setOptIn] = useState(false)
  const [result, setResult] = useState<Consultation | null>(null)
  const [question, setQuestion] = useState('')
  const action = useTrainingAction()
  const resultRef = useRef<HTMLDivElement>(null)
  const enabled = import.meta.env.VITE_TRAINING_CONSULTATION_ENABLED !== 'false'
  // Bring a new answer into view on short desktop screens such as 1280x720.
  useEffect(() => { if (result) resultRef.current?.scrollIntoView?.({ block: 'start' }) }, [result])
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!optIn || !enabled) return
    void action.run(async () => { setResult(await trainingGuidanceApi.consult(question.trim())) }, 'Center information loaded.')
  }
  return (
    <div className="training-page">
      <Link to={returnTo}>Back to previous page</Link>
      <PageHeader title="Center assistant" description="KNOWLEDGE_BASED automated consultation. It uses center data and fixed guidance; no generative AI provider is configured." />
      <p>No conversation history is stored. Your question and answer stay on this page until you leave or turn consultation off. No data is sent to a model provider. General exercise answers are fixed curated text, not personal or medical advice.</p>
      {!enabled ? <EmptyState title="Automated consultation is disabled" /> : <Card title="Ask about the center">
        <label><input type="checkbox" checked={optIn} disabled={action.pending} onChange={event => { setOptIn(event.target.checked); setResult(null); setQuestion('') }} /> Enable automated center consultation</label>
        <div role="group" aria-label="Example questions"><p>Example questions (select one to fill the question field):</p>
          <div className="training-actions">{ASSISTANT_EXAMPLES.map(example => <Button key={example} type="button" variant="secondary" disabled={!optIn || action.pending} onClick={() => setQuestion(example)}>{example}</Button>)}</div>
        </div>
        <form className="training-form" onSubmit={submit}><Input label="Center question" name="question" value={question} onChange={event => setQuestion(event.target.value)} maxLength={500} required disabled={!optIn || action.pending} />
          <Button type="submit" disabled={!optIn} loading={action.pending}>Ask center assistant</Button></form><ActionMessage {...action} />
      </Card>}
      {result && <div ref={resultRef}><Card title={result.status === 'ANSWERED' ? 'Grounded center answer' : 'Answer unavailable'}><div role="status"><p>{result.mode} - {result.answer}</p></div>
        {result.status === 'NO_ANSWER' && <p>This assistant does not answer personal, medical or private questions. Ask your human coach or reception.</p>}
        {result.sources.length > 0 && <ul className="training-list">{result.sources.map(source => <li key={`${source.kind}-${source.id}`}><h2>{source.title}</h2><p>Source type: {sourceKindLabel(source.kind)}</p><p>{source.detail}</p><Link to={source.path}>Open source: {source.title}</Link></li>)}</ul>}
      </Card></div>}
      <Link to="/member/training/questions">Ask your human coach</Link>
    </div>
  )
}
