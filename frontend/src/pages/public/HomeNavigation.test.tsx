import { act, fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { installFakeFetch, json } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

function catalog() {
  installFakeFetch((call) => json(200, call.path === '/sports'
    ? [{ id: 1, code: 'FOOTBALL', name: 'Football' }]
    : call.path === '/coaches/public' ? [{ id: 12, fullName: 'Demo Coach', sportIds: [1] }] : []))
}

afterEach(() => vi.unstubAllGlobals())

describe('Home navigation disclosures', () => {
  it('uses existing content targets and keeps Home a leaf', async () => {
    catalog()
    renderApp('/')
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'View profile of Demo Coach' })
    expect(within(nav).queryByRole('button', { name: /Home destinations/ })).not.toBeInTheDocument()
    for (const name of ['Sports', 'Coaches', 'Packages', 'FAQ']) {
      act(() => within(nav).getByRole('link', { name }).focus())
      const list = within(nav).getByRole('list', { name: `${name} destinations` })
      for (const link of within(list).getAllByRole('link')) {
        expect(document.getElementById(link.getAttribute('href')!.slice(1))).not.toBeNull()
      }
      await user.keyboard('{Escape}')
      expect(within(nav).getByRole('button', { name: `Toggle ${name} destinations` })).toHaveFocus()
    }
  })

  it('opens on keyboard focus and mouse hover, then closes on exit and outside pointer', async () => {
    catalog()
    renderApp('/')
    const user = userEvent.setup()
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    const sports = within(nav).getByRole('link', { name: 'Sports' })
    const toggle = within(nav).getByRole('button', { name: 'Toggle Sports destinations' })
    act(() => sports.focus())
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await user.tab()
    expect(toggle).toHaveFocus()
    await user.keyboard(' ')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await user.keyboard('{Enter}')
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await user.tab()
    expect(within(nav).getByRole('link', { name: 'Football' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(toggle).toHaveFocus()
    fireEvent.pointerEnter(sports.closest('.home-nav__group')!, { pointerType: 'mouse' })
    fireEvent.pointerLeave(sports.closest('.home-nav__group')!, { pointerType: 'mouse' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await user.click(toggle)
    await user.click(screen.getByRole('heading', { name: /Find your sport/ }))
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })

  it('changes header surface at the sentinel and disconnects observers', () => {
    let notify: IntersectionObserverCallback = () => undefined
    const disconnect = vi.fn()
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: IntersectionObserverCallback) { notify = callback }
      observe() {}
      disconnect = disconnect
    })
    catalog()
    const { unmount } = renderApp('/')
    act(() => notify([{ isIntersecting: false, boundingClientRect: { top: -1 } } as IntersectionObserverEntry], {} as IntersectionObserver))
    expect(screen.getByRole('banner')).toHaveClass('home-header--solid')
    act(() => notify([{ isIntersecting: true, boundingClientRect: { top: 96 } } as IntersectionObserverEntry], {} as IntersectionObserver))
    expect(screen.getByRole('banner')).not.toHaveClass('home-header--solid')
    unmount()
    expect(disconnect).toHaveBeenCalledOnce()
  })
})
