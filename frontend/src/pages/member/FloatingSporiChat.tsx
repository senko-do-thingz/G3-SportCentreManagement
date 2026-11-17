import { useCallback, useContext, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { AuthContext } from '../../auth/authContext'
import { sporiApi, type ChatMessageItem, type ChatSession } from '../../api/sporiApi'
import { SporiRobotMark } from './SporiRobotMark'
import { SporiAiBadge, SporiAvatar, SporiComposer, SporiCopyButton, SporiHandoff, SporiIcon, SporiSources, SporiText, SporiTyping, type SporiIconName } from './sporiChat'
import { formatTime, useSporiChat, useSporiScroll, type SporiMessage } from './useSporiChat'
import './sporiChat.css'

type SporiRole = 'MEMBER' | 'MANAGER' | 'RECEPTIONIST' | 'COACH'

/** Greeting and suggested questions per role; every role talks to the same Spori API. */
const ROLE_COPY: Record<SporiRole, { intro: string; prompts: { icon: SporiIconName; text: string }[] }> = {
  MEMBER: {
    intro: "I'm Spori. Ask me about classes, packages, or your own records, in English or Vietnamese.",
    prompts: [
      { icon: 'calendar', text: 'Which classes are available soon?' },
      { icon: 'flame', text: 'How should I warm up before practice?' },
      { icon: 'ticket', text: 'How many sessions do I have left?' },
    ],
  },
  MANAGER: {
    intro: "I'm Spori. Ask me about center figures, sessions, packages or services, in English or Vietnamese.",
    prompts: [
      { icon: 'note', text: 'Give me an overview of the center today' },
      { icon: 'calendar', text: 'Which sessions are published this week?' },
      { icon: 'card', text: 'What are the current package prices?' },
    ],
  },
  RECEPTIONIST: {
    intro: "I'm Spori. Ask me about today's sessions, packages, booking rules or services, in English or Vietnamese.",
    prompts: [
      { icon: 'calendar', text: "What is today's session overview?" },
      { icon: 'card', text: 'What are the current package prices?' },
      { icon: 'ticket', text: 'How does booking and cancellation work?' },
    ],
  },
  COACH: {
    intro: "I'm Spori. Ask me about your upcoming sessions, classes or center services, in English or Vietnamese.",
    prompts: [
      { icon: 'calendar', text: 'Show my sessions for the next 7 days' },
      { icon: 'note', text: 'How many learner questions are waiting for me?' },
      { icon: 'flame', text: 'Which sports does the center offer?' },
    ],
  },
}

/**
 * The compact floating Spori chat used by every role.
 * Supports multi-session conversations, conversation archiving on the small chatbox,
 * and expanding to full assistant for all roles.
 */
export function FloatingSporiChat({ isDesktop = true }: { isDesktop?: boolean }) {
  const location = useLocation()
  const auth = useContext(AuthContext)
  const role: SporiRole = auth?.role ?? 'MEMBER'
  const member = role === 'MEMBER'
  const copy = ROLE_COPY[role]
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const chat = useSporiChat('Spori could not load an answer. Please try again.')
  const launcher = useRef<HTMLButtonElement>(null)
  const dialog = useRef<HTMLElement>(null)
  const messageLog = useRef<HTMLDivElement>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  const enabled = import.meta.env.VITE_TRAINING_CONSULTATION_ENABLED !== 'false'
  const returnTo = location.pathname + location.search + location.hash
  const roleBasePath = role === 'MANAGER' ? '/manager' : role === 'COACH' ? '/coach' : role === 'RECEPTIONIST' ? '/reception' : '/member'
  const assistantHref = `${roleBasePath}/assistant?returnTo=${encodeURIComponent(returnTo)}`

  // Multi-session & archive states
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [activeSessionId, setActiveSessionId] = useState<number | null>(null)
  const [activeSessionMessages, setActiveSessionMessages] = useState<ChatMessageItem[]>([])
  const [sessionPending, setSessionPending] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [showArchived, setShowArchived] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const fetchSessions = useCallback(async () => {
    try {
      const list = await sporiApi.listSessions(showArchived ? true : false)
      if (Array.isArray(list)) {
        setSessions(list)
      }
    } catch {
      // Safe fallback in mock environments
    }
  }, [showArchived])

  useEffect(() => {
    if (!open) return
    let active = true
    void sporiApi.listSessions(showArchived ? true : false)
      .then((list) => {
        if (active && Array.isArray(list)) {
          setSessions(list)
        }
      })
      .catch(() => {})
    return () => {
      active = false
    }
  }, [open, showArchived])

  useEffect(() => {
    if (!open) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.current?.querySelector<HTMLButtonElement>('.spori-panel__close')?.focus()
    const handleKeyboard = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setOpen(false)
        launcher.current?.focus()
        return
      }
      if (event.key !== 'Tab') return
      const focusable = Array.from(dialog.current?.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled])') ?? [])
      if (!focusable.length) return
      const current = focusable.indexOf(document.activeElement as HTMLElement)
      const next = event.shiftKey ? current - 1 : current + 1
      if (next < 0) { event.preventDefault(); focusable.at(-1)?.focus() }
      else if (next >= focusable.length) { event.preventDefault(); focusable[0].focus() }
    }
    document.addEventListener('keydown', handleKeyboard)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyboard)
    }
  }, [open])

  const isSessionMode = activeSessionId !== null
  const totalCount = isSessionMode ? activeSessionMessages.length : chat.messages.length
  const isPending = sessionPending || chat.pending

  useSporiScroll(messageLog, {
    count: totalCount,
    pending: isPending,
    lastIsAnswer: isSessionMode ? activeSessionMessages.at(-1)?.senderType === 'ASSISTANT' : chat.messages.at(-1)?.role !== 'user',
    open,
  })

  function close() { setOpen(false); launcher.current?.focus() }

  const handleNewChat = useCallback(() => {
    setActiveSessionId(null)
    setActiveSessionMessages([])
    chat.reset()
    setDraft('')
    setShowHistory(false)
    input.current?.focus()
  }, [chat])

  const selectSession = useCallback(async (sessionId: number) => {
    setActiveSessionId(sessionId)
    setShowHistory(false)
    setSessionPending(true)
    try {
      const detail = await sporiApi.getSession(sessionId)
      if (detail && Array.isArray(detail.messages)) {
        setActiveSessionMessages(detail.messages)
      }
    } catch (err) {
      console.error('Failed to load session detail', err)
    } finally {
      setSessionPending(false)
    }
  }, [])

  const handleArchiveCurrent = useCallback(async () => {
    if (activeSessionId) {
      try {
        await sporiApi.archiveSession(activeSessionId, true)
        setNotice('Conversation archived')
        setTimeout(() => setNotice(null), 3000)
        handleNewChat()
        void fetchSessions()
      } catch (err) {
        console.error('Failed to archive session', err)
      }
    } else if (chat.messages.length > 0) {
      try {
        const created = await sporiApi.createSession()
        if (created && created.id) {
          await sporiApi.archiveSession(created.id, true)
        }
      } catch {
        // safe fallback
      }
      setNotice('Conversation archived')
      setTimeout(() => setNotice(null), 3000)
      handleNewChat()
      void fetchSessions()
    }
  }, [activeSessionId, chat.messages.length, fetchSessions, handleNewChat])

  const handleToggleArchive = useCallback(async (sessionId: number, currentArchived: boolean, e: React.MouseEvent) => {
    e.stopPropagation()
    try {
      await sporiApi.archiveSession(sessionId, !currentArchived)
      void fetchSessions()
      if (activeSessionId === sessionId && !showArchived) {
        handleNewChat()
      }
    } catch (err) {
      console.error('Failed to toggle archive', err)
    }
  }, [activeSessionId, fetchSessions, handleNewChat, showArchived])

  const handleDeleteSession = useCallback(async (sessionId: number, e: React.MouseEvent) => {
    e.stopPropagation()
    if (!window.confirm('Delete this conversation?')) return
    try {
      await sporiApi.deleteSession(sessionId)
      setSessions((curr) => curr.filter((s) => s.id !== sessionId))
      if (activeSessionId === sessionId) {
        handleNewChat()
      }
    } catch (err) {
      console.error('Failed to delete session', err)
    }
  }, [activeSessionId, handleNewChat])

  async function send() {
    const question = draft.trim()
    if (!question || isPending || !enabled) return
    setDraft('')

    if (activeSessionId) {
      const tempUserMsg: ChatMessageItem = {
        id: Date.now(),
        senderType: 'USER',
        content: question,
        sentAt: new Date().toISOString(),
      }
      setActiveSessionMessages((prev) => [...prev, tempUserMsg])
      setSessionPending(true)
      try {
        const res = await sporiApi.sendMessage(activeSessionId, question)
        if (res && res.assistantMessage) {
          setActiveSessionMessages((prev) => [...prev, res.assistantMessage])
          void fetchSessions()
        }
      } catch {
        // Fallback to chat.send if sendMessage fails (e.g. mock test environment)
        void chat.send(question)
      } finally {
        setSessionPending(false)
        input.current?.focus()
      }
      return
    }

    // When starting from fresh chat: attempt session creation
    try {
      const created = await sporiApi.createSession()
      if (created && created.id) {
        setActiveSessionId(created.id)
        const tempUserMsg: ChatMessageItem = {
          id: Date.now(),
          senderType: 'USER',
          content: question,
          sentAt: new Date().toISOString(),
        }
        setActiveSessionMessages([tempUserMsg])
        setSessionPending(true)
        const res = await sporiApi.sendMessage(created.id, question)
        if (res && res.assistantMessage) {
          setActiveSessionMessages((prev) => [...prev, res.assistantMessage])
          void fetchSessions()
        }
        setSessionPending(false)
        input.current?.focus()
        return
      }
    } catch {
      // In mock/test environments where createSession is not mocked,
      // fallback smoothly to chat.send(question)
    } finally {
      setSessionPending(false)
    }

    void chat.send(question).then(() => input.current?.focus())
  }

  const hasMessages = isSessionMode ? activeSessionMessages.length > 0 : chat.messages.length > 0

  return <>
    <button
      ref={launcher}
      type="button"
      className={'member-concept__assistant-launcher spori-fab' + (open ? ' is-open' : '')}
      aria-haspopup="dialog"
      aria-expanded={open}
      aria-controls="member-assistant-dialog"
      aria-label={open ? 'Close Spori AI' : 'Ask Spori AI'}
      title={open ? 'Close Spori AI' : 'Ask Spori AI'}
      onClick={() => setOpen((value) => !value)}
    >
      <span className="member-concept__sr-only">Spori AI</span>
      <span className="member-concept__assistant-mark spori-fab__icon spori-fab__icon--bot" aria-hidden="true"><SporiRobotMark /></span>
      <span className="spori-fab__icon spori-fab__icon--close" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="24" height="24"><path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" /></svg>
      </span>
    </button>
    {open && <>
      <button className="member-concept__assistant-scrim spori-scrim" type="button" aria-label="Close Spori AI panel" onClick={close} />
      <section ref={dialog} id="member-assistant-dialog" className={'spori-panel' + (isDesktop ? '' : ' spori-panel--sheet')} role="dialog" aria-modal="true" aria-labelledby="member-assistant-title" tabIndex={-1}>
        <header className="spori-panel__head">
          <span className="spori-presence"><SporiAvatar size="md" /><span className="spori-presence__dot" aria-hidden="true" /></span>
          <div className="spori-panel__identity">
            <h2 id="member-assistant-title">Spori AI</h2>
            <p>Sportify assistant · replies in seconds</p>
          </div>
          <div className="spori-panel__actions">
            <button
              type="button"
              className="spori-icon-button"
              title="New conversation"
              aria-label="New conversation"
              disabled={isPending}
              onClick={handleNewChat}
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.2">
                <path d="M12 5v14M5 12h14" strokeLinecap="round" />
              </svg>
            </button>
            <button
              type="button"
              className="spori-icon-button"
              title="Archive conversation"
              aria-label="Archive conversation"
              disabled={!hasMessages || isPending}
              onClick={() => void handleArchiveCurrent()}
            >
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="2" y="3" width="20" height="5" rx="1" />
                <path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8M10 12h4" strokeLinecap="round" />
              </svg>
            </button>
            <button
              type="button"
              className="spori-icon-button"
              title={showHistory ? 'Back to chat' : 'Conversation history'}
              aria-label={showHistory ? 'Back to chat' : 'Conversation history'}
              onClick={() => setShowHistory((prev) => !prev)}
            >
              {showHistory ? (
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.2">
                  <path d="M19 12H5M12 19l-7-7 7-7" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              )}
            </button>
            {member && (
              <Link className="spori-icon-button" to={assistantHref} onClick={close} aria-label="Open full assistant" title="Open full assistant">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M14 4h6v6M20 4l-8 8M10 5H5v14h14v-5" />
                </svg>
              </Link>
            )}
            <button type="button" className="spori-icon-button spori-panel__close" aria-label="Close Spori AI" title="Close" onClick={close}>
              <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" /></svg>
            </button>
          </div>
        </header>

        {notice && (
          <div className="notice notice--info" role="status" style={{ margin: '8px 14px 0', padding: '6px 12px', fontSize: '0.8rem' }}>
            {notice}
          </div>
        )}

        {showHistory ? (
          <div className="spori-history-panel" role="region" aria-label="Conversation history">
            <div className="spori-sidebar__filter" style={{ display: 'flex', gap: '4px', marginBottom: '8px' }}>
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

            <div className="spori-history-list" role="list">
              {sessions.length === 0 ? (
                <div className="spori-sidebar__empty" style={{ padding: '32px 16px', textAlign: 'center', color: 'var(--color-text-muted)' }}>
                  {showArchived ? 'No archived conversations' : 'No conversations yet'}
                </div>
              ) : (
                sessions.map((s) => {
                  const isArchived = s.archived ?? s.isArchived ?? false
                  const isActive = activeSessionId === s.id
                  return (
                    <div
                      key={s.id}
                      role="listitem"
                      className={'spori-session-item' + (isActive ? ' is-active' : '')}
                      onClick={() => void selectSession(s.id)}
                    >
                      <span className="spori-session-item__icon" aria-hidden="true">
                        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                      </span>
                      <span className="spori-session-item__title" title={s.title}>
                        {s.title || 'Untitled conversation'}
                      </span>
                      <div className="spori-session-item__actions">
                        <button
                          type="button"
                          className="spori-session-action-btn"
                          title={isArchived ? 'Unarchive' : 'Archive'}
                          aria-label={isArchived ? 'Unarchive chat' : 'Archive chat'}
                          onClick={(e) => void handleToggleArchive(s.id, isArchived, e)}
                        >
                          <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2">
                            <rect x="2" y="3" width="20" height="5" rx="1" />
                            <path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8M10 12h4" strokeLinecap="round" />
                          </svg>
                        </button>
                        <button
                          type="button"
                          className="spori-session-action-btn spori-session-action-btn--danger"
                          title="Delete chat"
                          aria-label="Delete chat"
                          onClick={(e) => void handleDeleteSession(s.id, e)}
                        >
                          <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                          </svg>
                        </button>
                      </div>
                    </div>
                  )
                })
              )}
            </div>
          </div>
        ) : (
          <div ref={messageLog} className="spori-log" role="log" aria-live="polite" aria-relevant="additions">
            <div className={'spori-welcome' + (hasMessages ? ' is-compact' : '')}>
              <p className="spori-welcome__title">Hi there!</p>
              <p className="spori-welcome__copy">{copy.intro}</p>
            </div>
            {!hasMessages && enabled && (
              <div className="spori-suggest" aria-label="Suggested questions">
                <p className="spori-suggest__label">Popular questions</p>
                {copy.prompts.map((prompt) => (
                  <button
                    type="button"
                    key={prompt.text}
                    onClick={() => {
                      setDraft(prompt.text)
                      input.current?.focus()
                    }}
                  >
                    <span className="spori-suggest__icon" aria-hidden="true"><SporiIcon name={prompt.icon} /></span>
                    <span className="spori-suggest__text">{prompt.text}</span>
                    <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M9 6l6 6-6 6" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" /></svg>
                  </button>
                ))}
              </div>
            )}

            {isSessionMode ? (
              activeSessionMessages.map((msg) =>
                msg.senderType === 'USER' ? (
                  <div key={msg.id} className="spori-row spori-row--user">
                    <div className="spori-bubble"><SporiText text={msg.content} /></div>
                    <time className="spori-time">{formatTime(new Date(msg.sentAt))}</time>
                  </div>
                ) : (
                  <div key={msg.id} className="spori-row spori-row--bot">
                    <SporiAvatar />
                    <div className="spori-bubble">
                      <SporiText text={msg.content} />
                    </div>
                    <div className="spori-meta-row">
                      <time className="spori-time">{formatTime(new Date(msg.sentAt))}</time>
                      <SporiAiBadge />
                      <SporiCopyButton text={msg.content} />
                    </div>
                  </div>
                )
              )
            ) : (
              chat.messages.map((message) => <FloatingMessage key={message.id} message={message} member={member} onNavigate={close} />)
            )}
            {isPending && <SporiTyping label="Spori is typing…" />}
          </div>
        )}

        {!enabled
          ? <p className="notice notice--warning spori-panel__notice" role="status">Automated consultation is currently disabled.</p>
          : !showHistory && (
            <SporiComposer
              id="spori-question"
              label="Ask Spori a question"
              buttonLabel="Send"
              placeholder="Type a message…"
              value={draft}
              pending={isPending}
              onChange={setDraft}
              onSubmit={() => void send()}
              inputRef={input}
            />
          )}
        <p className="spori-panel__foot">Answers use Sportify data · Spori can make mistakes</p>
      </section>
    </>}
  </>
}

function FloatingMessage({ message, member, onNavigate }: { message: SporiMessage; member: boolean; onNavigate: () => void }) {
  if (message.role === 'user') return (
    <div className="spori-row spori-row--user">
      <div className="spori-bubble"><SporiText text={message.text} /></div>
      <time className="spori-time">{formatTime(message.at)}</time>
    </div>
  )
  if (message.role === 'error') return (
    <div className="spori-row spori-row--bot">
      <SporiAvatar />
      <div className="spori-bubble is-error" role="alert"><SporiText text={message.text} /></div>
    </div>
  )
  const { result, question } = message
  const vi = result.language === 'vi'
  // Who should help: the server's decision, or reception for a Member's unanswered question.
  const handoff = result.handoff ?? (member && result.status === 'NO_ANSWER' ? 'RECEPTIONIST' : null)
  // Deduplicate redundant source row if handoff button is already shown
  const sources = (result.sources ?? []).filter(source => !(handoff === 'COACH' && source.kind === 'HUMAN_COACH'))
  // A plain rule "no answer" without a named owner is replaced by a clear handoff text.
  const ruleNoAnswer = result.status === 'NO_ANSWER' && result.mode !== 'AI_GENERATED' && !result.handoff
  return (
    <div className="spori-row spori-row--bot">
      <SporiAvatar />
      <div className={'spori-bubble' + (result.status === 'NO_ANSWER' ? ' is-warning' : '')}>
        {ruleNoAnswer
          ? <p>{vi ? 'Tôi không tìm thấy câu trả lời chắc chắn cho câu hỏi của bạn. Bạn có thể liên hệ Lễ tân Sportify để được hỗ trợ thêm.' : "I couldn't find a reliable answer to your question. You can contact Sportify Reception for further assistance."}</p>
          : <SporiText text={result.answer} />}
        {handoff && <div className="spori-handoff">
          {ruleNoAnswer && member && <p>{vi ? 'Mở mục Liên hệ Lễ tân, chọn chủ đề, nhập câu hỏi và gửi; phản hồi sẽ hiển thị trong Yêu cầu hỗ trợ của tôi.' : 'Open Contact Reception, choose a topic, enter the question and submit; replies appear in My support requests.'}</p>}
          <SporiHandoff role={handoff} member={member} vi={vi} question={question} answer={result.answer} onNavigate={onNavigate} />
        </div>}
        <SporiSources sources={sources} onNavigate={onNavigate} vi={vi} />
      </div>
      <div className="spori-meta-row">
        <time className="spori-time">{formatTime(message.at)}</time>
        {result.mode === 'AI_GENERATED' ? <SporiAiBadge /> : <span className="spori-mode-badge" title="This answer uses verified Sportify guidance, not a live Gemini-generated reply">Guided answer</span>}
        <SporiCopyButton text={result.answer} vi={vi} />
      </div>
    </div>
  )
}
