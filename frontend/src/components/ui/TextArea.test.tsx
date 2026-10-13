import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { TextArea } from './TextArea'

describe('FE-12 TextArea', () => {
  it('TC-FE12-060 links the hint only while it is rendered and keeps the caller ids', async () => {
    const { rerender } = render(<TextArea label="Note" hint="Optional" aria-describedby="extra" />)
    const field = screen.getByRole('textbox', { name: 'Note' })
    const hintId = screen.getByText('Optional').id
    expect(field).toHaveAttribute('aria-describedby', `${hintId} extra`)
    expect(field).not.toHaveAttribute('aria-invalid')

    rerender(<TextArea label="Note" hint="Optional" error="Note is too long" aria-describedby="extra" />)
    expect(screen.queryByText('Optional')).not.toBeInTheDocument()
    const errorId = screen.getByRole('alert').id
    expect(field).toHaveAttribute('aria-describedby', `${errorId} extra`)
    expect(field).toHaveAttribute('aria-invalid', 'true')

    await userEvent.setup().type(field, 'abc')
    expect(field).toHaveValue('abc')
  })

  it('TC-FE12-061 caller props cannot override the computed aria props', () => {
    render(<TextArea label="Reason" required error="Reason is required" aria-invalid={false} rows={5} />)
    const field = screen.getByRole('textbox', { name: /Reason/ })
    expect(field).toHaveAttribute('aria-invalid', 'true')
    expect(field).toHaveAttribute('rows', '5')
    expect(field).toBeRequired()
  })
})
