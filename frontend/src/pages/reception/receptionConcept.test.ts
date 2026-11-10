import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/*
 * RUI-00 boundary guard: the Receptionist Concept A stylesheet must only style Receptionist screens and keep the
 * square Home-style corners. Comments are stripped before checking.
 */
const css = readFileSync('src/styles/receptionConcept.css', 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')

function selectors(text: string): string[] {
  const found: string[] = []
  const rule = /([^{}@]+)\{[^{}]*\}/g
  for (const match of text.matchAll(rule)) {
    found.push(...match[1].split(/,(?![^(]*\))/).map((part) => part.trim()).filter(Boolean))
  }
  return found
}

describe('receptionConcept.css', () => {
  it('scopes every selector to a Receptionist-only hook', () => {
    const hook = /(\.rcp\b|\.rcp-|\.page\.rcp)/
    const unscoped = selectors(css).filter((selector) => !hook.test(selector))
    expect(unscoped).toEqual([])
  })

  it('never uses rounded radius tokens', () => {
    expect(css).not.toMatch(/border-radius:\s*var\(--radius-/)
  })

  it('is ASCII only', () => {
    expect([...css].every((char) => char.charCodeAt(0) < 128)).toBe(true)
  })
})
