import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../auth/authContext'
import { type Consultation } from '../../api/trainingGuidanceApi'
import { sporiApi } from '../../api/sporiApi'
import { FloatingSporiChat } from './FloatingSporiChat'

vi.mock('../../api/sporiApi', () => ({
  sporiApi: {
    chat: vi.fn(),
    listSessions: vi.fn().mockResolvedValue([]),
    createSession: vi.fn().mockResolvedValue({ id: 99, title: 'New conversation', archived: false }),
    getSession: vi.fn().mockResolvedValue({ id: 99, title: 'New conversation', archived: false, messages: [] }),
    archiveSession: vi.fn().mockResolvedValue({ id: 99, title: 'New conversation', archived: true }),
    deleteSession: vi.fn().mockResolvedValue(undefined),
    sendMessage: vi.fn().mockResolvedValue({
      sessionId: 99,
      userMessage: { id: 1, senderType: 'USER', content: 'Hello', sentAt: '2026-11-17T10:00:00Z' },
      assistantMessage: { id: 2, senderType: 'ASSISTANT', content: 'Hi there!', sentAt: '2026-11-17T10:00:01Z' },
    }),
  },
}))

describe('FloatingSporiChat', () => {
  it('displays receptionist handoff on NO_ANSWER', async () => {
    const noAnswerResult: Consultation = {
      mode: 'KNOWLEDGE_BASED',
      status: 'NO_ANSWER',
      answer: 'I do not have a grounded answer to that question.',
      historyStored: false,
      sources: [],
      language: 'en',
    }
    vi.mocked(sporiApi.createSession).mockRejectedValueOnce(new Error('Session not available'))
    vi.mocked(sporiApi.chat).mockResolvedValueOnce(noAnswerResult)

    render(
      <MemoryRouter>
        <FloatingSporiChat isDesktop={true} />
      </MemoryRouter>
    )

    // Open chat
    fireEvent.click(screen.getByRole('button', { name: /Ask Spori AI/i }))

    // Type question and send
    const input = screen.getByRole('textbox', { name: 'Ask Spori a question' })
    fireEvent.change(input, { target: { value: 'Can I bring my pet kangaroo to swimming?' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    await waitFor(() => {
      expect(screen.getByText(/I couldn't find a reliable answer to your question/i)).toBeInTheDocument()
    })

    expect(screen.getByText(/Open Contact Reception, choose a topic, enter the question and submit/i)).toBeInTheDocument()
    const handoffLink = screen.getByRole('link', { name: /Contact Receptionist/i })
    expect(handoffLink).toHaveAttribute('href', '/member/support')
  })

  it('archives conversation directly on small chatbox', async () => {
    render(
      <MemoryRouter>
        <FloatingSporiChat isDesktop={true} />
      </MemoryRouter>
    )

    fireEvent.click(screen.getByRole('button', { name: /Ask Spori AI/i }))

    // Type and send a message using session mode
    const input = screen.getByRole('textbox', { name: 'Ask Spori a question' })
    fireEvent.change(input, { target: { value: 'What are the current package prices?' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    await waitFor(() => {
      expect(screen.getByText('Hi there!')).toBeInTheDocument()
    })

    // Click archive button
    const archiveBtn = screen.getByRole('button', { name: 'Archive conversation' })
    expect(archiveBtn).toBeEnabled()
    fireEvent.click(archiveBtn)

    await waitFor(() => {
      expect(sporiApi.archiveSession).toHaveBeenCalledWith(99, true)
      expect(screen.getByText('Conversation archived')).toBeInTheDocument()
    })
  })

  it('toggles conversation history with Recent and Archived tabs', async () => {
    const mockSessions = [
      { id: 10, title: 'Previous conversation', archived: false, messageCount: 2, createdAt: '2026-11-17T09:00:00Z', updatedAt: '2026-11-17T09:05:00Z' },
    ]
    vi.mocked(sporiApi.listSessions).mockResolvedValueOnce(mockSessions)

    render(
      <MemoryRouter>
        <FloatingSporiChat isDesktop={true} />
      </MemoryRouter>
    )

    fireEvent.click(screen.getByRole('button', { name: /Ask Spori AI/i }))

    // Click history button
    const historyBtn = screen.getByRole('button', { name: 'Conversation history' })
    fireEvent.click(historyBtn)

    expect(screen.getByRole('button', { name: 'Recent' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Archived' })).toBeInTheDocument()
    expect(await screen.findByText('Previous conversation')).toBeInTheDocument()
  })

  it('shows expand link for members but not for staff roles', () => {
    const fakeStaffAuth: AuthContextValue = {
      user: { id: 1, email: 'mgr@sportify.test', fullName: 'Manager User', phone: null, role: 'MANAGER', status: 'ACTIVE' },
      role: 'MANAGER',
      status: 'authenticated',
      sessionExpired: false,
      signedOutElsewhere: false,
      signOutNotice: null,
      accountSwitched: null,
      dismissAccountSwitch: vi.fn(),
      loadError: null,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      reloadUser: vi.fn(),
    }

    const { unmount } = render(
      <AuthContext.Provider value={fakeStaffAuth}>
        <MemoryRouter>
          <FloatingSporiChat isDesktop={true} />
        </MemoryRouter>
      </AuthContext.Provider>
    )

    fireEvent.click(screen.getByRole('button', { name: /Ask Spori AI/i }))
    const managerExpandLink = screen.getByRole('link', { name: 'Open full assistant' })
    expect(managerExpandLink).toBeInTheDocument()
    expect(managerExpandLink.getAttribute('href')).toContain('/manager/assistant')

    unmount()

    const fakeMemberAuth: AuthContextValue = {
      ...fakeStaffAuth,
      role: 'MEMBER',
      user: { id: 2, email: 'member@sportify.test', fullName: 'Member User', phone: null, role: 'MEMBER', status: 'ACTIVE' },
    }

    render(
      <AuthContext.Provider value={fakeMemberAuth}>
        <MemoryRouter>
          <FloatingSporiChat isDesktop={true} />
        </MemoryRouter>
      </AuthContext.Provider>
    )

    fireEvent.click(screen.getByRole('button', { name: /Ask Spori AI/i }))
    const expandLink = screen.getByRole('link', { name: 'Open full assistant' })
    expect(expandLink).toBeInTheDocument()
    expect(expandLink.getAttribute('href')).toContain('/member/assistant')
  })

  it('stops an in-flight reply when clicking Stop generating and shows the stopped notice', async () => {
    let aborted = false
    const createSessionSpy = vi.spyOn(sporiApi, 'createSession').mockRejectedValueOnce(new Error('No sessions'))
    const chatSpy = vi.spyOn(sporiApi, 'chat').mockImplementationOnce((_q, _h, signal) => new Promise((_resolve, reject) => {
      signal?.addEventListener('abort', () => {
        aborted = true
        reject(new DOMException('Aborted', 'AbortError'))
      })
    }))

    render(
      <MemoryRouter>
        <FloatingSporiChat isDesktop={true} />
      </MemoryRouter>
    )

    fireEvent.click(screen.getByRole('button', { name: /Ask Spori AI/i }))
    const textarea = screen.getByRole('textbox', { name: /Ask Spori a question/i })
    fireEvent.change(textarea, { target: { value: 'How can I create a new Coach Account?' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    const stopBtn = await screen.findByRole('button', { name: 'Stop generating' })
    fireEvent.click(stopBtn)

    expect(aborted).toBe(true)
    expect(await screen.findByText('You stopped this task.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Stop generating' })).not.toBeInTheDocument()

    createSessionSpy.mockRestore()
    chatSpy.mockRestore()
  })

  it('uses an in-app confirmation modal instead of window.confirm when deleting a conversation', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    const listSpy = vi.spyOn(sporiApi, 'listSessions').mockResolvedValue([
      { id: 55, title: 'Coach guide chat', archived: false, messageCount: 2, createdAt: '2026-11-19T10:00:00', updatedAt: '2026-11-19T10:05:00' },
    ])
    const deleteSpy = vi.spyOn(sporiApi, 'deleteSession').mockResolvedValue(undefined)

    render(
      <MemoryRouter>
        <FloatingSporiChat isDesktop={true} />
      </MemoryRouter>
    )

    fireEvent.click(screen.getByRole('button', { name: /Ask Spori AI/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Conversation history' }))
    expect(await screen.findByText('Coach guide chat')).toBeInTheDocument()

    // Bam xoa mo Modal thay vi window.confirm; bam Cancel giu nguyen hoi thoai
    fireEvent.click(screen.getByRole('button', { name: 'Delete chat' }))
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(await screen.findByRole('heading', { name: 'Delete conversation' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(deleteSpy).not.toHaveBeenCalled()
    expect(screen.getByText('Coach guide chat')).toBeInTheDocument()

    // Mo lai va bam Delete de xoa
    fireEvent.click(screen.getByRole('button', { name: 'Delete chat' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    await waitFor(() => {
      expect(deleteSpy).toHaveBeenCalledWith(55)
    })

    confirmSpy.mockRestore()
    listSpy.mockRestore()
    deleteSpy.mockRestore()
  })
})
