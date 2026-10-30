import { useCallback, useState } from 'react'

/**
 * Opt-in choice for the member assistant (NX-31). It is kept per account in this browser, so a
 * second member who signs in on the same computer never inherits the first member's choice, and
 * nothing is sent to the server. Storage can be blocked (private window), so every access is
 * guarded and the screens still work with an in-memory choice.
 */
export type AssistantChoice = 'on' | 'off' | null

const KEY_PREFIX = 'sportify.assistant.optIn.'

function storage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function readAssistantChoice(userId: number): AssistantChoice {
  try {
    const value = storage()?.getItem(KEY_PREFIX + userId)
    return value === 'on' || value === 'off' ? value : null
  } catch {
    return null
  }
}

function writeAssistantChoice(userId: number, choice: 'on' | 'off'): void {
  try {
    storage()?.setItem(KEY_PREFIX + userId, choice)
  } catch {
    /* Keep the in-memory choice when storage is blocked. */
  }
}

export function useAssistantChoice(userId: number | null | undefined): [AssistantChoice, (choice: 'on' | 'off') => void] {
  const [choice, setChoice] = useState<AssistantChoice>(() => (userId ? readAssistantChoice(userId) : null))
  const update = useCallback(
    (next: 'on' | 'off') => {
      if (userId) writeAssistantChoice(userId, next)
      setChoice(next)
    },
    [userId],
  )
  return [choice, update]
}

export const ASSISTANT_PATH = '/member/assistant'

/**
 * The page the member came from, passed in the router state. Only a path inside the member area is
 * accepted, so a crafted state can never send the member to another site or to a protocol URL.
 */
export function safeReturnPath(value: unknown): string | null {
  if (typeof value !== 'string' || value.length === 0 || value.length > 300) return null
  if (!value.startsWith('/member') || value.startsWith('//') || value.includes('\\') || /^\/member[a-z]/i.test(value)) return null
  if (value === ASSISTANT_PATH || value.startsWith(ASSISTANT_PATH + '/') || value.startsWith(ASSISTANT_PATH + '?')) return null
  return value
}
