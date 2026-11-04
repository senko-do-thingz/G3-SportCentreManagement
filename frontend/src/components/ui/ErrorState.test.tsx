import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ApiError } from '../../api/errors'
import { ErrorState } from './ErrorState'

it('distinguishes a conflict and leaves retry under user control', async () => {
  const retry = vi.fn()
  render(<ErrorState error={new ApiError(409, 'This record changed.')} onRetry={retry} />)
  expect(screen.getByRole('alert')).toHaveClass('state--conflict')
  expect(screen.getByRole('heading')).toHaveTextContent('Conflict:')
  expect(screen.getByText('This record changed.')).toBeInTheDocument()
  expect(retry).not.toHaveBeenCalled()
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
  expect(retry).toHaveBeenCalledTimes(1)
})

it('keeps ordinary server errors distinct from conflicts', () => {
  render(<ErrorState error={new ApiError(503, 'Service unavailable')} />)
  expect(screen.getByRole('alert')).not.toHaveClass('state--conflict')
  expect(screen.getByText('Service unavailable')).toBeInTheDocument()
})
