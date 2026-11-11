import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState, type ComponentProps, type FormEvent } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { CalendarDateField } from './CalendarDateField'

function ControlledDate(props: Partial<ComponentProps<typeof CalendarDateField>> = {}) {
  const [value, setValue] = useState(props.value ?? '2026-11-11')
  return <CalendarDateField label="Session date" value={value} onChange={setValue} {...props} />
}

function SubmittingDate({ onSubmit, required = true }: { onSubmit: (value: string) => void; required?: boolean }) {
  const [value, setValue] = useState('2026-11-15')
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit(value)
  }
  return <form onSubmit={submit} noValidate>
    <CalendarDateField label="Session date" value={value} onChange={setValue} required={required} />
    <output data-testid="canonical-date">{value}</output>
    <button type="submit">Save</button>
  </form>
}

describe('CalendarDateField', () => {
  it('shows dd/MM/yyyy in the visible text control while preserving an ISO form value', () => {
    const { container } = render(<ControlledDate name="sessionDate" />)
    const visible = screen.getByLabelText('Session date')
    expect(visible).toHaveAttribute('type', 'text')
    expect(visible).toHaveValue('11/11/2026')
    expect(visible).toHaveAccessibleDescription('Date format: dd/MM/yyyy.')
    expect(container.querySelector('input[name="sessionDate"]')).toHaveValue('2026-11-11')
  })

  it('commits a complete valid keyboard value as ISO and never emits partial input', async () => {
    const user = userEvent.setup()
    const change = vi.fn()
    render(<CalendarDateField label="Session date" value="" onChange={change} />)
    const visible = screen.getByLabelText('Session date')
    await user.type(visible, '02/01/2026')
    expect(change).toHaveBeenCalledOnce()
    expect(change).toHaveBeenCalledWith('2026-01-02')
    await user.tab()
    expect(change).toHaveBeenCalledWith('2026-01-02')
  })

  it.each(['29/02/2027', '31/04/2026', '11/11/'])('keeps invalid value %s out of application state', async (value) => {
    const user = userEvent.setup()
    const change = vi.fn()
    render(<CalendarDateField label="Session date" value="" onChange={change} />)
    await user.type(screen.getByLabelText('Session date'), value)
    await user.tab()
    expect(change).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a valid date in dd/MM/yyyy format.')
  })

  it('blocks a noValidate form instead of submitting a stale committed value', async () => {
    const user = userEvent.setup()
    const submit = vi.fn()
    render(<SubmittingDate onSubmit={submit} />)
    const visible = screen.getByLabelText(/Session date/)
    await user.clear(visible)
    await user.type(visible, '31/11/2026')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a valid date in dd/MM/yyyy format.')
    expect(submit).not.toHaveBeenCalled()
    expect(screen.getByTestId('canonical-date')).toHaveTextContent('')
    expect(visible).toHaveFocus()
  })

  it('commits a valid keyboard date before an immediate submit', async () => {
    const user = userEvent.setup()
    const submit = vi.fn()
    render(<SubmittingDate onSubmit={submit} />)
    const visible = screen.getByLabelText(/Session date/)
    await user.clear(visible)
    await user.type(visible, '29/02/2028')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(submit).toHaveBeenCalledWith('2028-02-29')
  })

  it('blocks Enter for invalid text and restores the previous committed value on Escape', async () => {
    const user = userEvent.setup()
    const submit = vi.fn()
    render(<SubmittingDate onSubmit={submit} />)
    const visible = screen.getByLabelText(/Session date/)
    await user.clear(visible)
    await user.type(visible, '29/02/2027{Enter}')
    expect(submit).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a valid date in dd/MM/yyyy format.')
    await user.type(visible, '{Escape}')
    expect(visible).toHaveValue('15/11/2026')
    expect(screen.getByTestId('canonical-date')).toHaveTextContent('2026-11-15')
  })

  it('applies an external reset while the text field has focus', async () => {
    const user = userEvent.setup()
    const change = vi.fn()
    const { rerender } = render(<CalendarDateField label="Session date" value="2026-11-15" onChange={change} />)
    const visible = screen.getByLabelText('Session date')
    await user.click(visible)
    await user.clear(visible)
    await user.type(visible, '11/11/')
    rerender(<CalendarDateField label="Session date" value="2028-02-29" onChange={change} />)
    expect(visible).toHaveValue('29/02/2028')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('validates required, min and max bounds with formatted messages', async () => {
    const user = userEvent.setup()
    const change = vi.fn()
    const { rerender } = render(<CalendarDateField label="Session date" value="" onChange={change} required min="2026-11-01" max="2026-11-30" />)
    await user.click(screen.getByLabelText(/Session date/, { selector: 'input[type="text"]' }))
    await user.tab()
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a date in dd/MM/yyyy format.')
    rerender(<CalendarDateField label="Session date" value="2026-10-31" onChange={change} min="2026-11-01" max="2026-11-30" />)
    await user.click(screen.getByLabelText('Session date'))
    await user.tab()
    expect(screen.getByRole('alert')).toHaveTextContent('Date must be on or after 01/11/2026.')
  })

  it('commits a calendar picker choice and restores focus to the visible editor', async () => {
    const { container } = render(<ControlledDate value="" />)
    fireEvent.change(container.querySelector('.calendar-date-field__picker')!, { target: { value: '2028-02-29' } })
    await Promise.resolve()
    expect(screen.getByLabelText('Session date')).toHaveValue('29/02/2028')
    expect(screen.getByLabelText('Session date')).toHaveFocus()
  })

  it('clears an optional value, restores on Escape, and respects disabled/read-only states', async () => {
    const user = userEvent.setup()
    const change = vi.fn()
    const { rerender } = render(<CalendarDateField label="Session date" value="2026-11-11" onChange={change} />)
    const visible = screen.getByLabelText('Session date')
    await user.clear(visible)
    await user.tab()
    expect(change).toHaveBeenCalledWith('')
    rerender(<CalendarDateField label="Session date" value="2026-11-12" onChange={change} />)
    expect(visible).toHaveValue('12/11/2026')
    rerender(<CalendarDateField label="Session date" value="2026-11-11" onChange={change} />)
    await user.click(visible)
    await user.clear(visible)
    await user.type(visible, '31/02/2026{Escape}')
    expect(visible).toHaveValue('11/11/2026')
    rerender(<CalendarDateField label="Session date" value="2026-11-11" onChange={change} disabled />)
    expect(visible).toBeDisabled()
    expect(document.querySelector('.calendar-date-field__picker')).toBeDisabled()
    rerender(<CalendarDateField label="Session date" value="2026-11-11" onChange={change} readOnly />)
    expect(visible).toHaveAttribute('readonly')
    expect(document.querySelector('.calendar-date-field__picker')).not.toBeInTheDocument()
  })

  it('keeps server validation associated with the field without hiding format guidance', () => {
    render(<ControlledDate error="The selected date is unavailable." />)
    expect(screen.getByLabelText('Session date')).toHaveAccessibleDescription('Date format: dd/MM/yyyy. The selected date is unavailable.')
    expect(screen.getByRole('alert')).toHaveTextContent('The selected date is unavailable.')
  })
})
