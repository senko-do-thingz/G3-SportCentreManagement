import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())
const profile = { memberId: 42, memberName: 'Lan', memberCode: 'M42', mainGoal: 'Improve passing', currentLevel: 'BEGINNER' }
function setup(extra: (call: RecordedCall) => Response | Promise<Response>, route = '/coach/training/progress?classId=1&memberId=42') {
  tokenStorage.startSession({ accessToken: 'coach', refreshToken: 'refresh' })
  const backend = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, { ...MEMBER, role: 'COACH' })
    if (call.path === '/coach/training/classes') return json(200, [{ id: 1, name: 'Football', sportId: 1 }, { id: 2, name: 'Tennis', sportId: 2 }])
    if (call.path.endsWith('/classes/1/members')) return json(200, [{ bookingId: 1, memberId: 42, memberName: 'Lan' }, { bookingId: 2, memberId: 43, memberName: 'Minh' }])
    if (call.path.endsWith('/classes/2/members')) return json(200, [])
    return extra(call)
  })
  renderApp(route)
  return backend
}
it('shows private profile, truthful notes-only outcomes and persisted feedback', async () => {
  setup(call => call.path.endsWith('/profile') ? json(200, profile) : json(200, { learner: profile, sessions: [{ resultId: 1, sessionCode: '051126FB01', date: '2026-11-05', attendanceStatus: 'PRESENT', notesOnly: true, scores: [], notes: 'Technique practice' }], feedback: [{ id: 1, planTitle: 'Passing plan', comments: 'Saved guidance', rating: 4, createdAt: '2026-11-05T09:00:00' }], interpretation: 'Recorded results' }))
  expect(await screen.findByText('Improve passing')).toBeInTheDocument()
  expect(screen.getByText('Notes only - no numeric trend')).toBeInTheDocument()
  expect(screen.getByText('Saved guidance')).toBeInTheDocument()
})
it('clears previous learner data while another learner is pending and on class changes', async () => {
  let resolve!: (result: Response) => void
  setup(call => call.path.includes('/members/43/') ? new Promise<Response>(done => { resolve = done }) : call.path.endsWith('/profile') ? json(200, profile) : json(200, { learner: profile, sessions: [], feedback: [], interpretation: 'Recorded' }))
  await screen.findByText('Improve passing')
  const user = userEvent.setup()
  await user.selectOptions(screen.getByLabelText('Member'), '43')
  expect(screen.queryByText('Improve passing')).not.toBeInTheDocument()
  await waitFor(() => expect(resolve).toBeDefined())
  await user.selectOptions(screen.getByLabelText('Assigned class'), '2')
  resolve(backendError(404, 'Assigned learner not found'))
  expect(await screen.findByText('No eligible confirmed members in this class.')).toBeInTheDocument()
  expect(screen.queryByText('Improve passing')).not.toBeInTheDocument()
})
it('shows assignment denial without a profile or invented scores', async () => {
  setup(() => backendError(404, 'Assigned learner not found'))
  expect(await screen.findByText('Assigned learner not found')).toBeInTheDocument()
  expect(screen.queryByText('Profile and goals')).not.toBeInTheDocument()
})

it('safely rejects a deep-linked member outside the assigned class candidates', async () => {
  const { calls } = setup(() => backendError(404, 'Unexpected progress request'), '/coach/training/progress?classId=1&memberId=999')
  expect(await screen.findByText('The linked member is not available in this assigned class. Choose an eligible member from the list.')).toBeInTheDocument()
  expect(screen.getByLabelText('Member')).toHaveValue('')
  expect(calls.some(call => call.path.includes('/members/999/'))).toBe(false)
  expect(screen.getByText('Choose a member to view progress')).toBeInTheDocument()
})
