import { useContext, useEffect, useRef, useState } from 'react'
import { AuthContext } from '../../auth/authContext'
import { type Consultation } from '../../api/trainingGuidanceApi'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../components/ui/EmptyState'
import { safeMemberReturnTo } from './assistantNavigation'
import { SporiAvatar, SporiComposer, SporiSources, SporiText, SporiTyping } from './sporiChat'
import { formatTime, useSporiChat } from './useSporiChat'

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
  { icon: '📅', text: 'Which classes are available in the next two weeks?' },
  { icon: '💳', text: 'What are the package prices?' },
  { icon: '🔥', text: 'How should I warm up before practice?' },
  { icon: '🎟️', text: 'How many sessions do I have left?' },
  { icon: '📝', text: 'What is my homework?' },
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
  AI_GENERATED: 'Written by Gemini AI from Sportify data',
}

/** Labels inside an answer bubble; Vietnamese when the Member asked in Vietnamese. */
const ANSWER_TEXT = {
  en: {
    answered: 'Grounded center answer', unavailable: 'Answer unavailable', found: 'How this answer was found',
    noAnswer: 'This assistant does not answer personal, medical or private questions. Ask your human coach or reception.',
    noAnswerSteps: 'Open Contact Reception, choose a topic, enter the question and submit; replies appear in My support requests.',
    contactReceptionist: 'Contact Receptionist',
    contact: 'Contact reception about this answer',
    modes: MODE_LABELS, kinds: SOURCE_KIND_LABELS, otherKind: 'Center information',
  },
  vi: {
    answered: 'Câu trả lời từ dữ liệu trung tâm', unavailable: 'Chưa có câu trả lời', found: 'Cách tìm ra câu trả lời',
    noAnswer: 'Trợ lý không trả lời câu hỏi cá nhân, y tế hay riêng tư. Hãy hỏi Coach hoặc lễ tân.',
    noAnswerSteps: 'Mở mục Liên hệ Lễ tân, chọn chủ đề, nhập câu hỏi và gửi; phản hồi sẽ hiển thị trong Yêu cầu hỗ trợ của tôi.',
    contactReceptionist: 'Liên hệ Lễ tân',
    contact: 'Liên hệ lễ tân về câu trả lời này',
    modes: { KNOWLEDGE_BASED: 'Quy tắc từ khoá', AI_ASSISTED: 'AI đã hiểu câu hỏi của bạn', AI_GENERATED: 'Gemini AI viết từ dữ liệu Sportify' } as Record<Consultation['mode'], string>,
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
  const chat = useSporiChat('Spori could not load an answer. Please try again.')
  const [question, setQuestion] = useState(() => {
    const routeQuestion = (location.state as { assistantQuestion?: unknown } | null)?.assistantQuestion
    return typeof routeQuestion === 'string' ? routeQuestion.slice(0, 500) : params.get('question')?.slice(0, 500) ?? ''
  })
  const logRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const enabled = import.meta.env.VITE_TRAINING_CONSULTATION_ENABLED !== 'false'
  const started = chat.messages.length > 0 || chat.pending

  // Keep the newest message in view, like any chat.
  useEffect(() => {
    const log = logRef.current
    if (!log) return
    if (typeof log.scrollTo === 'function') log.scrollTo({ top: log.scrollHeight, behavior: 'smooth' })
    else log.scrollTop = log.scrollHeight
  }, [chat.messages, chat.pending])

  function submit() {
    const text = question.trim()
    if (!enabled || !text || chat.pending) return
    setQuestion('')
    void chat.send(text).then(() => inputRef.current?.focus())
  }

  function pickExample(example: string) {
    setQuestion(example)
    inputRef.current?.focus()
  }

  return (
    <div className="training-page spori-page">
      <div className="spori-page__top">
        <Link className="spori-page__back" to={returnTo}>
          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" /></svg>
          Back to previous page
        </Link>
        {chat.messages.length > 0 && <button type="button" className="spori-page__new" onClick={() => { chat.reset(); inputRef.current?.focus() }} disabled={chat.pending}>
          <svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" /></svg>
          New chat
        </button>}
      </div>

      <section className="spori-page__window" aria-labelledby="assistant-chat-title">
        <header className="spori-page__head">
          <SporiAvatar size="md" />
          <div className="spori-page__identity">
            <h1 id="assistant-chat-title">Center assistant</h1>
            <p><span className="spori-online" aria-hidden="true" />Spori AI · Online</p>
          </div>
        </header>

        {!enabled ? <div className="spori-page__disabled"><EmptyState title="Automated consultation is disabled" /></div> : <>
          <div ref={logRef} className={'spori-log spori-page__log' + (started ? '' : ' is-empty')} role="log" aria-live="polite" aria-relevant="additions" aria-label="Conversation">
            {!started && <div className="spori-hero">
              <SporiAvatar size="lg" />
              <h2>Hi, I'm Spori 👋</h2>
              <p>Ask about classes, prices, or your own packages, bookings and homework — in English or Vietnamese.</p>
              <div className="spori-hero__grid" role="group" aria-label="Example questions">
                {ASSISTANT_EXAMPLES.map(example => <button key={example.text} type="button" onClick={() => pickExample(example.text)}>
                  <span aria-hidden="true">{example.icon}</span>{example.text}
                </button>)}
              </div>
            </div>}
            {chat.messages.map(message => message.role === 'user'
              ? <div key={message.id} className="spori-row spori-row--user"><div className="spori-bubble"><SporiText text={message.text} /></div><time className="spori-time">{formatTime(message.at)}</time></div>
              : message.role === 'error'
                ? <div key={message.id} className="spori-row spori-row--bot"><SporiAvatar /><div className="spori-bubble is-error" role="alert"><SporiText text={message.text} /></div></div>
                : <AnswerBubble key={message.id} question={message.question} result={message.result} at={message.at} />)}
            {chat.pending && <SporiTyping label="Spori is typing…" />}
          </div>

          <div className="spori-page__composer">
            <SporiComposer id="assistant-question" label="Your question" buttonLabel="Ask" placeholder="Message Spori…" value={question}
              pending={chat.pending} onChange={setQuestion} onSubmit={submit} inputRef={inputRef} />
            <p className="spori-page__hint" aria-label="Privacy note">Answers come from Sportify data only. Personal details are never sent to AI.</p>
          </div>
        </>}
      </section>

      <nav aria-label="Need help from a person?" className="spori-page__handoff">
        <span>Need a person?</span>
        <Link to="/member/training/questions">Ask your human coach</Link>
        <Link to="/member/support">Contact reception</Link>
        <Link to="/member/support#my-requests">My support requests</Link>
      </nav>
    </div>
  )
}

function AnswerBubble({ question, result, at }: { question: string; result: Consultation; at: Date }) {
  const text = ANSWER_TEXT[result.language === 'vi' ? 'vi' : 'en']
  const noAnswer = result.status === 'NO_ANSWER'
  return (
    <div className="spori-row spori-row--bot">
      <SporiAvatar />
      <div className={'spori-bubble' + (noAnswer ? ' is-warning' : '')}>
        <span className={'spori-tag' + (noAnswer ? ' spori-tag--warning' : '')}>{noAnswer ? text.unavailable : text.answered}</span>
        <SporiText text={result.answer} />
        {noAnswer && <div className="spori-handoff">
          {result.mode !== 'AI_GENERATED' && <><p>{text.noAnswer}</p><p>{text.noAnswerSteps}</p></>}
          <Link className="spori-handoff__button" to="/member/support" state={{ question, excerpt: assistantExcerpt(question, result) }}>{text.contactReceptionist}</Link>
        </div>}
        <SporiSources sources={result.sources} kindLabel={kind => text.kinds[kind] ?? text.otherKind} />
        <p className="spori-meta">{text.found}: {text.modes[result.mode] ?? result.mode}.</p>
        {!noAnswer && <Link className="spori-meta-link" to="/member/support" state={{ excerpt: assistantExcerpt(question, result) }}>{text.contact}</Link>}
      </div>
      <time className="spori-time">{formatTime(at)}</time>
    </div>
  )
}
