import { api } from './client'
import type { ChatTurn, Consultation } from './trainingGuidanceApi'

export interface ChatSession {
  id: number
  title: string
  archived: boolean
  isArchived?: boolean
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface ChatMessageItem {
  id: number
  senderType: 'USER' | 'ASSISTANT'
  content: string
  sentAt: string
}

export interface ChatSessionDetail {
  id: number
  title: string
  archived: boolean
  isArchived?: boolean
  createdAt: string
  updatedAt: string
  messages: ChatMessageItem[]
}

export interface ChatSessionMessageResponse {
  sessionId: number
  userMessage: ChatMessageItem
  assistantMessage: ChatMessageItem
}

/** Spori AI assistant API: both backward-compatible consultation and modern session management */
export const sporiApi = {
  // Legacy single-turn consultation
  chat: (question: string, history: ChatTurn[] = []) =>
    api.post<Consultation>('/assistant/chat', history.length ? { question, history } : { question }),

  // Multi-session management
  listSessions: (archived?: boolean) => {
    const query = archived !== undefined ? { archived } : undefined
    return api.get<ChatSession[]>('/spori-ai/sessions', { query })
  },
  createSession: (title?: string) =>
    api.post<ChatSession>('/spori-ai/sessions', title ? { title } : {}),
  getSession: (id: number) =>
    api.get<ChatSessionDetail>(`/spori-ai/sessions/${id}`),
  renameSession: (id: number, title: string) =>
    api.put<ChatSession>(`/spori-ai/sessions/${id}/title`, { title }),
  archiveSession: (id: number, archived = true) =>
    api.put<ChatSession>(`/spori-ai/sessions/${id}/archive`, { archived }),
  deleteSession: (id: number) =>
    api.delete<void>(`/spori-ai/sessions/${id}`),
  sendMessage: (sessionId: number, message: string) =>
    api.post<ChatSessionMessageResponse>(`/spori-ai/sessions/${sessionId}/messages`, { message }),
}

