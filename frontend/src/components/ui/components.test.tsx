import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/renderApp'
import { StatusBadge } from './Badge'
import { Table } from './Table'
import { ToastProvider } from './ToastProvider'
import { useToast } from './toastContext'
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
      'Filters',
      'Notices',
      'Tabs',
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

    expect(await within(table).findByText('Demo User 06')).toBeInTheDocument()
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
  it('serves /ui-kit only in development builds', async () => {
    vi.stubEnv('DEV', false)
    renderApp('/ui-kit')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    vi.unstubAllEnvs()
  })

  it('keeps the previous rows visible under an overlay while loading', () => {
    const columns = [{ key: 'name', header: 'Name' }]
    const rows = [{ id: 1, name: 'Taylor Nguyen' }]
    const { container, rerender } = render(
      <Table caption="People" columns={columns} rows={rows} rowKey={(row) => row.id} />,
    )
    rerender(<Table caption="People" columns={columns} rows={rows} rowKey={(row) => row.id} loading />)

    expect(screen.getByText('Taylor Nguyen')).toBeInTheDocument()
    expect(container.querySelector('tbody')).toHaveAttribute('aria-busy', 'true')
    expect(container.querySelector('.table__overlay')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Loading rows')
  })

  it('shows the row spinner only for a first load without rows', () => {
    const { container } = render(
      <Table caption="People" columns={[{ key: 'name', header: 'Name' }]} rows={[]} rowKey={() => 1} loading />,
    )
    expect(container.querySelector('.table__overlay')).not.toBeInTheDocument()
    expect(container.querySelector('.table__state-cell .spinner')).toBeInTheDocument()
  })

  it('announces each toast once, through one live region', async () => {
    const user = userEvent.setup()
    function Trigger() {
      const toast = useToast()
      return (
        <>
          <button type="button" onClick={() => toast.success('Saved')}>
            ok
          </button>
          <button type="button" onClick={() => toast.error('Failed')}>
            fail
          </button>
        </>
      )
    }
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    )
    await user.click(screen.getByRole('button', { name: 'ok' }))
    await user.click(screen.getByRole('button', { name: 'fail' }))

    const saved = screen.getByText('Saved').closest('.toast') as HTMLElement
    const failed = screen.getByText('Failed').closest('.toast') as HTMLElement
    for (const toast of [saved, failed]) {
      expect(toast).not.toHaveAttribute('role')
      expect(toast).not.toHaveAttribute('aria-live')
    }
    expect(saved.closest('[aria-live]')).toHaveAttribute('aria-live', 'polite')
    expect(failed.closest('[aria-live]')).toHaveAttribute('aria-live', 'assertive')
    expect(saved.parentElement?.parentElement?.closest('[aria-live]')).toBeNull()
  })

  describe('modal focus trap', () => {
    function TrapHarness() {
      return (
        <>
          <button type="button">Outside</button>
          <Modal open onClose={() => undefined} title="Trap" dismissible={false}>
            {/* Hidden elements sit at both edges, exactly where the trap wraps focus. */}
            <button type="button" hidden>
              Hidden attribute
            </button>
            <div aria-hidden="true">
              <button type="button">Aria hidden</button>
            </div>
            <button type="button">First</button>
            <button type="button">Last</button>
            <button type="button" style={{ display: 'none' }}>
              Display none
            </button>
          </Modal>
        </>
      )
    }

    it('skips hidden elements when wrapping with Tab and Shift+Tab', async () => {
      const user = userEvent.setup()
      render(<TrapHarness />)
      expect(screen.getByRole('button', { name: 'First' })).toHaveFocus()

      await user.tab({ shift: true })
      expect(screen.getByRole('button', { name: 'Last' })).toHaveFocus()
      await user.tab()
      expect(screen.getByRole('button', { name: 'First' })).toHaveFocus()
    })

    it('pulls focus back into the dialog when it moved outside', async () => {
      const user = userEvent.setup()
      render(<TrapHarness />)

      screen.getByRole('button', { name: 'Outside', hidden: true }).focus()
      expect(screen.getByRole('button', { name: 'First' })).toHaveFocus()

      await user.tab()
      expect(document.activeElement && screen.getByRole('dialog').contains(document.activeElement)).toBe(true)
    })
  })
})
