import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { Brand } from './Brand'

// NX-18 D3 (WCAG 2.5.3 Label in Name): the words a visitor sees must be part of the accessible name.
const visibleWords = (element: Element) => (element.textContent ?? '').replace(/\s+/g, ' ').trim().toLowerCase()

describe('Brand link accessible name', () => {
  it.each([false, true])('contains the visible words (compact=%s)', (compact) => {
    render(<MemoryRouter><Brand compact={compact} /></MemoryRouter>)
    const link = screen.getByRole('link', { name: 'Sportify Sports center home' })
    const visible = visibleWords(link.querySelector('.brand__text')!)
    expect(visible.length).toBeGreaterThan(0)
    expect(link.getAttribute('aria-label')!.toLowerCase()).toContain(visible)
  })
})
