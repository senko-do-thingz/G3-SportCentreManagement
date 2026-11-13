import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { installFakeFetch, json } from '../../test/fakeBackend'
import { MemberAssistantPage } from './MemberAssistantPage'
import { TrainingData } from './TrainingData'
import { TrainingPlanDetailPage } from './TrainingPages'

afterEach(() => vi.unstubAllGlobals())

describe('Training request lifecycle', () => {
  it('refreshes saved training data without navigating away', async () => {
    let latest = 'Initial attendance'
    const load = vi.fn(async () => latest)
    render(<TrainingData load={load} refreshLabel="Refresh attendance">{value => <p>{value}</p>}</TrainingData>)
    expect(await screen.findByText('Initial attendance')).toBeInTheDocument()
    latest = 'Attendance updated by reception'
    await userEvent.click(screen.getByRole('button', { name: 'Refresh attendance' }))
    expect(await screen.findByText('Attendance updated by reception')).toBeInTheDocument()
    expect(screen.queryByText('Initial attendance')).not.toBeInTheDocument()
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('allows retry after a refresh fails without leaving the training page', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce('Saved result')
      .mockRejectedValueOnce(new Error('Training service unavailable'))
      .mockResolvedValueOnce('Latest result')
    render(<TrainingData load={load} refreshLabel="Refresh results">{value => <p>{String(value)}</p>}</TrainingData>)
    await screen.findByText('Saved result')
    await userEvent.click(screen.getByRole('button', { name: 'Refresh results' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Training service unavailable')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('Latest result')).toBeInTheDocument()
  })

  it('hides the previous data immediately when the requested resource changes', async () => {
    const first = vi.fn(async () => 'First plan')
    let completeNext!: (value: string) => void
    const next = vi.fn(() => new Promise<string>(resolve => { completeNext = resolve }))
    const content = (value: string) => <p>{value}</p>
    const view = render(<TrainingData load={first}>{content}</TrainingData>)
    expect(await screen.findByText('First plan')).toBeInTheDocument()

    view.rerender(<TrainingData load={next}>{content}</TrainingData>)
    expect(screen.queryByText('First plan')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toBeInTheDocument()
    await act(async () => completeNext('Next plan'))
    expect(await screen.findByText('Next plan')).toBeInTheDocument()
  })

  it('aborts a slow previous request and ignores its late response', async () => {
    let previousSignal!: AbortSignal
    let completePrevious!: (value: string) => void
    const previous = (signal: AbortSignal) => {
      previousSignal = signal
      return new Promise<string>(resolve => { completePrevious = resolve })
    }
    const current = async () => 'Current plan'
    const content = (value: string) => <p>{value}</p>
    const view = render(<TrainingData load={previous}>{content}</TrainingData>)

    view.rerender(<TrainingData load={current}>{content}</TrainingData>)
    expect(previousSignal.aborted).toBe(true)
    expect(await screen.findByText('Current plan')).toBeInTheDocument()
    await act(async () => completePrevious('Obsolete plan'))
    expect(screen.queryByText('Obsolete plan')).not.toBeInTheDocument()
    expect(screen.getByText('Current plan')).toBeInTheDocument()
  })

  it('keeps a settled request when only the render callback changes', async () => {
    const load = vi.fn(async () => 'Plan')
    const view = render(<TrainingData load={load}>{value => <p>{value}</p>}</TrainingData>)
    expect(await screen.findByText('Plan')).toBeInTheDocument()
    view.rerender(<TrainingData load={load}>{value => <strong>{value}</strong>}</TrainingData>)
    expect(screen.getByText('Plan').tagName).toBe('STRONG')
    expect(load).toHaveBeenCalledTimes(1)
  })

  it('loads the new plan when a detail route changes id without unmounting', async () => {
    const { calls } = installFakeFetch(call => {
      const id = call.path.endsWith('/2') ? 2 : 1
      return json(200, {
        plan: { id, title: `Plan ${id}`, className: 'Swimming', coachName: 'An', description: null },
        steps: [],
        feedback: [],
      })
    })
    render(
      <MemoryRouter initialEntries={['/plans/1']}>
        <Link to="/plans/2">Next plan</Link>
        <Routes><Route path="/plans/:id" element={<TrainingPlanDetailPage />} /></Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByRole('heading', { name: 'Plan 1' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Next plan' }))
    expect(await screen.findByRole('heading', { name: 'Plan 2' })).toBeInTheDocument()
    await waitFor(() => expect(calls.map(call => call.path)).toEqual([
      '/member/training/plans/1', '/member/training/plans/2',
    ]))
  })
})

it('preserves the safe return path in the real center assistant', () => {
  render(
    <MemoryRouter initialEntries={['/member/assistant?returnTo=%2Fmember%2Ftraining%2Fplans%3Fview%3Dweek%23plan-list']}>
      <MemberAssistantPage />
    </MemoryRouter>,
  )
  expect(screen.getByRole('link', { name: 'Back to previous page' })).toHaveAttribute('href', '/member/training/plans?view=week#plan-list')
  expect(screen.getByRole('button', { name: 'Ask' })).toBeInTheDocument()
})
