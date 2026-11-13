import { fireEvent, render, screen, within } from '@testing-library/react'
import type { FormEvent } from 'react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { Select } from './Select'

const options = [
  { value: 'MEMBER', label: 'Member' },
  { value: 'COACH', label: 'Coach' },
]

describe('shared custom Select', () => {
  it('opens the Home-style listbox and selects options with mouse and keyboard', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    function Harness() {
      const [role, setRole] = useState('MEMBER')
      return <Select label="Role" placeholder="All roles" value={role} options={options} onChange={(event) => {
        onChange(event)
        setRole(event.target.value)
      }} />
    }
    render(<Harness />)

    const control = screen.getByRole('combobox', { name: 'Role' })
    await user.click(control)

    expect(screen.getByRole('listbox', { name: 'Role' })).toBeInTheDocument()
    expect(within(screen.getByRole('listbox')).getByRole('option', { name: 'Member' })).toHaveAttribute('aria-selected', 'true')

    await user.keyboard('{ArrowDown}{Enter}')

    expect(onChange).toHaveBeenCalled()
    expect(control).toHaveValue('COACH')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()

    await user.click(control)
    await user.click(within(screen.getByRole('listbox')).getByRole('option', { name: 'All roles' }))
    expect(control).toHaveValue('')
  })

  it('dismisses outside clicks and keeps disabled options unavailable', async () => {
    const user = userEvent.setup()
    render(
      <>
        <Select label="Role" options={[...options, { value: 'ADMIN', label: 'Admin', disabled: true }]} />
        <button type="button">Outside</button>
      </>,
    )

    const control = screen.getByRole('combobox', { name: 'Role' })
    await user.click(control)
    expect(within(screen.getByRole('listbox')).getByRole('option', { name: 'Admin' })).toHaveAttribute('aria-disabled', 'true')

    await user.keyboard('{End}{Enter}')
    expect(control).toHaveValue('COACH')

    await user.click(control)
    await user.click(screen.getByRole('button', { name: 'Outside' }))
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  it('skips disabled options when Home or End opens the listbox', async () => {
    const user = userEvent.setup()
    render(
      <Select
        aria-label="Role"
        options={[
          { value: 'DISABLED', label: 'Disabled', disabled: true },
          { value: 'COACH', label: 'Coach' },
        ]}
      />,
    )

    const control = screen.getByRole('combobox', { name: 'Role' })
    control.focus()
    await user.keyboard('{Home}{Enter}')

    expect(control).toHaveValue('COACH')
  })

  it('retains form submission and required validation through the custom menu', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn((event: FormEvent<HTMLFormElement>) => event.preventDefault())
    render(
      <form onSubmit={onSubmit}>
        <Select label="Role" name="role" required placeholder="Choose a role" options={options} />
        <button type="submit">Save</button>
      </form>,
    )

    const form = screen.getByRole('button', { name: 'Save' }).closest('form')!
    const control = screen.getByRole('combobox', { name: /^Role/ })
    expect(control).toBeInvalid()

    await user.click(control)
    await user.click(within(screen.getByRole('listbox')).getByRole('option', { name: 'Coach' }))

    expect(control).toHaveValue('COACH')
    expect(new FormData(form).get('role')).toBe('COACH')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(onSubmit).toHaveBeenCalledOnce()
  })

  it('keeps controlled values synchronized when changed through the native form interface', () => {
    function Harness() {
      return <Select aria-label="Role" value="MEMBER" options={options} onChange={() => undefined} />
    }
    render(<Harness />)
    const control = screen.getByRole('combobox', { name: 'Role' })
    fireEvent.change(control, { target: { value: 'COACH' } })
    expect(control).toHaveValue('MEMBER')
  })
})
