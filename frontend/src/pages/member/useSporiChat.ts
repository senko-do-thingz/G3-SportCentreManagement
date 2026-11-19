import { useEffect, useRef, useState, type RefObject } from 'react'
import { type ChatTurn, type Consultation } from '../../api/trainingGuidanceApi'
import { sporiApi } from '../../api/sporiApi'

/** Shared Spori conversation state: messages, the pending flag, and the history sent back with each question. */

export type SporiMessage =
  | { id: number; role: 'user'; text: string; at: Date }
  | { id: number; role: 'assistant'; question: string; result: Consultation; at: Date }
  | { id: number; role: 'error'; text: string; at: Date }
  | { id: number; role: 'stopped'; text: string; at: Date }

/** Earlier turns the server may use to understand follow-ups; the server trims and filters them again. */
const HISTORY_TURNS = 8

// Kiem tra cau hoi co phai tieng Viet hay khong de chon thong bao dung tac vu phu hop
const VI_WORD_PATTERN = /\b(toi|cua|khong|ko|bao nhieu|the nao|minh|duoc|nhung|gi|nao|vay|nhe|xin|lam sao|o dau|khi nao|chao|cam on|tao|lop|buoi|lich|goi tap|hoi vien|le tan)\b/i

export function isVietnameseText(text: string): boolean {
  const normalized = text.normalize('NFC')
  const stripped = normalized.replace(/[\u0111\u0110]/g, 'd').normalize('NFD').replace(/\p{M}+/gu, '')
  return normalized.toLowerCase() !== stripped.toLowerCase() || VI_WORD_PATTERN.test(stripped)
}

export function stoppedTaskMessage(question: string): string {
  return isVietnameseText(question) ? 'B\u1EA1n \u0111\u00E3 d\u1EEBng t\u00E1c v\u1EE5 n\u00E0y.' : 'You stopped this task.'
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException
    ? error.name === 'AbortError'
    : error instanceof Error && error.name === 'AbortError'
}

export function useSporiChat(fallbackError: string) {
  const [messages, setMessages] = useState<SporiMessage[]>([])
  const [pending, setPending] = useState(false)
  const nextId = useRef(1)
  const abortRef = useRef<AbortController | null>(null)
  const activeQuestionRef = useRef('')

  async function send(question: string) {
    const text = question.trim()
    if (!text) return
    const history: ChatTurn[] = messages.flatMap((message): ChatTurn[] => message.role === 'user'
      ? [{ role: 'user', text: message.text }]
      : message.role === 'assistant' ? [{ role: 'assistant', text: message.result.answer }] : []).slice(-HISTORY_TURNS)
    setMessages(current => [...current, { id: nextId.current++, role: 'user', text, at: new Date() }])
    const controller = new AbortController()
    abortRef.current = controller
    activeQuestionRef.current = text
    setPending(true)
    try {
      const result = await sporiApi.chat(text, history, controller.signal)
      if (controller.signal.aborted || abortRef.current !== controller) return
      setMessages(current => [...current, { id: nextId.current++, role: 'assistant', question: text, result, at: new Date() }])
    } catch (error) {
      if (controller.signal.aborted || isAbortError(error) || abortRef.current !== controller) return
      const message = error instanceof Error && error.message ? error.message : fallbackError
      setMessages(current => [...current, { id: nextId.current++, role: 'error', text: message, at: new Date() }])
    } finally {
      if (abortRef.current === controller) {
        abortRef.current = null
        activeQuestionRef.current = ''
        setPending(false)
      }
    }
  }

  // Dung yeu cau dang cho va ghi nhan thong bao huy vao khung chat
  function stop() {
    const controller = abortRef.current
    if (!controller) return
    const cancelledQuestion = activeQuestionRef.current
    abortRef.current = null
    activeQuestionRef.current = ''
    controller.abort()
    setPending(false)
    setMessages(current => [...current, { id: nextId.current++, role: 'stopped', text: stoppedTaskMessage(cancelledQuestion), at: new Date() }])
  }

  function reset() {
    abortRef.current?.abort()
    abortRef.current = null
    activeQuestionRef.current = ''
    setPending(false)
    setMessages([])
  }

  return { messages, pending, send, stop, reset }
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
