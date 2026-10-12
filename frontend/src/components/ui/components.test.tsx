import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/renderApp'
import { StatusBadge } from './Badge'
import { Modal } from './Modal'
import { Pagination } from './Pagination'
import { pageWindow } from './pageWindow'

describe('shared UI components', () => {
  it('renders every component on the /ui-kit demo page', async () => {
    renderApp('/ui-kit')

    expect(await screen.findByRole('heading', { name: 'UI kit' })).toBeInTheDocument()
    for (const section of [
      'Design tokens',
      'Buttons',
      'Inputs',
      'Select',
      'Badges',
      'Table with pagination',
      'Toast',
      'Spinner',
      'Empty state',
      'Error state',
    ]) {
      expect(screen.getByRole('heading', { name: section })).toBeInTheDocument()
    }
    expect(screen.getByRole('table', { name: 'Demo center accounts' })).toBeInTheDocument()
    expect(screen.getByText('1.350.000 VND')).toBeInTheDocument()
    expect(screen.getByText('04 Jul 2027')).toBeInTheDocument()
  })

  it('opens the modal, closes it with Escape and shows a toast', async () => {
    const user = userEvent.setup()
    renderApp('/ui-kit')

    await user.click(await screen.findByRole('button', { name: 'Open modal' }))
    const dialog = screen.getByRole('dialog', { name: 'Add staff account' })
    expect(dialog).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Success toast' }))
    expect(await screen.findByText('Your changes were saved.')).toBeInTheDocument()
  })

  it('pages through the demo table', async () => {
    const user = userEvent.setup()
    renderApp('/ui-kit')
    const table = await screen.findByRole('table', { name: 'Demo center accounts' })
    expect(within(table).getByText('Demo User 01')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Next' }))

    expect(within(table).getByText('Demo User 06')).toBeInTheDocument()
    expect(screen.getByText('Showing 6-10 of 23')).toBeInTheDocument()
  })

  it('maps status codes to badge tones and labels', () => {
    render(
      <>
        <StatusBadge status="ACTIVE" />
        <StatusBadge status="PENDING_PAYMENT" />
        <StatusBadge status="EXPIRED" />
        <StatusBadge status="SOMETHING_NEW" />
      </>,
    )
    expect(screen.getByText('Active')).toHaveClass('badge--success')
    expect(screen.getByText('Pending payment')).toHaveClass('badge--warning')
    expect(screen.getByText('Expired')).toHaveClass('badge--danger')
    expect(screen.getByText('Something new')).toHaveClass('badge--neutral')
  })

  it('builds a compact page window with gaps', () => {
    expect(pageWindow(0, 1)).toEqual([0])
    expect(pageWindow(5, 10)).toEqual([0, null, 4, 5, 6, null, 9])
    expect(pageWindow(0, 3)).toEqual([0, 1, 2])
  })

  it('disables Previous on the first page and reports the selected page', async () => {
    const onPageChange = vi.fn()
    const user = userEvent.setup()
    render(<Pagination page={0} pageSize={10} totalItems={42} onPageChange={onPageChange} />)

    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Page 1' })).toHaveAttribute('aria-current', 'page')
    await user.click(screen.getByRole('button', { name: 'Page 5' }))
    expect(onPageChange).toHaveBeenCalledWith(4)
  })

  it('keeps a non dismissible modal open on Escape', async () => {
    const user = userEvent.setup()
    function Harness() {
      const [open, setOpen] = useState(true)
      return (
        <Modal open={open} onClose={() => setOpen(false)} title="Processing payment" dismissible={false}>
          <button type="button">Inside</button>
        </Modal>
      )
    }
    render(<Harness />)
    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog', { name: 'Processing payment' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Inside' })).toHaveFocus()
  })
  it('closes only the top modal on Escape when modals are stacked', async () => {
    const user = userEvent.setup()
    function Harness() {
      const [outer, setOuter] = useState(true)
      const [inner, setInner] = useState(true)
      return (
        <>
          <Modal open={outer} onClose={() => setOuter(false)} title="Outer">
            <button type="button">Outer button</button>
          </Modal>
          <Modal open={inner} onClose={() => setInner(false)} title="Inner">
            <button type="button">Inner button</button>
          </Modal>
        </>
      )
    }
    render(<Harness />)
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Inner' })).not.toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Outer' })).toBeInTheDocument()
  })

  it('restores focus to the opener after dismissible changed while open', async () => {
    const user = userEvent.setup()
    function Harness() {
      const [open, setOpen] = useState(false)
      const [busy, setBusy] = useState(false)
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            Opener
          </button>
          <Modal open={open} onClose={() => setOpen(false)} title="Edit" dismissible={!busy}>
            <button type="button" onClick={() => setBusy(true)}>
              Start saving
            </button>
            <button type="button" onClick={() => setOpen(false)}>
              Done
            </button>
          </Modal>
        </>
      )
    }
    render(<Harness />)
    await user.click(screen.getByRole('button', { name: 'Opener' }))
    await user.click(screen.getByRole('button', { name: 'Start saving' }))
    await user.click(screen.getByRole('button', { name: 'Done' }))
    expect(screen.getByRole('button', { name: 'Opener' })).toHaveFocus()
  })
})
