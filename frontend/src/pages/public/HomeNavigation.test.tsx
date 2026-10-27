import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { installFakeFetch, json } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
import { HOME_FAQ } from './homeContent'

function catalog() {
  installFakeFetch((call) => json(200, call.path === '/sports'
    ? [{ id: 1, code: 'FOOTBALL', name: 'Football' }]
    : call.path === '/coaches/public' ? [{ id: 12, fullName: 'Demo Coach', sportIds: [1] }] : []))
}

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/') })

describe('Home navigation disclosures', () => {
  it.each(['mouse', 'keyboard'])('reveals a selected FAQ answer using %s and can reopen the same hash', async (mode) => {
    catalog()
    const user = userEvent.setup()
    renderApp('/')
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    const item = document.getElementById('home-faq-2')!
    const answer = within(item).getByText(HOME_FAQ[2].answer)
    expect(answer).not.toBeVisible()
    for (let attempt = 0; attempt < 2; attempt++) {
      act(() => within(nav).getByRole('link', { name: 'FAQ' }).focus())
      const link = within(nav).getByRole('link', { name: HOME_FAQ[2].question })
      if (mode === 'mouse') await user.click(link)
      else { act(() => link.focus()); await user.keyboard('{Enter}') }
      expect(answer).toBeVisible()
      expect(item.querySelector('summary')).toHaveFocus()
      expect(within(document.getElementById('home-faq-3')!).getByText(HOME_FAQ[3].answer)).not.toBeVisible()
      await user.click(item.querySelector('summary')!)
      expect(answer).not.toBeVisible()
    }
  })

  it('reveals direct and changed FAQ hashes without locking native summary toggles', async () => {
    catalog()
    const user = userEvent.setup()
    renderApp('/#home-faq-2')
    const answer = screen.getByText(HOME_FAQ[2].answer)
    expect(answer).toBeVisible()
    await user.click(document.querySelector('#home-faq-2 summary')!)
    expect(answer).not.toBeVisible()
    act(() => { window.location.hash = '#home-faq-3' })
    await waitFor(() => expect(screen.getByText(HOME_FAQ[3].answer)).toBeVisible())
    expect(answer).not.toBeVisible()
    expect(document.querySelector('#home-faq-3 summary')).toHaveFocus()
    await user.click(document.querySelector('#home-faq-3 summary')!)
    expect(screen.getByText(HOME_FAQ[3].answer)).not.toBeVisible()
  })

  it('uses existing content targets and keeps Home a leaf', async () => {
    catalog()
    renderApp('/')
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'View profile of Demo Coach' })
    expect(within(nav).queryByRole('button', { name: /Home destinations/ })).not.toBeInTheDocument()
    for (const name of ['Sports', 'Packages', 'FAQ']) {
      act(() => within(nav).getByRole('link', { name }).focus())
      const list = within(nav).getByRole('list', { name: `${name} destinations` })
      for (const link of within(list).getAllByRole('link')) {
        expect(document.getElementById(link.getAttribute('href')!.slice(1))).not.toBeNull()
      }
      await user.keyboard('{Escape}')
      expect(within(nav).getByRole('link', { name })).toHaveFocus()
    }
  })

  it('opens on keyboard focus and mouse hover, then closes on exit and outside pointer', async () => {
    catalog()
    renderApp('/')
    const user = userEvent.setup()
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    const sports = within(nav).getByRole('link', { name: 'Sports' })
    const toggle = await within(nav).findByRole('button', { name: 'Toggle Sports destinations' })
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

  it('keeps parent links separate from disclosure and reaches concrete children with arrows', async () => {
    catalog()
    renderApp('/')
    const user = userEvent.setup()
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    await screen.findByRole('button', { name: 'View profile of Demo Coach' })
    for (const name of ['Sports', 'Packages', 'FAQ']) {
      const trigger = within(nav).getByRole('link', { name })
      expect(trigger.textContent).toBe(name)
      const disclosure = within(nav).getByRole('button', { name: `Toggle ${name} destinations` })
      expect(disclosure).toHaveAttribute('aria-controls')
      expect(disclosure.textContent).not.toMatch(/[+-]/)
      act(() => trigger.focus())
      await user.keyboard('{Escape}{ArrowDown}')
      const links = within(within(nav).getByRole('list', { name: `${name} destinations` })).getAllByRole('link')
      await waitFor(() => expect(links[0]).toHaveFocus())
      await user.keyboard('{Escape}')
      expect(trigger).toHaveFocus()
      await user.keyboard('{ArrowUp}')
      await waitFor(() => expect(links[links.length - 1]).toHaveFocus())
      await user.keyboard('{Escape}')
    }
  })

  it('makes Coaches a plain link and excludes generic header entries while retaining the filter reset', async () => {
    catalog()
    renderApp('/')
    const user = userEvent.setup()
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    const coaches = within(nav).getByRole('link', { name: 'Coaches' })
    expect(coaches).toHaveAttribute('href', '#coaches')
    expect(coaches).not.toHaveAttribute('aria-expanded')
    expect(coaches).not.toHaveAttribute('aria-haspopup')
    fireEvent.pointerEnter(coaches.closest('.home-nav__group')!, { pointerType: 'mouse' })
    expect(within(nav).queryByRole('button', { name: /Coaches destinations/ })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('list', { name: 'Coaches destinations', hidden: true })).not.toBeInTheDocument()
    await user.click(coaches)
    expect(document.getElementById('coaches')).toHaveFocus()
    act(() => within(nav).getByRole('link', { name: 'Sports' }).focus())
    expect(within(within(nav).getByRole('list', { name: 'Sports destinations' })).getAllByRole('link').map((a) => a.textContent)).toEqual(['Football'])
    expect(within(nav).queryByRole('link', { name: /All (sports|coaches|packages|faq|questions)/i, hidden: true })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Sport All sports' }))
    expect(screen.getByRole('option', { name: 'All sports' })).toHaveAttribute('aria-selected', 'true')
  })

  it('uses API sports absent from marketing cards as useful finder presets', async () => {
    installFakeFetch((call) => json(200, call.path === '/sports' ? [{ id: 77, code: 'NEW', name: 'New sport' }] : []))
    renderApp('/')
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Toggle Sports destinations' })
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    act(() => within(nav).getByRole('link', { name: 'Sports' }).focus())
    await user.click(within(nav).getByRole('link', { name: 'New sport' }))
    expect(screen.getByRole('button', { name: 'Sport New sport' })).toBeInTheDocument()
    expect(document.getElementById('finder')).toHaveFocus()
  })

  it.each([false, true])('scrolls an in-page CTA once, with reduced motion %s', async (reduce) => {
    catalog()
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: query.includes('reduced-motion') && reduce, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    const previous = HTMLElement.prototype.scrollIntoView
    const scroll = vi.fn()
    HTMLElement.prototype.scrollIntoView = scroll
    try {
      renderApp('/')
      const user = userEvent.setup()
      const link = screen.getAllByRole('link', { name: 'Find a class' })[0]
      await user.click(link)
      expect(scroll).toHaveBeenCalledTimes(1)
      expect(scroll).toHaveBeenCalledWith({ behavior: reduce ? 'instant' : 'smooth', block: 'start' })
      expect(document.getElementById('finder')).toHaveFocus()
      await user.click(link)
      expect(scroll).toHaveBeenCalledTimes(2)
    } finally { HTMLElement.prototype.scrollIntoView = previous }
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
