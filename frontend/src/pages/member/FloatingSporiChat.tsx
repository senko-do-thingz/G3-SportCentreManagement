import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { trainingGuidanceApi, type Consultation } from '../../api/trainingGuidanceApi'
import { SporiRobotMark } from './SporiRobotMark'

const PROMPTS = [
  'Which classes are available soon?',
  'How should I warm up before practice?',
  'How many sessions do I have left?',
] as const

type ChatMessage = {
  id: number
  role: 'user' | 'assistant'
  text: string
  result?: Consultation
  error?: boolean
}

export function FloatingSporiChat({ isDesktop }: { isDesktop: boolean }) {
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [consented, setConsented] = useState(false)
  const [pending, setPending] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const nextMessageId = useRef(1)
  const launcher = useRef<HTMLButtonElement>(null)
  const dialog = useRef<HTMLElement>(null)
  const messageLog = useRef<HTMLDivElement>(null)
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
  }, [messages, open, pending])

  function close() { setOpen(false); launcher.current?.focus() }

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const question = draft.trim()
    if (!question || !consented || pending || !enabled) return
    const userMessage: ChatMessage = { id: nextMessageId.current++, role: 'user', text: question }
    setMessages((current) => [...current, userMessage])
    setDraft('')
    setPending(true)
    try {
      const result = await trainingGuidanceApi.consult(question)
      setMessages((current) => [...current, { id: nextMessageId.current++, role: 'assistant', text: result.answer, result }])
    } catch (error) {
      const text = error instanceof Error && error.message ? error.message : 'Spori could not load a grounded answer. Please try again or open the full assistant.'
      setMessages((current) => [...current, { id: nextMessageId.current++, role: 'assistant', text, error: true }])
    } finally {
      setPending(false)
    }
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
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
      <button className="member-concept__assistant-scrim" type="button" aria-label="Close Spori AI panel" onClick={close} />
      <section ref={dialog} id="member-assistant-dialog" className="member-concept__assistant-dialog" role="dialog" aria-modal="true" aria-labelledby="member-assistant-title" tabIndex={-1}>
        <div className="member-concept__assistant-dialog-head">
          <div className="member-concept__assistant-identity">
            {isDesktop && <span className="member-concept__assistant-dialog-mark"><SporiRobotMark /></span>}
            <span><p className="member-concept__assistant-kicker">Sportify training guide</p><h2 id="member-assistant-title">Spori AI</h2></span>
          </div>
          <button type="button" className="member-concept__assistant-close" aria-label="Close Spori AI" onClick={close}>Close</button>
        </div>

        <div ref={messageLog} className="member-concept__assistant-messages" role="log" aria-live="polite" aria-relevant="additions">
          {messages.length === 0 && <div className="member-concept__assistant-welcome">
            <p className="member-concept__assistant-copy">Hello! I can help with center sessions, packages, booking guidance, and your own Sportify records. I cannot change bookings or payments.</p>
            <div className="member-concept__assistant-prompts" aria-label="Suggested questions">{PROMPTS.map((question) => <button type="button" key={question} onClick={() => setDraft(question)}>{question}<span aria-hidden="true">&gt;</span></button>)}</div>
          </div>}
          {messages.map((message) => <article className={`member-concept__assistant-message member-concept__assistant-message--${message.role}${message.error ? ' is-error' : ''}`} key={message.id}>
            <strong>{message.role === 'user' ? 'You' : 'Spori'}</strong>
            <p>{message.text}</p>
            {message.result?.sources.length ? <ul>{message.result.sources.map((source) => <li key={`${message.id}-${source.kind}-${source.id}`}><Link to={source.path} onClick={close}>{source.title}</Link><span>{source.detail}</span></li>)}</ul> : null}
          </article>)}
          {pending && <div className="member-concept__assistant-typing" role="status"><span aria-hidden="true" />Spori is checking Sportify data...</div>}
        </div>

        {!enabled ? <p className="notice notice--warning" role="status">Automated consultation is currently disabled. The full assistant explains other support options.</p> : <form className="member-concept__assistant-composer" onSubmit={send}>
          <label className="member-concept__assistant-consent"><input type="checkbox" checked={consented} disabled={pending} onChange={(event) => setConsented(event.target.checked)} /> Enable automated center consultation</label>
          <label className="member-concept__sr-only" htmlFor="spori-question">Ask Spori a question</label>
          <div className="member-concept__assistant-compose-row">
            <textarea id="spori-question" value={draft} maxLength={500} rows={2} placeholder="Ask about training, classes, or your records" disabled={pending} onKeyDown={handleComposerKeyDown} onChange={(event) => setDraft(event.target.value)} />
            <button type="submit" disabled={!draft.trim() || !consented || pending}>Send</button>
          </div>
          <small>Enter sends; Shift+Enter adds a new line. No conversation history is stored.</small>
        </form>}
        <Link className="member-concept__assistant-full-link" to={assistantHref} onClick={close}>Expand to full assistant</Link>
      </section>
    </>}
  </>
}
