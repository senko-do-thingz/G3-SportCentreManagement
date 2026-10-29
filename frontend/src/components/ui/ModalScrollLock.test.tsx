import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useState } from 'react'
import userEvent from '@testing-library/user-event'
import { Modal } from './Modal'

let bodyStyle: string
let rootStyle: string
beforeEach(() => {
  bodyStyle = document.body.style.cssText
  rootStyle = document.documentElement.style.cssText
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  document.body.style.cssText = bodyStyle
  document.documentElement.style.cssText = rootStyle
})

function Stack({ outer, inner }: { outer: boolean; inner: boolean }) {
  return <><button>Original opener</button>
    <Modal open={outer} onClose={() => undefined} title="Outer"><button>Outer action</button></Modal>
    <Modal open={inner} onClose={() => undefined} title="Inner"><button>Inner action</button></Modal>
  </>
}

describe('shared modal document lock', () => {
  it('keeps shared dialog classes and focus when adding a scoped presentation class', () => {
    const { rerender } = render(<Modal open title="Payout" onClose={() => undefined} className="home-page payment-dialog"><button>Record</button></Modal>)
    const dialog = screen.getByRole('dialog', { name: 'Payout' })
    expect(dialog).toHaveClass('modal', 'modal--md', 'home-page', 'payment-dialog')
    expect(dialog).toContainElement(document.activeElement as HTMLElement)
    rerender(<Modal open title="Payout" onClose={() => undefined}><button>Record</button></Modal>)
    expect(dialog).toHaveClass('modal', 'modal--md')
    expect(dialog).not.toHaveClass('payment-dialog')
    expect(document.body.style.overflow).toBe('hidden')
  })

  it.each([0, 17])('restores exact overflow/gutter styles across repeated out-of-order stacks with a %ipx gutter', (gap) => {
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(window.innerWidth - gap)
    document.body.style.setProperty('overflow-y', 'auto', 'important')
    document.body.style.paddingRight = '7px'
    document.documentElement.style.setProperty('scrollbar-gutter', 'auto', 'important')
    const { rerender } = render(<Stack outer={false} inner={false} />)
    const opener = screen.getByRole('button', { name: 'Original opener' })
    opener.focus()
    for (let cycle = 0; cycle < 3; cycle++) {
      rerender(<Stack outer inner={false} />)
      expect(document.body.style.overflow).toBe('hidden')
      expect(document.body.style.getPropertyPriority('overflow')).toBe('important')
      expect(document.documentElement.style.getPropertyValue('scrollbar-gutter')).toBe(gap ? 'stable' : 'auto')
      expect(document.body.style.paddingRight).toBe('7px')
      rerender(<Stack outer inner />)
      expect(document.body.style.paddingRight).toBe('7px')
      rerender(<Stack outer={false} inner />)
      expect(document.body.style.overflow).toBe('hidden')
      expect(screen.getByRole('dialog', { name: 'Inner' })).toContainElement(document.activeElement as HTMLElement)
      rerender(<Stack outer={false} inner={false} />)
      expect(document.body.style.overflowY).toBe('auto')
      expect(document.body.style.getPropertyPriority('overflow-y')).toBe('important')
      expect(document.body.style.paddingRight).toBe('7px')
      expect(document.documentElement.style.getPropertyValue('scrollbar-gutter')).toBe('auto')
      expect(document.documentElement.style.getPropertyPriority('scrollbar-gutter')).toBe('important')
      expect(opener).toHaveFocus()
    }
  })

  it('keeps the lock and returns focus to the surviving outer dialog', () => {
    const { rerender } = render(<Stack outer inner={false} />)
    const action = within(screen.getByRole('dialog', { name: 'Outer' })).getByRole('button', { name: 'Outer action' })
    action.focus()
    rerender(<Stack outer inner />)
    rerender(<Stack outer inner={false} />)
    expect(document.body.style.overflow).toBe('hidden')
    expect(action).toHaveFocus()
  })

  it('retains an existing stable both-edges gutter throughout the lock', () => {
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(window.innerWidth - 17)
    document.documentElement.style.setProperty('scrollbar-gutter', 'stable both-edges', 'important')
    const { unmount } = render(<Stack outer inner={false} />)
    expect(document.documentElement.style.getPropertyValue('scrollbar-gutter')).toBe('stable both-edges')
    expect(document.documentElement.style.getPropertyPriority('scrollbar-gutter')).toBe('important')
    unmount()
    expect(document.documentElement.style.getPropertyValue('scrollbar-gutter')).toBe('stable both-edges')
  })

  it('restores opener focus after the backdrop mouse down without subsequent default focus loss', async () => {
    function Harness() {
      const [open, setOpen] = useState(false)
      return <><button onClick={() => setOpen(true)}>Open dialog</button>
        <Modal open={open} onClose={() => setOpen(false)} title="Backdrop test"><button>Inside</button></Modal>
      </>
    }
    render(<Harness />)
    const user = userEvent.setup()
    const opener = screen.getByRole('button', { name: 'Open dialog' })
    await user.click(opener)
    await user.click(document.querySelector('.modal-backdrop')!)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(opener).toHaveFocus()
  })
})
