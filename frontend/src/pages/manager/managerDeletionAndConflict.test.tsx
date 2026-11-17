import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DeleteReasonModal } from './ManagerShared'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER } from '../../test/fakeBackend'

describe('DeleteReasonModal', () => {
  it('requires a non-blank reason and calls onConfirm with trimmed text', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined)
    const onClose = vi.fn()
    const onSuccess = vi.fn()
    const user = userEvent.setup()

    render(
      <DeleteReasonModal
        title="Delete Account"
        entityName="John Doe"
        onClose={onClose}
        onConfirm={onConfirm}
        onSuccess={onSuccess}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Delete Account' })).toBeInTheDocument()
    expect(screen.getByText(/0\/255/)).toBeInTheDocument()

    const submitBtn = screen.getByRole('button', { name: 'Delete permanently' })
    const textarea = screen.getByRole('textbox', { name: /Reason for deletion/i })

    // Blur/submit with whitespace
    await user.type(textarea, '   ')
    await user.tab()

    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.getByText(/Reason for deletion is required/i)).toBeInTheDocument()
    expect(submitBtn).toBeDisabled()

    // Type valid reason
    await user.clear(textarea)
    await user.type(textarea, 'Staff member resigned')
    expect(screen.getByText(/21\/255/)).toBeInTheDocument()

    await user.click(submitBtn)
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('Staff member resigned'))
    expect(onSuccess).toHaveBeenCalled()
  })
})

describe('Manager deletion workflows', () => {
  const sampleUser = {
    id: 99,
    fullName: 'Jane Staff',
    email: 'jane@sportify.test',
    phone: '0901234567',
    role: 'STAFF',
    status: 'ACTIVE',
    createdAt: '2026-01-01T08:00:00Z',
  }

  const sampleClass = {
    id: 12,
    code: 'CLS-012',
    name: 'Tennis Basics',
    sportId: 2,
    sportName: 'Tennis',
    ageGroupId: 1,
    ageGroupLabel: 'Adults',
    level: 'BEGINNER',
    coachId: 3,
    coachName: 'Coach Mike',
    maxMembers: 15,
    upcomingSessions: 0,
    active: true,
    createdAt: '2026-01-01T08:00:00Z',
    updatedAt: '2026-01-01T08:00:00Z',
  }

  beforeEach(() => {
    vi.stubEnv('VITE_MANAGER_PREVIEW', 'false')
    tokenStorage.startSession({ accessToken: 'manager-token', refreshToken: 'refresh-token' })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it('triggers delete user modal and sends deletion request with reason', async () => {
    const user = userEvent.setup()
    const api = installFakeFetch((call) => {
      const path = call.path.split('?')[0]
      if (path === '/users/me') return json(200, MANAGER)
      if (path === '/manager/users') {
        return json(200, { content: [sampleUser], totalElements: 1, totalPages: 1, number: 0, size: 20 })
      }
      if (path === '/manager/users/99/delete' && call.method === 'POST') {
        return json(204, null)
      }
      return backendError(404, 'Not found')
    })

    renderApp('/manager')
    await screen.findByText('Jane Staff')

    const deleteBtn = screen.getByRole('button', { name: 'Delete' })
    await user.click(deleteBtn)

    expect(screen.getByRole('heading', { name: 'Delete User Account' })).toBeInTheDocument()
    const textarea = screen.getByRole('textbox', { name: /Reason for deletion/i })
    await user.type(textarea, 'Resignation on 2026-11-15')

    await user.click(screen.getByRole('button', { name: 'Delete permanently' }))

    await waitFor(() => {
      const deleteCall = api.calls.find((c) => c.path === '/manager/users/99/delete')
      expect(deleteCall).toBeDefined()
      expect(deleteCall?.body).toEqual({ reason: 'Resignation on 2026-11-15' })
    })
  })

  it('triggers delete class modal and sends deletion request with reason', async () => {
    const user = userEvent.setup()
    const api = installFakeFetch((call) => {
      const path = call.path.split('?')[0]
      if (path === '/users/me') return json(200, MANAGER)
      if (path === '/sports') return json(200, [])
      if (path === '/coaches') return json(200, [])
      if (path === '/manager/classes') {
        return json(200, { content: [sampleClass], totalElements: 1, totalPages: 1, number: 0, size: 20 })
      }
      if (path === '/manager/classes/12/delete' && call.method === 'POST') {
        return json(204, null)
      }
      return backendError(404, 'Not found')
    })

    renderApp('/manager/classes')
    await screen.findByText('Tennis Basics')

    const deleteBtn = screen.getByRole('button', { name: 'Delete' })
    await user.click(deleteBtn)

    expect(screen.getByRole('heading', { name: 'Delete Class' })).toBeInTheDocument()
    const textarea = screen.getByRole('textbox', { name: /Reason for deletion/i })
    await user.type(textarea, 'Seasonal schedule concluded')

    await user.click(screen.getByRole('button', { name: 'Delete permanently' }))

    await waitFor(() => {
      const deleteCall = api.calls.find((c) => c.path === '/manager/classes/12/delete')
      expect(deleteCall).toBeDefined()
      expect(deleteCall?.body).toEqual({ reason: 'Seasonal schedule concluded' })
    })
  })
})
