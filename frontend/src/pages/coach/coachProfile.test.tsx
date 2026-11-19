import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CoachSelfProfile } from '../../api/coachProfilesApi'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => {
  tokenStorage.clear()
  vi.unstubAllGlobals()
})

const initialCoachProfile: CoachSelfProfile = {
  userId: 7,
  fullName: 'Tran Minh Quan',
  email: 'quan.coach@example.com',
  avatarUrl: '/images/coaches/bao-le.jpg',
  headline: 'Badminton coach',
  bio: 'Footwork and court coverage.',
  yearsExperience: 5,
  specialties: 'Singles, Footwork',
  isPublic: true,
  displayOrder: 1,
  sports: [{ id: 1, name: 'Badminton' }],
  certifications: [
    {
      id: 10,
      sportId: 1,
      sportName: 'Badminton',
      name: 'BWF Level 1',
      issuer: 'BWF',
      issuedOn: '2024-01-10',
      expiresOn: '2028-01-10',
      verified: true,
    },
  ],
  certificationOptions: [
    {
      key: 'BWF Level 1|BWF|1',
      name: 'BWF Level 1',
      issuer: 'BWF',
      sportId: 1,
      sportName: 'Badminton',
    },
    {
      key: 'BWF Level 2|Badminton World Federation|1',
      name: 'BWF Level 2',
      issuer: 'Badminton World Federation',
      sportId: 1,
      sportName: 'Badminton',
    },
  ],
}

describe('Coach self-service profile page (/coach/profile)', () => {
  it('loads coach profile, updates bio and headline, and adds/removes certifications from the center dropdown', async () => {
    let current = { ...initialCoachProfile }
    tokenStorage.startSession({ accessToken: 'coach-token', refreshToken: 'refresh' })
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/users/me') {
        return json(200, { ...MEMBER, id: 7, fullName: 'Tran Minh Quan', email: 'quan.coach@example.com', role: 'COACH' })
      }
      if (call.path === '/staff/notifications') return json(200, [])
      if (call.path === '/coach/profile' && call.method === 'GET') {
        return json(200, current)
      }
      if (call.path === '/coach/profile' && call.method === 'PUT') {
        const body = call.body as Record<string, unknown>
        current = {
          ...current,
          headline: String(body.headline ?? ''),
          bio: String(body.bio ?? ''),
          yearsExperience: (body.yearsExperience as number | null) ?? null,
          specialties: String(body.specialties ?? ''),
        }
        return json(200, current)
      }
      if (call.path === '/coach/profile/certifications' && call.method === 'POST') {
        const body = call.body as Record<string, unknown>
        current = {
          ...current,
          certifications: [
            ...current.certifications,
            {
              id: 22,
              sportId: (body.sportId as number | null) ?? 1,
              sportName: 'Badminton',
              name: String(body.name),
              issuer: (body.issuer as string | null) ?? null,
              issuedOn: (body.issuedOn as string | null) ?? null,
              expiresOn: (body.expiresOn as string | null) ?? null,
              verified: false,
            },
          ],
        }
        return json(201, current)
      }
      if (call.path === '/coach/profile/certifications/22' && call.method === 'DELETE') {
        current = {
          ...current,
          certifications: current.certifications.filter((c) => c.id !== 22),
        }
        return json(200, current)
      }
      return backendError(404, 'Not found')
    })

    const user = userEvent.setup()
    renderApp('/coach/profile')

    expect(await screen.findByRole('heading', { name: 'My Profile' })).toBeInTheDocument()
    const headlineInput = await screen.findByLabelText('Headline')
    expect(headlineInput).toHaveValue('Badminton coach')

    // Cap nhat thong tin ho so cong khai cua Coach
    await user.clear(headlineInput)
    await user.type(headlineInput, 'National badminton coach')
    const yearsInput = screen.getByLabelText('Years of coaching')
    await user.clear(yearsInput)
    await user.type(yearsInput, '8')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    await waitFor(() => {
      expect(calls.some((c) => c.path === '/coach/profile' && c.method === 'PUT')).toBe(true)
    })
    const updatedNotices = await screen.findAllByText('Your coaching profile has been updated.')
    expect(updatedNotices.length).toBeGreaterThan(0)
    const preview = screen.getByRole('complementary', { name: 'Public preview' })
    expect(within(preview).getByText('8 years')).toBeInTheDocument()

    // Chon va them chung chi tu dropdown danh muc cua trung tam
    const select = screen.getByRole('combobox', { name: 'Available center certifications' })
    await user.selectOptions(select, 'BWF Level 2|Badminton World Federation|1')
    await user.click(screen.getByRole('button', { name: 'Add certification' }))

    const certList = await screen.findByRole('list', { name: 'Your certifications' })
    expect(within(certList).getByText('BWF Level 2')).toBeInTheDocument()
    expect(within(certList).getByText('Not verified')).toBeInTheDocument()

    // Xoa chung chi vua them
    await user.click(within(certList).getByRole('button', { name: 'Remove BWF Level 2' }))
    await waitFor(() => {
      expect(within(certList).queryByText('BWF Level 2')).not.toBeInTheDocument()
    })
  })
})
