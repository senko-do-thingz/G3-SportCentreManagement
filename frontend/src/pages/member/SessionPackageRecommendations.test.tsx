import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { installFakeFetch, json } from '../../test/fakeBackend'
import { sportPackage } from '../../test/memberFixtures'
import type { BrowseSession } from '../../api/memberSessionsApi'
import { SessionPackageRecommendations } from './SessionPackageRecommendations'

const session = {
  id: 47, sessionId: '131126BK01', sportId: 3, sportName: 'Basketball', trainingFormat: 'COACH_LED',
  sessionDate: '2026-11-13',
} as BrowseSession

afterEach(() => vi.unstubAllGlobals())

describe('session package recovery', () => {
  it('shows only active packages matching both sport and training format', async () => {
    installFakeFetch((call) => call.path === '/packages' ? json(200, [
      sportPackage({ id: 7, sportId: 3, sportName: 'Basketball', name: 'Basketball coach', trainingFormat: 'COACH_LED', isActive: true }),
      sportPackage({ id: 8, sportId: 3, sportName: 'Basketball', name: 'Basketball self', trainingFormat: 'SELF_TRAINING', isActive: true }),
      sportPackage({ id: 9, sportId: 4, sportName: 'Tennis', name: 'Tennis coach', trainingFormat: 'COACH_LED', isActive: true }),
      sportPackage({ id: 10, sportId: 3, sportName: 'Basketball', name: 'Inactive coach', trainingFormat: 'COACH_LED', isActive: false }),
    ]) : json(404, {}))
    render(<MemoryRouter><SessionPackageRecommendations session={session} memberId={11} /></MemoryRouter>)
    const group = await screen.findByText('Basketball coach')
    const recommendation = group.closest('.booking-package') as HTMLElement
    expect(screen.getAllByRole('link', { name: 'Register this package' })).toHaveLength(1)
    expect(within(recommendation).getByRole('link')).toHaveAttribute('href', '/reception/register?tab=package&memberId=11&packageId=7&sessionId=131126BK01&week=2026-11-13')
    expect(screen.queryByText('Basketball self')).not.toBeInTheDocument()
    expect(screen.queryByText('Tennis coach')).not.toBeInTheDocument()
    expect(screen.queryByText('Inactive coach')).not.toBeInTheDocument()
  })

  it('shows the empty catalog guidance without suggesting a purchase', async () => {
    installFakeFetch((call) => call.path === '/packages' ? json(200, []) : json(404, {}))
    render(<MemoryRouter><SessionPackageRecommendations session={session} memberId={11} /></MemoryRouter>)
    expect(await screen.findByText(/No matching sport package is currently available/)).toHaveTextContent('Package availability must be checked with Manager.')
    expect(screen.queryByRole('link', { name: 'Register this package' })).not.toBeInTheDocument()
  })
})
