import { readFileSync } from 'node:fs'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button'
import { Notice } from './Notice'
import { PasswordInput } from './PasswordInput'
import { Select } from './Select'
import { tabIds } from './tabIds'
import { Tabs, type TabItem } from './Tabs'
import { ToastProvider } from './ToastProvider'
import { useToast } from './toastContext'

const tokens = readFileSync('src/styles/tokens.css', 'utf8')
const components = readFileSync('src/styles/components.css', 'utf8')
const memberStyles = readFileSync('src/styles/member.css', 'utf8')

/** Relative luminance contrast ratio of two #rrggbb colors (WCAG 2.2). */
function contrast(first: string, second: string): number {
  const luminance = (hex: string) => {
    const channels = [1, 3, 5].map((start) => parseInt(hex.slice(start, start + 2), 16) / 255)
    const [r, g, b] = channels.map((value) => (value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }
  const [high, low] = [luminance(first), luminance(second)].sort((a, b) => b - a)
  return (high + 0.05) / (low + 0.05)
}

/** Value of a token in the light block (first match) or the dark block (last match). */
function token(name: string, mode: 'light' | 'dark'): string {
  const matches = Array.from(tokens.matchAll(new RegExp(`${name}:\\s*(#[0-9a-fA-F]{6});`, 'g')))
  const match = mode === 'light' ? matches[0] : matches[matches.length - 1]
  if (!match) {
    throw new Error(`Missing token ${name}`)
  }
  return match[1].toLowerCase()
}

describe('UI-01 shared tokens', () => {
  it('uses the approved Home Royal and Gold brand colors', () => {
    expect(token('--color-primary', 'light')).toBe('#1f4fd8')
    expect(token('--color-navy', 'light')).toBe('#0b1736')
    expect(token('--color-accent', 'light')).toBe('#f5b301')
    expect(token('--color-highlight', 'light')).toBe('#f5b301')
    expect(token('--color-highlight', 'dark')).toBe('#ffc83d')
  })

  it.each([
    ['light primary text on surface', token('--color-primary', 'light'), token('--color-surface', 'light')],
    ['light text on primary fill', token('--color-on-primary', 'light'), token('--color-primary', 'light')],
    ['light primary text on soft fill', token('--color-primary', 'light'), token('--color-primary-soft', 'light')],
    ['text on gold fill', token('--color-on-accent', 'light'), token('--color-accent', 'light')],
    ['dark primary text on surface', token('--color-primary', 'dark'), token('--color-surface', 'dark')],
    ['dark text on primary fill', token('--color-on-primary', 'dark'), token('--color-primary', 'dark')],
    ['dark text on active primary fill', token('--color-on-primary', 'dark'), token('--color-primary-active', 'dark')],
    ['dark primary text on soft fill', token('--color-primary', 'dark'), token('--color-primary-soft', 'dark')],
  ])('meets WCAG AA 4.5:1 for %s', (_name, foreground, background) => {
    expect(contrast(foreground, background)).toBeGreaterThanOrEqual(4.5)
  })

  it('keeps shared notice and tab styles in components.css, not in a screen stylesheet', () => {
    expect(components).toContain('.notice--success {')
    expect(components).toContain('.tabs__tab--active {')
    expect(memberStyles).not.toMatch(/^\.tabs \{/m)
    expect(memberStyles).not.toMatch(/^\.notice--success \{/m)
  })
})

describe('Notice', () => {
  it('announces danger notices assertively with a hidden severity prefix', () => {
    render(<Notice tone="danger">The payment could not be verified.</Notice>)
    const notice = screen.getByRole('alert')
    expect(notice).toHaveTextContent('Error: The payment could not be verified.')
    expect(notice).toHaveClass('notice', 'notice--danger')
  })

  it('stays silent by default for info and can be made polite', () => {
    const { rerender } = render(<Notice tone="info">Transfer the exact amount.</Notice>)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()

    rerender(
      <Notice tone="success" live="polite">
        Saved.
      </Notice>,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Saved.')
  })

  it('renders a title, a warning prefix and an action', () => {
    render(
      <Notice tone="warning" title="Package expires soon" action={<Button size="sm">Renew</Button>}>
        Renew before the end date.
      </Notice>,
    )
    expect(screen.getByText('Package expires soon').closest('p')).toHaveTextContent('Warning: Package expires soon')
    expect(screen.getByRole('button', { name: 'Renew' })).toBeInTheDocument()
  })
})

describe('Tabs', () => {
  const items: TabItem[] = [
    { id: 'packages', label: 'Sport packages' },
    { id: 'locked', label: 'Locked', disabled: true },
    { id: 'card', label: 'Membership card' },
    { id: 'history', label: 'History' },
  ]

  function Harness({ onChange }: { onChange?: (id: string) => void }) {
    const [value, setValue] = useState('packages')
    return (
      <Tabs
        label="My membership"
        idPrefix="membership"
        items={items}
        value={value}
        onChange={(id) => {
          onChange?.(id)
          setValue(id)
        }}
      >
        {(id) => <p>Panel {id}</p>}
      </Tabs>
    )
  }

  it('links every tab to a panel and renders only the selected panel', () => {
    render(<Harness />)
    expect(screen.getByRole('tablist', { name: 'My membership' })).toBeInTheDocument()
    const selected = screen.getByRole('tab', { name: 'Sport packages' })
    expect(selected).toHaveAttribute('aria-selected', 'true')
    expect(selected).toHaveAttribute('tabindex', '0')
    expect(selected).toHaveAttribute('aria-controls', tabIds('membership', 'packages').panel)
    expect(screen.getByRole('tab', { name: 'Membership card' })).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('tabpanel', { name: 'Sport packages' })).toHaveTextContent('Panel packages')
    expect(screen.queryByText('Panel card')).not.toBeInTheDocument()
    expect(document.getElementById(tabIds('membership', 'card').panel)).toHaveAttribute('hidden')
  })

  it('moves with arrows, Home and End, skips disabled tabs and wraps', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)

    await user.click(screen.getByRole('tab', { name: 'Sport packages' }))
    expect(onChange).not.toHaveBeenCalled()

    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'Membership card' })).toHaveFocus()
    expect(screen.getByRole('tab', { name: 'Membership card' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('Panel card')).toBeInTheDocument()

    await user.keyboard('{End}')
    expect(screen.getByRole('tab', { name: 'History' })).toHaveFocus()
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'Sport packages' })).toHaveFocus()
    await user.keyboard('{ArrowLeft}')
    expect(screen.getByRole('tab', { name: 'History' })).toHaveFocus()
    await user.keyboard('{Home}')
    expect(screen.getByRole('tab', { name: 'Sport packages' })).toHaveFocus()

    expect(screen.getByRole('tab', { name: 'Locked' })).toBeDisabled()
    expect(onChange).not.toHaveBeenCalledWith('locked')
  })

  it('keeps the first enabled tab reachable when the value matches no tab', () => {
    render(
      <Tabs label="Broken value" items={items} value="missing" onChange={() => undefined}>
        {(id) => <p>Panel {id}</p>}
      </Tabs>,
    )
    expect(screen.getByRole('tab', { name: 'Sport packages' })).toHaveAttribute('tabindex', '0')
    expect(screen.getByText('Panel packages')).toBeInTheDocument()
  })
})

describe('UI-01 accessibility fixes', () => {
  it('keeps the caller aria-describedby ids on Select together with hint and error ids', () => {
    render(
      <>
        <p id="role-help">Roles control access.</p>
        <Select
          label="Role"
          hint="Pick one role"
          error="Role is required"
          aria-describedby="role-help"
          options={[{ value: 'MEMBER', label: 'Member' }]}
        />
      </>,
    )
    const select = screen.getByRole('combobox', { name: /role/i })
    const ids = select.getAttribute('aria-describedby')?.split(' ') ?? []
    expect(ids).toContain('role-help')
    expect(ids.some((id) => id.endsWith('-error'))).toBe(true)
    expect(select).toHaveAccessibleDescription(/Role is required/)
    expect(select).toHaveAccessibleDescription(/Roles control access/)
  })

  it('gives the password toggle a name that matches its visible text and controls the field', async () => {
    const user = userEvent.setup()
    render(<PasswordInput label="Password" id="pw" />)
    const toggle = screen.getByRole('button', { name: 'Show password' })
    expect(toggle).toHaveTextContent('Show')
    expect(toggle).toHaveAttribute('aria-controls', 'pw')
    expect(toggle).not.toHaveAttribute('aria-pressed')

    await user.click(toggle)
    expect(screen.getByRole('button', { name: 'Hide password' })).toHaveTextContent('Hide')
    expect(screen.getByLabelText(/^password/i)).toHaveAttribute('type', 'text')
  })

  it('does not add a second live region when a Button is loading', () => {
    render(
      <Button loading onClick={() => undefined}>
        Save
      </Button>,
    )
    const button = screen.getByRole('button', { name: 'Save' })
    expect(button).toHaveAttribute('aria-busy', 'true')
    expect(button).toBeDisabled()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(button.querySelector('.spinner')).toHaveAttribute('aria-hidden', 'true')
  })

  it('offers a warning toast helper announced politely', async () => {
    vi.useFakeTimers()
    try {
      function Trigger() {
        const toast = useToast()
        return (
          <button type="button" onClick={() => toast.warning('Card expiring', 'Your card ends in 7 days.')}>
            Warn
          </button>
        )
      }
      render(
        <ToastProvider>
          <Trigger />
        </ToastProvider>,
      )
      act(() => screen.getByRole('button', { name: 'Warn' }).click())
      const toast = screen.getByText('Card expiring').closest('.toast')
      expect(toast).toHaveClass('toast--warning')
      expect(toast?.closest('[aria-live]')).toHaveAttribute('aria-live', 'polite')

      act(() => vi.advanceTimersByTime(5999))
      expect(screen.getByText('Card expiring')).toBeInTheDocument()
      act(() => vi.advanceTimersByTime(1))
      expect(screen.queryByText('Card expiring')).not.toBeInTheDocument()
    } finally {
      vi.useRealTimers()
    }
  })
})
