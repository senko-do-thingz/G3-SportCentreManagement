import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/renderApp'
import { installFakeFetch, json } from '../../test/fakeBackend'
import { HOME_FACILITIES, HOME_SPORTS } from './homeContent'
import { buildCredits, COACH_DEMO_PHOTOS } from './creditsContent'

afterEach(() => vi.unstubAllGlobals())

describe('Photo credits', () => {
  it('lists every Home photo, coach portrait, video and the font with its source and license', () => {
    const groups = buildCredits()
    const photos = groups.find((group) => group.title === 'Photos')!
    expect(photos.items).toHaveLength(2 + HOME_SPORTS.length + HOME_FACILITIES.length)
    expect(photos.items.every((item) => /^https:\/\/images\.unsplash\.com\/photo-[\w-]+$/.test(item.link))).toBe(true)
    expect(groups.find((group) => group.title === 'Demo coach portraits')!.items).toHaveLength(COACH_DEMO_PHOTOS.length)
    expect(groups.find((group) => group.title === 'Background videos')!.items.map((item) => item.link)).toEqual(expect.arrayContaining([expect.stringContaining('-44448/')]))
    expect(groups.map((group) => group.license.url)).toEqual(expect.arrayContaining(['https://unsplash.com/license', 'https://openfontlicense.org']))
  })

  it('is reachable from the Home footer and opens sources in a new tab safely', async () => {
    installFakeFetch((call) => json(200, call.path === '/users/me' ? {} : []))
    const user = userEvent.setup()
    renderApp('/')
    await user.click(screen.getByRole('link', { name: 'Photo credits' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Photo credits' })).toBeInTheDocument()
    const photos = screen.getByRole('region', { name: 'Photos' })
    const links = within(photos).getAllByRole('link')
    expect(links.every((link) => link.getAttribute('target') === '_blank' && link.getAttribute('rel') === 'noopener noreferrer')).toBe(true)
    expect(within(photos).getByText('Where you play - Indoor Pool')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Back to Home' })[0]).toHaveAttribute('href', '/')
  })
})
