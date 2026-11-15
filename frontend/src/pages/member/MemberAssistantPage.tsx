import { useCallback, useContext, useEffect, useRef, useState } from 'react'
import { AuthContext } from '../../auth/authContext'
import { type Consultation } from '../../api/trainingGuidanceApi'
import { sporiApi, type ChatMessageItem, type ChatSession } from '../../api/sporiApi'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../components/ui/EmptyState'
import { SporiAiBadge, SporiAvatar, SporiComposer, SporiCopyButton, SporiHandoff, SporiIcon, SporiSources, SporiText, SporiTyping } from './sporiChat'
import { formatTime, useSporiChat, useSporiScroll } from './useSporiChat'
import './sporiChat.css'

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
  { icon: 'calendar', text: 'Which classes are available in the next two weeks?' },
  { icon: 'card', text: 'What are the package prices?' },
  { icon: 'flame', text: 'How should I warm up before practice?' },
  { icon: 'ticket', text: 'How many sessions do I have left?' },
  { icon: 'note', text: 'What is my homework?' },
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
  const chat = useSporiChat('Spori could not load an answer. Please try again.')
  const [question, setQuestion] = useState(() => {
    const routeQuestion = (location.state as { assistantQuestion?: unknown } | null)?.assistantQuestion
    return typeof routeQuestion === 'string' ? routeQuestion.slice(0, 500) : params.get('question')?.slice(0, 500) ?? ''
  })
  const logRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const enabled = import.meta.env.VITE_TRAINING_CONSULTATION_ENABLED !== 'false'

  // Multi-session chat states
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [activeSessionId, setActiveSessionId] = useState<number | null>(null)
  const [activeSessionMessages, setActiveSessionMessages] = useState<ChatMessageItem[]>([])
  const [loadingSessions, setLoadingSessions] = useState(false)
  const [sessionPending, setSessionPending] = useState(false)
  const [showArchived, setShowArchived] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [editingSessionId, setEditingSessionId] = useState<number | null>(null)
  const [editTitle, setEditTitle] = useState('')

  const fetchSessions = useCallback(async () => {
    try {
      setLoadingSessions(true)
      const list = await sporiApi.listSessions(showArchived ? true : false)
      if (Array.isArray(list)) {
        setSessions(list)
      }
    } catch {
      // Safe fallback in mock environments
    } finally {
      setLoadingSessions(false)
    }
  }, [showArchived])

  useEffect(() => {
    void fetchSessions()
  }, [fetchSessions])

  const selectSession = useCallback(async (sessionId: number) => {
    if (chat.pending || sessionPending) return
    setActiveSessionId(sessionId)
    try {
      const detail = await sporiApi.getSession(sessionId)
      if (detail && Array.isArray(detail.messages)) {
        setActiveSessionMessages(detail.messages)
      }
    } catch (err) {
      console.error('Failed to load session detail', err)
    }
  }, [chat.pending, sessionPending])

  const startNewChat = useCallback(() => {
    setActiveSessionId(null)
    setActiveSessionMessages([])
    chat.reset()
    setQuestion('')
    inputRef.current?.focus()
  }, [chat])

  const isSessionMode = activeSessionId !== null
  const isCurrentChatEmpty = isSessionMode
    ? activeSessionMessages.length === 0
    : chat.messages.length === 0

  const handleNewChat = useCallback(async () => {
    // Prevent spamming new chat sessions:
    // If the active conversation is empty, treat the current session as the new one
    // without sending unnecessary requests to create redundant sessions.
    if (isCurrentChatEmpty) {
      setQuestion('')
      inputRef.current?.focus()
      return
    }

    try {
      const created = await sporiApi.createSession()
      if (created && created.id) {
        setSessions(curr => [created, ...curr])
        setActiveSessionId(created.id)
        setActiveSessionMessages([])
        chat.reset()
        setQuestion('')
        inputRef.current?.focus()
        return
      }
    } catch (err) {
      console.error('Failed to create new session, falling back to local chat reset', err)
    }

    startNewChat()
  }, [isCurrentChatEmpty, startNewChat])

  const handleRename = useCallback(async (sessionId: number, newTitle: string) => {
    const trimmed = newTitle.trim()
    if (!trimmed) {
      setEditingSessionId(null)
      return
    }
    try {
      await sporiApi.renameSession(sessionId, trimmed)
      setSessions(curr => curr.map(s => s.id === sessionId ? { ...s, title: trimmed } : s))
      setEditingSessionId(null)
    } catch (err) {
      console.error('Failed to rename session', err)
    }
  }, [])

  const handleDelete = useCallback(async (sessionId: number, e: React.MouseEvent) => {
    e.stopPropagation()
    if (!window.confirm('Are you sure you want to delete this conversation?')) return
    try {
      await sporiApi.deleteSession(sessionId)
      setSessions(curr => curr.filter(s => s.id !== sessionId))
      if (activeSessionId === sessionId) {
        startNewChat()
      }
    } catch (err) {
      console.error('Failed to delete session', err)
    }
  }, [activeSessionId, startNewChat])

  const handleArchive = useCallback(async (sessionId: number, currentArchived: boolean, e: React.MouseEvent) => {
    e.stopPropagation()
    try {
      await sporiApi.archiveSession(sessionId, !currentArchived)
      await fetchSessions()
      if (activeSessionId === sessionId && !showArchived) {
        startNewChat()
      }
    } catch (err) {
      console.error('Failed to archive session', err)
    }
  }, [activeSessionId, fetchSessions, showArchived, startNewChat])

  const sessionStarted = isSessionMode
    ? activeSessionMessages.length > 0 || sessionPending
    : chat.messages.length > 0 || chat.pending
  const pending = chat.pending || sessionPending

  useSporiScroll(logRef, {
    count: isSessionMode ? activeSessionMessages.length : chat.messages.length,
    pending,
    lastIsAnswer: isSessionMode
      ? activeSessionMessages.at(-1)?.senderType === 'ASSISTANT'
      : chat.messages.at(-1)?.role !== 'user'
  })

  async function submit() {
    const text = question.trim()
    if (!enabled || !text || pending) return
    setQuestion('')

    if (activeSessionId) {
      const tempUserMsg: ChatMessageItem = {
        id: Date.now(),
        senderType: 'USER',
        content: text,
        sentAt: new Date().toISOString()
      }
      setActiveSessionMessages(prev => [...prev, tempUserMsg])
      setSessionPending(true)
      try {
        const res = await sporiApi.sendMessage(activeSessionId, text)
        if (res && res.assistantMessage) {
          setActiveSessionMessages(prev => [...prev, res.assistantMessage])
          void fetchSessions()
        }
      } catch (err) {
        console.error('Failed to send message in session', err)
        setActiveSessionMessages(prev => [
          ...prev,
          {
            id: Date.now() + 1,
            senderType: 'ASSISTANT',
            content: 'Could not connect to Spori AI. Please try again.',
            sentAt: new Date().toISOString()
          }
        ])
      } finally {
        setSessionPending(false)
        inputRef.current?.focus()
      }
      return
    }

    void chat.send(text).then(() => inputRef.current?.focus())
  }

  function pickExample(example: string) {
    setQuestion(example)
    inputRef.current?.focus()
  }

  const activeSession = sessions.find(s => s.id === activeSessionId)

  return (
    <div className="training-page spori-page">
      <div className="spori-page__top">
        <div className="spori-page__top-left">
          <button
            type="button"
            className="spori-page__toggle-sidebar"
            onClick={() => setSidebarOpen(prev => !prev)}
            title={sidebarOpen ? 'Hide chat history' : 'Show chat history'}
            aria-label={sidebarOpen ? 'Hide chat history' : 'Show chat history'}
          >
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <path d="M9 3v18" />
            </svg>
            <span>{sidebarOpen ? 'Hide history' : 'Chat history'}</span>
          </button>
        </div>
        <button
          type="button"
          className="spori-page__new"
          onClick={handleNewChat}
          disabled={pending}
        >
          <svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" /></svg>
          New chat
        </button>
      </div>

      <div className={'spori-page__layout' + (!sidebarOpen ? ' is-sidebar-collapsed' : '')}>
        <aside className={'spori-sidebar' + (!sidebarOpen ? ' is-collapsed' : '')} aria-label="Chat history">
          <div className="spori-sidebar__head">
            <button
              type="button"
              className="spori-sidebar__new-btn"
              onClick={handleNewChat}
              disabled={pending}
              aria-label="New conversation"
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.2">
                <path d="M12 5v14M5 12h14" strokeLinecap="round" />
              </svg>
              <span>New conversation</span>
            </button>
          </div>

          <div className="spori-sidebar__filter">
            <button
              type="button"
              className={'spori-sidebar__tab' + (!showArchived ? ' is-active' : '')}
              onClick={() => setShowArchived(false)}
            >
              Recent
            </button>
            <button
              type="button"
              className={'spori-sidebar__tab' + (showArchived ? ' is-active' : '')}
              onClick={() => setShowArchived(true)}
            >
              Archived
            </button>
          </div>

          <div className="spori-sidebar__list" role="list">
            {loadingSessions ? (
              <div className="spori-sidebar__loading">Loading history...</div>
            ) : sessions.length === 0 ? (
              <div className="spori-sidebar__empty">
                {showArchived ? 'No archived chats' : 'No conversations yet'}
              </div>
            ) : (
              sessions.map((s) => {
                const isActive = activeSessionId === s.id
                const isEditing = editingSessionId === s.id
                const isArchived = s.archived ?? s.isArchived ?? false

                return (
                  <div
                    key={s.id}
                    role="listitem"
                    className={'spori-session-item' + (isActive ? ' is-active' : '')}
                    onClick={() => { if (!isEditing) void selectSession(s.id) }}
                  >
                    <span className="spori-session-item__icon" aria-hidden="true">
                      <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    </span>

                    {isEditing ? (
                      <form
                        className="spori-session-item__edit-form"
                        onSubmit={(e) => { e.preventDefault(); void handleRename(s.id, editTitle) }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        <input
                          type="text"
                          className="spori-session-item__edit-input"
                          value={editTitle}
                          onChange={(e) => setEditTitle(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Escape') setEditingSessionId(null)
                          }}
                          autoFocus
                          maxLength={100}
                        />
                        <button type="submit" className="spori-session-item__edit-save" title="Save" aria-label="Save">
                          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.5">
                            <polyline points="20 6 9 17 4 12" />
                          </svg>
                        </button>
                        <button
                          type="button"
                          className="spori-session-item__edit-cancel"
                          title="Cancel"
                          aria-label="Cancel"
                          onClick={() => setEditingSessionId(null)}
                        >
                          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.5">
                            <line x1="18" y1="6" x2="6" y2="18" />
                            <line x1="6" y1="6" x2="18" y2="18" />
                          </svg>
                        </button>
                      </form>
                    ) : (
                      <>
                        <span className="spori-session-item__title" title={s.title}>
                          {s.title || 'Untitled conversation'}
                        </span>

                        <div className="spori-session-item__actions">
                          <button
                            type="button"
                            className="spori-session-action-btn"
                            title="Rename"
                            aria-label="Rename chat"
                            onClick={(e) => {
                              e.stopPropagation()
                              setEditingSessionId(s.id)
                              setEditTitle(s.title || '')
                            }}
                          >
                            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                            </svg>
                          </button>
                          <button
                            type="button"
                            className="spori-session-action-btn"
                            title={isArchived ? 'Unarchive' : 'Archive'}
                            aria-label={isArchived ? 'Unarchive chat' : 'Archive chat'}
                            onClick={(e) => void handleArchive(s.id, isArchived, e)}
                          >
                            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2">
                              <rect x="2" y="3" width="20" height="5" rx="1" />
                              <path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8M10 12h4" />
                            </svg>
                          </button>
                          <button
                            type="button"
                            className="spori-session-action-btn spori-session-action-btn--danger"
                            title="Delete chat"
                            aria-label="Delete chat"
                            onClick={(e) => void handleDelete(s.id, e)}
                          >
                            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                            </svg>
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                )
              })
            )}
          </div>
        </aside>

        <section className="spori-page__window" aria-labelledby="assistant-chat-title">
          <header className="spori-page__head">
            <SporiAvatar size="md" />
            <div className="spori-page__identity">
              <h1 id="assistant-chat-title">
                {activeSession ? activeSession.title : 'Center assistant'}
              </h1>
              <p><span className="spori-online" aria-hidden="true" />Spori AI · Online</p>
            </div>
          </header>

          {!enabled ? <div className="spori-page__disabled"><EmptyState title="Automated consultation is disabled" /></div> : <>
            <div ref={logRef} className={'spori-log spori-page__log' + (sessionStarted ? '' : ' is-empty')} role="log" aria-live="polite" aria-relevant="additions" aria-label="Conversation">
              {!sessionStarted && <div className="spori-hero">
                <SporiAvatar size="lg" />
                <h2>Hi, I'm Spori!</h2>
                <p>Ask about classes, prices, or your own packages, bookings and homework, in English or Vietnamese.</p>
                <div className="spori-hero__grid" role="group" aria-label="Example questions">
                  {ASSISTANT_EXAMPLES.map(example => <button key={example.text} type="button" onClick={() => pickExample(example.text)}>
                    <span aria-hidden="true"><SporiIcon name={example.icon} /></span>{example.text}
                  </button>)}
                </div>
              </div>}

              {isSessionMode ? (
                activeSessionMessages.map((msg) => msg.senderType === 'USER' ? (
                  <div key={msg.id} className="spori-row spori-row--user">
                    <div className="spori-bubble"><SporiText text={msg.content} /></div>
                    <time className="spori-time">{formatTime(new Date(msg.sentAt))}</time>
                  </div>
                ) : (
                  <div key={msg.id} className="spori-row spori-row--bot">
                    <SporiAvatar />
                    <div className="spori-bubble">
                      <span className="spori-tag">Spori AI</span>
                      <SporiText text={msg.content} />
                    </div>
                    <div className="spori-meta-row">
                      <time className="spori-time">{formatTime(new Date(msg.sentAt))}</time>
                      <SporiAiBadge />
                      <SporiCopyButton text={msg.content} />
                    </div>
                  </div>
                ))
              ) : (
                chat.messages.map(message => message.role === 'user'
                  ? <div key={message.id} className="spori-row spori-row--user"><div className="spori-bubble"><SporiText text={message.text} /></div><time className="spori-time">{formatTime(message.at)}</time></div>
                  : message.role === 'error'
                    ? <div key={message.id} className="spori-row spori-row--bot"><SporiAvatar /><div className="spori-bubble is-error" role="alert"><SporiText text={message.text} /></div></div>
                    : <AnswerBubble key={message.id} question={message.question} result={message.result} at={message.at} />)
              )}

              {pending && <SporiTyping label="Spori is typing…" />}
            </div>

            <div className="spori-page__composer">
              <SporiComposer id="assistant-question" label="Your question" buttonLabel="Ask" placeholder="Message Spori…" value={question}
                pending={pending} onChange={setQuestion} onSubmit={submit} inputRef={inputRef} />
              <p className="spori-page__hint" aria-label="Privacy note">Answers come from Sportify data only. Personal details are never sent to AI.</p>
            </div>
          </>}
        </section>
      </div>

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
  const vi = result.language === 'vi'
  const text = ANSWER_TEXT[vi ? 'vi' : 'en']
  const noAnswer = result.status === 'NO_ANSWER'
  const showCoachHandoff = result.handoff === 'COACH'
  const showReceptionHandoff = !noAnswer && result.handoff === 'RECEPTIONIST'

  // Filter out redundant source items if handoff already provides the actionable button for that same destination
  const displayedSources = (result.sources || []).filter(source => {
    if (showCoachHandoff && source.kind === 'HUMAN_COACH') return false
    if (showReceptionHandoff && source.kind === 'SUPPORT') return false
    return true
  })

  return (
    <div className="spori-row spori-row--bot">
      <SporiAvatar />
      <div className={'spori-bubble' + (noAnswer ? ' is-warning' : '')}>
        <span className={'spori-tag' + (noAnswer ? ' spori-tag--warning' : '')}>{noAnswer ? text.unavailable : text.answered}</span>
        <SporiText text={result.answer} />
        {noAnswer && result.handoff !== 'COACH' && <div className="spori-handoff">
          {result.mode !== 'AI_GENERATED' && !result.handoff && <><p>{text.noAnswer}</p><p>{text.noAnswerSteps}</p></>}
          <Link className="spori-handoff__button" to="/member/support" state={{ question, excerpt: assistantExcerpt(question, result) }}>{text.contactReceptionist}</Link>
        </div>}
        {(showCoachHandoff || showReceptionHandoff) && <div className="spori-handoff">
          <SporiHandoff role={result.handoff!} member vi={vi} question={question} answer={result.answer} />
        </div>}
        <SporiSources sources={displayedSources} kindLabel={kind => text.kinds[kind] ?? text.otherKind} vi={vi} />
        <p className="spori-meta">{text.found}: {text.modes[result.mode] ?? result.mode}.</p>
        {!noAnswer && <Link className="spori-meta-link" to="/member/support" state={{ excerpt: assistantExcerpt(question, result) }}>{text.contact}</Link>}
      </div>
      <div className="spori-meta-row">
        <time className="spori-time">{formatTime(at)}</time>
        {result.mode === 'AI_GENERATED' ? <SporiAiBadge /> : <span className="spori-mode-badge" title="This answer uses verified Sportify guidance, not a live Gemini-generated reply">Guided answer</span>}
        <SporiCopyButton text={result.answer} vi={vi} />
      </div>
    </div>
  )
}
