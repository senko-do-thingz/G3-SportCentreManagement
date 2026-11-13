import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { SporiRobotMark } from './SporiRobotMark'
import { SporiAvatar, SporiComposer, SporiSources, SporiText, SporiTyping } from './sporiChat'
import { formatTime, useSporiChat, type SporiMessage } from './useSporiChat'

const PROMPTS = [
  'Which classes are available soon?',
  'How should I warm up before practice?',
  'How many sessions do I have left?',
] as const

export function FloatingSporiChat({ isDesktop }: { isDesktop: boolean }) {
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const chat = useSporiChat('Spori could not load a grounded answer. Please try again or open the full assistant.')
  const launcher = useRef<HTMLButtonElement>(null)
  const dialog = useRef<HTMLElement>(null)
  const messageLog = useRef<HTMLDivElement>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  const enabled = import.meta.env.VITE_TRAINING_CONSULTATION_ENABLED !== 'false'
  const returnTo = location.pathname + location.search + location.hash
  const assistantHref = '/member/assistant?returnTo=' + encodeURIComponent(returnTo)

  useEffect(() => {
    if (!open) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.current?.querySelector<HTMLButtonElement>('button')?.focus()
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

  useEffect(() => {
    if (!open) return
    const log = messageLog.current
    if (!log) return
    if (typeof log.scrollTo === 'function') log.scrollTo({ top: log.scrollHeight, behavior: 'smooth' })
    else log.scrollTop = log.scrollHeight
  }, [chat.messages, open, chat.pending])

  function close() { setOpen(false); launcher.current?.focus() }

  function send() {
    const question = draft.trim()
    if (!question || chat.pending || !enabled) return
    setDraft('')
    void chat.send(question).then(() => input.current?.focus())
  }

  return <>
    <button
      ref={launcher}
      type="button"
      className="member-concept__assistant-launcher"
      aria-haspopup="dialog"
      aria-expanded={open}
      aria-controls="member-assistant-dialog"
      aria-label={open ? 'Close Spori AI' : 'Ask Spori AI'}
      title={open ? 'Close Spori AI' : 'Ask Spori AI'}
      onClick={() => setOpen((value) => !value)}
    >
      <span className="member-concept__assistant-mark" aria-hidden="true">{isDesktop ? <SporiRobotMark /> : 'S'}</span>
      <span className="member-concept__assistant-launcher-copy"><strong>Spori AI</strong><small>Ask about your training</small></span>
    </button>
    {open && <>
      <button className="member-concept__assistant-scrim spori-scrim" type="button" aria-label="Close Spori AI panel" onClick={close} />
      <section ref={dialog} id="member-assistant-dialog" className="spori-panel" role="dialog" aria-modal="true" aria-labelledby="member-assistant-title" tabIndex={-1}>
        <header className="spori-panel__head">
          <button type="button" className="spori-icon-button spori-panel__close" aria-label="Close Spori AI" title="Close" onClick={close}>
            <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" /></svg>
          </button>
          <SporiAvatar size="md" />
          <div className="spori-panel__identity">
            <h2 id="member-assistant-title">Spori AI</h2>
            <p><span className="spori-online" aria-hidden="true" />Online · Sportify assistant</p>
          </div>
          <Link className="spori-icon-button" to={assistantHref} onClick={close} aria-label="Open full assistant" title="Open full assistant">
            <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true"><path d="M14 4h6v6M20 4l-8 8M10 5H5v14h14v-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
          </Link>
        </header>

        <div ref={messageLog} className="spori-log" role="log" aria-live="polite" aria-relevant="additions">
          <div className="spori-welcome">
            <SporiAvatar size="lg" />
            <p className="spori-welcome__title">Hi, I'm Spori 👋</p>
            <p className="spori-welcome__copy">Ask me about classes, packages, or your records — in English or Vietnamese.</p>
          </div>
          {chat.messages.length === 0 && enabled && (
            <div className="spori-chips" aria-label="Suggested questions">
              {PROMPTS.map((question) => <button type="button" key={question} onClick={() => { setDraft(question); input.current?.focus() }}>{question}</button>)}
            </div>
          )}
          {chat.messages.map((message) => <FloatingMessage key={message.id} message={message} onNavigate={close} />)}
          {chat.pending && <SporiTyping label="Spori is typing…" />}
        </div>

        {!enabled
          ? <p className="notice notice--warning spori-panel__notice" role="status">Automated consultation is currently disabled. The full assistant explains other support options.</p>
          : <SporiComposer id="spori-question" label="Ask Spori a question" buttonLabel="Send" placeholder="Type a message…" value={draft}
              pending={chat.pending} onChange={setDraft} onSubmit={send} inputRef={input} />}
        <p className="spori-panel__foot">Answers use Sportify data · Spori can make mistakes</p>
      </section>
    </>}
  </>
}

function FloatingMessage({ message, onNavigate }: { message: SporiMessage; onNavigate: () => void }) {
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
  // A rule-based "no answer" is replaced by a clear handoff; an AI reply already explains itself.
  const ruleNoAnswer = result.status === 'NO_ANSWER' && result.mode !== 'AI_GENERATED'
  return (
    <div className="spori-row spori-row--bot">
      <SporiAvatar />
      <div className={'spori-bubble' + (result.status === 'NO_ANSWER' ? ' is-warning' : '')}>
        {ruleNoAnswer
          ? <p>{vi ? 'Tôi không tìm thấy câu trả lời chắc chắn cho câu hỏi của bạn. Bạn có thể liên hệ Lễ tân Sportify để được hỗ trợ thêm.' : "I couldn't find a reliable answer to your question. You can contact Sportify Reception for further assistance."}</p>
          : <SporiText text={result.answer} />}
        {result.status === 'NO_ANSWER' && <div className="spori-handoff">
          {/* An AI reply already explains the handoff in its own words; only the rule answer needs the steps. */}
          {ruleNoAnswer && <p>{vi ? 'Mở mục Liên hệ Lễ tân, chọn chủ đề, nhập câu hỏi và gửi; phản hồi sẽ hiển thị trong Yêu cầu hỗ trợ của tôi.' : 'Open Contact Reception, choose a topic, enter the question and submit; replies appear in My support requests.'}</p>}
          <Link to="/member/support" state={{ question, excerpt: `Question: ${question}\nSpori AI: ${result.answer}` }} onClick={onNavigate} className="spori-handoff__button">
            {vi ? 'Liên hệ Lễ tân' : 'Contact Receptionist'}
          </Link>
        </div>}
        <SporiSources sources={result.sources} onNavigate={onNavigate} />
      </div>
      <time className="spori-time">{formatTime(message.at)}{result.mode === 'AI_GENERATED' ? ' · Gemini' : ''}</time>
    </div>
  )
}
