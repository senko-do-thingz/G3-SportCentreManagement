import { api } from './client'
import type { ChatTurn, Consultation } from './trainingGuidanceApi'

/** Spori, the shared read-only assistant for every signed-in role. The server takes the role from the account. */
export const sporiApi = {
  chat: (question: string, history: ChatTurn[] = []) =>
    api.post<Consultation>('/assistant/chat', history.length ? { question, history } : { question }),
}
