import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { HomeSelect } from './HomeSelect'

describe('Home Sports filter', () => {
  it('retains keyboard selection, reset, selected announcement, Escape and focus', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<HomeSelect layout="grid" label="Sport" placeholder="All sports" value="2" options={[{ value: '1', label: 'Football' }, { value: '2', label: 'Tennis' }]} onChange={onChange} />)
    const trigger = screen.getByRole('button', { name: 'Sport Tennis' })
    await user.click(trigger)
    expect(screen.getByRole('option', { name: 'Tennis' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('listbox')).toHaveClass('home-select__list--grid')
    await user.keyboard('{Home}{Enter}')
    expect(onChange).toHaveBeenLastCalledWith('')
    expect(trigger).toHaveFocus()
    await user.keyboard('{ArrowDown}{Home}{ArrowDown}{Enter}')
    expect(onChange).toHaveBeenLastCalledWith('1')
    await user.click(trigger)
    await user.keyboard('{Escape}')
    expect(trigger).toHaveFocus()
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  it('keeps future options reachable and closes when focus leaves', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<><HomeSelect layout="grid" label="Sport" placeholder="All sports" value="" options={Array.from({ length: 30 }, (_, i) => ({ value: String(i + 1), label: `Sport ${i + 1}` }))} onChange={onChange} /><button>Next filter</button></>)
    await user.click(screen.getByRole('button', { name: 'Sport All sports' }))
    expect(screen.getAllByRole('option')).toHaveLength(31)
    await user.keyboard('{End}{Enter}')
    expect(onChange).toHaveBeenCalledWith('30')
    await user.click(screen.getByRole('button', { name: 'Sport All sports' }))
    await user.tab()
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next filter' })).toHaveFocus()
  })
})
