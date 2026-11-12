import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { trainingGuidanceApi, type Consultation } from '../../api/trainingGuidanceApi'
import { FloatingSporiChat } from './FloatingSporiChat'

vi.mock('../../api/trainingGuidanceApi', () => ({
  trainingGuidanceApi: {
    consult: vi.fn(),
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
    vi.mocked(trainingGuidanceApi.consult).mockResolvedValueOnce(noAnswerResult)

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
})
