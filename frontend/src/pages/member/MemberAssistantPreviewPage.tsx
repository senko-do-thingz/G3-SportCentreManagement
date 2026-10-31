import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { PageHeader } from '../../components/ui/PageHeader'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { Notice } from '../../components/ui/Notice'
import { TextArea } from '../../components/ui/TextArea'
import '../../styles/memberAssistant.css'

export interface AssistantReply {
  text: string
  /** Rendered only when a future approved service supplies verifiable references. */
  sources?: string[]
}

export type AssistantTransport = (question: string, signal: AbortSignal) => Promise<AssistantReply>

interface AssistantMessage {
  id: number
  role: 'member' | 'assistant'
  text: string
  sources?: string[]
}

interface MemberAssistantPreviewPageProps {
  /** Injection point for a future approved API or isolated UI preview. */
  transport?: AssistantTransport
}

const QUICK_QUESTIONS = [
  { title: 'Schedules', prompt: 'Where can I find the class schedule?' },
  { title: 'Exercises', prompt: 'What should I ask a coach before trying a new exercise?' },
  { title: 'Center services', prompt: 'What sports and services does the center offer?' },
]

const NOT_CONNECTED = 'The assistant service is not connected. Your question was not sent.'

const unavailableTransport: AssistantTransport = async () => {
  throw new Error(NOT_CONNECTED)
}

/** NX-30 visual preview. It intentionally does not invent an API or assistant answer. */
export function MemberAssistantPreviewPage({ transport = unavailableTransport }: MemberAssistantPreviewPageProps) {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<AssistantMessage[]>([])
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const nextMessageId = useRef(0)
  const request = useRef<AbortController | null>(null)
  const questionId = useId()

  useEffect(() => () => request.current?.abort(), [])

  async function submitQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const question = draft.trim()
    if (!question || sending) return

    const controller = new AbortController()
    request.current?.abort()
    request.current = controller
    setError(null)
    setSending(true)

    try {
      const reply = await transport(question, controller.signal)
      if (controller.signal.aborted) return
      const firstId = nextMessageId.current + 1
      nextMessageId.current += 2
      setMessages((current) => [
        ...current,
        { id: firstId, role: 'member', text: question },
        { id: firstId + 1, role: 'assistant', text: reply.text, sources: reply.sources },
      ])
      setDraft('')
    } catch (cause) {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : NOT_CONNECTED)
      }
    } finally {
      if (!controller.signal.aborted) setSending(false)
    }
  }

  function startNewConversation() {
    request.current?.abort()
    request.current = null
    setMessages([])
    setDraft('')
    setError(null)
    setSending(false)
    nextMessageId.current = 0
  }

  return (
    <div className="page member-assistant">
      <PageHeader
        eyebrow="Member / Assistant"
        title="AI Assistant"
        description="Explore help for schedules, exercises and Sportify Center services."
        actions={<Button variant="secondary" onClick={startNewConversation} disabled={!draft && !messages.length && !error && !sending}>New conversation</Button>}
      />

      <Card className="member-assistant__panel" aria-label="Assistant preview">
        <div className="member-assistant__topline">
          <div>
            <p className="member-assistant__eyebrow">SPORTIFY MEMBER HELP</p>
            <h2>How can we help?</h2>
          </div>
          <span className="member-assistant__status"><span aria-hidden="true" /> Preview only</span>
        </div>

        <Notice tone="warning" live="polite" className="member-assistant__notice">
          The assistant is not connected yet. Questions you send here will not leave this preview.
        </Notice>

        {messages.length === 0 ? (
          <section className="member-assistant__welcome" aria-labelledby="assistant-welcome-title">
            <div className="member-assistant__avatar" aria-hidden="true">S</div>
            <div>
              <h3 id="assistant-welcome-title">Start with a topic</h3>
              <p>Choose a prompt or write a question. This preview will not generate an answer.</p>
            </div>
          </section>
        ) : (
          <ol className="member-assistant__messages" aria-label="Current conversation" aria-live="polite">
            {messages.map((message) => (
              <li key={message.id} className={`member-assistant__message member-assistant__message--${message.role}`}>
                <span className="member-assistant__message-author">{message.role === 'member' ? 'You' : 'Sportify Assistant'}</span>
                <p>{message.text}</p>
                {message.sources?.length ? (
                  <ul className="member-assistant__sources" aria-label="Answer sources">
                    {message.sources.map((source) => <li key={source}>{source}</li>)}
                  </ul>
                ) : null}
              </li>
            ))}
          </ol>
        )}

        {messages.length === 0 && (
          <div className="member-assistant__prompts" aria-label="Suggested questions">
            {QUICK_QUESTIONS.map(({ title, prompt }) => (
              <button
                className="member-assistant__prompt"
                key={title}
                type="button"
                onClick={() => {
                  setDraft(prompt)
                  setError(null)
                  document.getElementById(questionId)?.focus()
                }}
              >
                <span>{title}</span>
                <span className="member-assistant__prompt-text">{prompt}</span>
                <span className="member-assistant__prompt-action">Use this question <span aria-hidden="true">→</span></span>
              </button>
            ))}
          </div>
        )}

        <form className="member-assistant__composer" onSubmit={submitQuestion}>
          <TextArea
            id={questionId}
            label="Your question"
            hint="Questions are limited to schedules, exercises and center services in the proposed scope."
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value)
              if (error) setError(null)
            }}
            placeholder="Ask about a schedule, exercise or center service…"
            rows={3}
            disabled={sending}
          />
          <Button type="submit" loading={sending} disabled={!draft.trim() || sending}>
            Send question
          </Button>
        </form>

        {error && (
          <Notice className="member-assistant__error" tone="danger" title="No answer was sent" live="assertive">
            {error}
          </Notice>
        )}
        <p className="member-assistant__privacy">No conversation is saved by this development preview.</p>
      </Card>
    </div>
  )
}
