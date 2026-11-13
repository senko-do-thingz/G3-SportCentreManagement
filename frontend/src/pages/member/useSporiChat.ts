import { useEffect, useRef, useState, type RefObject } from 'react'
import { trainingGuidanceApi, type ChatTurn, type Consultation } from '../../api/trainingGuidanceApi'

/** Shared Spori conversation state: messages, the pending flag, and the history sent back with each question. */

export type SporiMessage =
  | { id: number; role: 'user'; text: string; at: Date }
  | { id: number; role: 'assistant'; question: string; result: Consultation; at: Date }
  | { id: number; role: 'error'; text: string; at: Date }

/** Earlier turns the server may use to understand follow-ups; the server trims and filters them again. */
const HISTORY_TURNS = 8

export function useSporiChat(fallbackError: string) {
  const [messages, setMessages] = useState<SporiMessage[]>([])
  const [pending, setPending] = useState(false)
  const nextId = useRef(1)

  async function send(question: string) {
    const text = question.trim()
    if (!text) return
    const history: ChatTurn[] = messages.flatMap((message): ChatTurn[] => message.role === 'user'
      ? [{ role: 'user', text: message.text }]
      : message.role === 'assistant' ? [{ role: 'assistant', text: message.result.answer }] : []).slice(-HISTORY_TURNS)
    setMessages(current => [...current, { id: nextId.current++, role: 'user', text, at: new Date() }])
    setPending(true)
    try {
      const result = await trainingGuidanceApi.consult(text, history)
      setMessages(current => [...current, { id: nextId.current++, role: 'assistant', question: text, result, at: new Date() }])
    } catch (error) {
      const message = error instanceof Error && error.message ? error.message : fallbackError
      setMessages(current => [...current, { id: nextId.current++, role: 'error', text: message, at: new Date() }])
    } finally {
      setPending(false)
    }
  }

  return { messages, pending, send, reset: () => setMessages([]) }
}

/**
 * Chat scrolling as in most messengers: the Member's own message and the typing dots scroll to the bottom, while a new
 * answer scrolls to its first line so a long reply is read from the top.
 */
export function useSporiScroll(log: RefObject<HTMLDivElement | null>, deps: { count: number; pending: boolean; lastIsAnswer: boolean; open?: boolean }) {
  const { count, pending, lastIsAnswer, open = true } = deps
  useEffect(() => {
    const element = log.current
    if (!open || !element || typeof element.scrollTo !== 'function') return
    const rows = element.querySelectorAll<HTMLElement>('.spori-row')
    const last = rows[rows.length - 1]
    const top = !pending && lastIsAnswer && last ? Math.max(0, last.offsetTop - 12) : element.scrollHeight
    element.scrollTo({ top, behavior: 'smooth' })
  }, [log, count, pending, lastIsAnswer, open])
}

export function formatTime(at: Date) {
  return at.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}
