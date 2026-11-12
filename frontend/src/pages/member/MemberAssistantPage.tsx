import { useContext, useEffect, useRef, useState, type FormEvent } from 'react'
import { AuthContext } from '../../auth/authContext'
import { trainingGuidanceApi, type Consultation } from '../../api/trainingGuidanceApi'
import { Button, Input, Card } from '../../components/ui'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { SporiRobotMark } from './SporiRobotMark'
import { safeMemberReturnTo } from './assistantNavigation'
import { useTrainingAction } from '../training/trainingFormState'
import { ActionMessage } from '../training/TrainingFormFields'

export function MemberAssistantLink({ onNavigate, className, label = 'Open center assistant' }: {
  onNavigate?: () => void
  className?: string
  label?: string
}) {
  const location = useLocation()
  const active = location.pathname === '/member/assistant'
  const returnTo = `${location.pathname}${location.search}${location.hash}`
  const href = active ? `${location.pathname}${location.search}` : `/member/assistant?returnTo=${encodeURIComponent(returnTo)}`
  return (
    <Link
      className={className ?? ('workspace-header__nav-link' + (active ? ' workspace-header__nav-link--active' : ''))}
      aria-current={active ? 'page' : undefined}
      onClick={onNavigate}
      to={href}
    >
      {label}
    </Link>
  )
}

// One example per supported theme: schedules, center services, general exercise and the Member's own records.
const ASSISTANT_EXAMPLES = [
  'Which classes are available in the next two weeks?',
  'What are the package prices?',
  'How should I warm up before practice?',
  'How many sessions do I have left?',
  'What is my homework?',
] as const

const SOURCE_KIND_LABELS: Record<string, string> = {
  CLASS_SESSION: 'Live session catalog',
  SPORT_PACKAGE: 'Live package catalog',
  PRODUCT_GUIDANCE: 'Booking guidance',
  CURATED_FAQ: 'Curated center FAQ (fixed text)',
  HUMAN_COACH: 'Human coach',
  MY_PACKAGE: 'Your package record',
  MY_CARD: 'Your membership card',
  MY_BOOKING: 'Your booking',
  COACH_FEEDBACK: 'Coach feedback (as recorded)',
  HOMEWORK: 'Coach homework (as recorded)',
  SUPPORT: 'Reception support',
}
const MODE_LABELS: Record<Consultation['mode'], string> = {
  KNOWLEDGE_BASED: 'Keyword rules',
  AI_ASSISTED: 'AI understood your question',
}

/** Labels of the answer card; Vietnamese when the Member asked in Vietnamese. */
const ANSWER_TEXT = {
  en: {
    answered: 'Grounded center answer', unavailable: 'Answer unavailable', found: 'How this answer was found',
    noAnswer: 'This assistant does not answer personal, medical or private questions. Ask your human coach or reception.',
    noAnswerSteps: 'Open Contact Reception, choose a topic, enter the question and submit; replies appear in My support requests.',
    contactReceptionist: 'Contact Receptionist',
    sourceType: 'Source type', open: 'Open source', contact: 'Contact reception about this answer',
    modes: MODE_LABELS, kinds: SOURCE_KIND_LABELS, otherKind: 'Center information',
  },
  vi: {
    answered: 'Câu trả lời từ dữ liệu trung tâm', unavailable: 'Chưa có câu trả lời', found: 'Cách tìm ra câu trả lời',
    noAnswer: 'Trợ lý không trả lời câu hỏi cá nhân, y tế hay riêng tư. Hãy hỏi Coach hoặc lễ tân.',
    noAnswerSteps: 'Mở mục Liên hệ Lễ tân, chọn chủ đề, nhập câu hỏi và gửi; phản hồi sẽ hiển thị trong Yêu cầu hỗ trợ của tôi.',
    contactReceptionist: 'Liên hệ Lễ tân',
    sourceType: 'Loại nguồn', open: 'Mở nguồn', contact: 'Liên hệ lễ tân về câu trả lời này',
    modes: { KNOWLEDGE_BASED: 'Quy tắc từ khoá', AI_ASSISTED: 'AI đã hiểu câu hỏi của bạn' } as Record<Consultation['mode'], string>,
    kinds: {
      CLASS_SESSION: 'Lịch học của trung tâm', SPORT_PACKAGE: 'Danh mục gói tập', PRODUCT_GUIDANCE: 'Hướng dẫn đặt chỗ',
      CURATED_FAQ: 'FAQ cố định của trung tâm', HUMAN_COACH: 'Coach (người thật)', MY_PACKAGE: 'Gói tập của bạn',
      MY_CARD: 'Thẻ thành viên của bạn', MY_BOOKING: 'Buổi bạn đã đặt', COACH_FEEDBACK: 'Nhận xét của Coach (nguyên văn)',
      HOMEWORK: 'Bài tập về nhà của Coach (nguyên văn)', SUPPORT: 'Hỗ trợ của lễ tân',
    } as Record<string, string>,
    otherKind: 'Thông tin trung tâm',
  },
}

/** The question and answer a Member may choose to share with reception. */
function assistantExcerpt(question: string, result: Consultation): string {
  const sources = result.sources.map(source => `- ${source.title}: ${source.detail}`).join('\n')
  return `Question: ${question}\nAssistant answer: ${result.answer}${sources ? `\n${sources}` : ''}`.slice(0, 1000)
}

export function MemberAssistantPage() {
  const auth = useContext(AuthContext)
  return <CenterConsultationWorkspace key={auth?.user?.id ?? 'anonymous'} />
}

function CenterConsultationWorkspace() {
  const [params] = useSearchParams()
  const location = useLocation()
  const returnTo = safeMemberReturnTo(params.get('returnTo'))
  const [result, setResult] = useState<Consultation | null>(null)
  const [question, setQuestion] = useState(() => {
    const routeQuestion = (location.state as { assistantQuestion?: unknown } | null)?.assistantQuestion
    return typeof routeQuestion === 'string' ? routeQuestion.slice(0, 500) : params.get('question')?.slice(0, 500) ?? ''
  })
  const [asked, setAsked] = useState('')
  const action = useTrainingAction()
  const resultRef = useRef<HTMLDivElement>(null)
  const enabled = import.meta.env.VITE_TRAINING_CONSULTATION_ENABLED !== 'false'
  // Bring a new answer into view on short desktop screens such as 1280x720.
  useEffect(() => { if (result) resultRef.current?.scrollIntoView?.({ block: 'start' }) }, [result])
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!enabled) return
    const text = question.trim()
    void action.run(async () => { setResult(await trainingGuidanceApi.consult(text)); setAsked(text) }, 'Center information loaded.')
  }
  return (
    <div className="training-page assistant-workspace">
      <Link className="assistant-workspace__back" to={returnTo}>Back to previous page</Link>
      <div className="assistant-workspace__heading">
        <span className="assistant-workspace__mark"><SporiRobotMark /></span>
        <PageHeader eyebrow={<span className="assistant-workspace__desktop-eyebrow">Spori AI / Sportify training guide</span>} title="Center assistant" description="Ask about classes, prices, or your own records in English or Vietnamese." />
      </div>
      <div className="assistant-workspace__privacy"><p aria-label="Privacy note">Answers come from Sportify data only. Personal details are never sent to AI.</p></div>
      {!enabled ? <EmptyState title="Automated consultation is disabled" /> : <Card title="Ask about the center">
        <div role="group" aria-label="Example questions">
          <div className="training-actions">{ASSISTANT_EXAMPLES.map(example => <Button key={example} type="button" variant="secondary" disabled={action.pending} onClick={() => setQuestion(example)}>{example}</Button>)}</div>
        </div>
        <form className="training-form" onSubmit={submit}><Input label="Your question" name="question" value={question} onChange={event => setQuestion(event.target.value)} maxLength={500} required disabled={action.pending} />
          <Button type="submit" loading={action.pending}>Ask</Button></form><ActionMessage {...action} />
      </Card>}
      {result && (() => {
        const text = ANSWER_TEXT[result.language === 'vi' ? 'vi' : 'en']
        return <div ref={resultRef}><Card title={result.status === 'ANSWERED' ? text.answered : text.unavailable}>
          <div role="status" className="assistant-workspace__answer">
            <p>{result.mode} - {result.answer}</p>
            {result.status === 'NO_ANSWER' && (
              <div className="assistant-workspace__no-answer">
                <p>{text.noAnswer}</p>
                <p className="assistant-workspace__steps">{text.noAnswerSteps}</p>
                <div className="training-actions" style={{ marginTop: '12px', marginBottom: '12px' }}>
                  <Link
                    to="/member/support"
                    state={{ question: asked, excerpt: assistantExcerpt(asked, result) }}
                    className="btn btn--secondary"
                  >
                    {text.contactReceptionist}
                  </Link>
                </div>
              </div>
            )}
          </div>
          <p>{text.found}: {text.modes[result.mode] ?? result.mode}.</p>
          {result.sources.length > 0 && <ul className="training-list">{result.sources.map(source => <li key={`${source.kind}-${source.id}`}><h2>{source.title}</h2><p>{text.sourceType}: {text.kinds[source.kind] ?? text.otherKind}</p><p>{source.detail}</p><Link to={source.path}>{text.open}: {source.title}</Link></li>)}</ul>}
          {result.status !== 'NO_ANSWER' && <Link to="/member/support" state={{ excerpt: assistantExcerpt(asked, result) }}>{text.contact}</Link>}
        </Card></div>
      })()}
      <div role="group" aria-label="Need help from a person?" className="training-actions">
        <Link to="/member/training/questions">Ask your human coach</Link>
        <Link to="/member/support">Contact reception</Link>
        <Link to="/member/support#my-requests">My support requests</Link>
      </div>
    </div>
  )
}
