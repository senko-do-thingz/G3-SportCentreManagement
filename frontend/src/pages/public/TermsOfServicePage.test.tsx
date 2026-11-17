import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/renderApp'
import { installFakeFetch, json } from '../../test/fakeBackend'
import { TOS_SECTIONS } from './termsOfServiceContent'

afterEach(() => vi.unstubAllGlobals())

describe('Terms of Service Page', () => {
  it('renders the page title, kicker and all 9 accordion sections', async () => {
    installFakeFetch((call) => json(200, call.path === '/users/me' ? {} : []))
    renderApp('/terms')

    expect(await screen.findByRole('heading', { level: 1, name: 'Terms of Service' })).toBeInTheDocument()
    expect(screen.getByText('Legal and Policies')).toBeInTheDocument()

    // Kiem tra du 9 muc accordion duoc hien thi
    for (const section of TOS_SECTIONS) {
      expect(screen.getByRole('button', { name: new RegExp(section.title, 'i') })).toBeInTheDocument()
    }
  })

  it('allows expanding and collapsing individual accordion items', async () => {
    installFakeFetch((call) => json(200, call.path === '/users/me' ? {} : []))
    const user = userEvent.setup()
    renderApp('/terms')

    await screen.findByRole('heading', { level: 1, name: 'Terms of Service' })

    // Muc dau tien mo mac dinh
    const firstButton = screen.getByRole('button', { name: /1\. General Provisions/i })
    expect(firstButton).toHaveAttribute('aria-expanded', 'true')

    // Thu gon muc dau tien
    await user.click(firstButton)
    expect(firstButton).toHaveAttribute('aria-expanded', 'false')

    // Mo muc thu hai
    const secondButton = screen.getByRole('button', { name: /2\. Account and User Responsibilities/i })
    expect(secondButton).toHaveAttribute('aria-expanded', 'false')
    await user.click(secondButton)
    expect(secondButton).toHaveAttribute('aria-expanded', 'true')
  })

  it('toggles all accordion sections with the Expand all / Collapse all button', async () => {
    installFakeFetch((call) => json(200, call.path === '/users/me' ? {} : []))
    const user = userEvent.setup()
    renderApp('/terms')

    await screen.findByRole('heading', { level: 1, name: 'Terms of Service' })

    const toggleAllButton = screen.getByRole('button', { name: /expand all/i })
    await user.click(toggleAllButton)

    // Tat ca 9 muc phai duoc mo
    for (const section of TOS_SECTIONS) {
      expect(screen.getByRole('button', { name: new RegExp(section.title, 'i') })).toHaveAttribute(
        'aria-expanded',
        'true',
      )
    }

    // Nhan tiep de thu gon tat ca
    const collapseAllButton = screen.getByRole('button', { name: /collapse all/i })
    await user.click(collapseAllButton)

    for (const section of TOS_SECTIONS) {
      expect(screen.getByRole('button', { name: new RegExp(section.title, 'i') })).toHaveAttribute(
        'aria-expanded',
        'false',
      )
    }
  })

  it('renders the contact section directing users to Receptionist Desk and Management Office', async () => {
    installFakeFetch((call) => json(200, call.path === '/users/me' ? {} : []))
    renderApp('/terms')

    expect(await screen.findByRole('heading', { level: 2, name: /Questions or Need Assistance/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 3, name: 'Receptionist Desk' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 3, name: 'Center Management' })).toBeInTheDocument()
    expect(screen.getByText(/Ground Floor Lobby, Sportify Center/i)).toBeInTheDocument()
    expect(screen.getByText(/Executive Suite, Level 2, Sportify Center/i)).toBeInTheDocument()
  })

  it('is accessible via both /terms and /terms-of-service routes', async () => {
    installFakeFetch((call) => json(200, call.path === '/users/me' ? {} : []))
    renderApp('/terms-of-service')

    expect(await screen.findByRole('heading', { level: 1, name: 'Terms of Service' })).toBeInTheDocument()
  })

  it('has links to Terms of Service, Privacy Policy and Contact in the footer', async () => {
    installFakeFetch((call) => json(200, call.path === '/users/me' ? {} : []))
    renderApp('/')

    const tosLinks = await screen.findAllByRole('link', { name: 'Terms of Service' })
    expect(tosLinks.length).toBeGreaterThanOrEqual(1)
    expect(tosLinks[0]).toHaveAttribute('href', '/terms')

    const privacyLinks = screen.getAllByRole('link', { name: 'Privacy Policy' })
    expect(privacyLinks.length).toBeGreaterThanOrEqual(1)
    expect(privacyLinks[0]).toHaveAttribute('href', '/terms#privacy')

    const contactLinks = screen.getAllByRole('link', { name: 'Contact' })
    expect(contactLinks.length).toBeGreaterThanOrEqual(1)
  })
})
