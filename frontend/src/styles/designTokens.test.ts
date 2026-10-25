import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/*
 * UI-00 design system contract. Guards the shared tokens frozen in W0 so screen tasks build on one
 * palette: the approved Home brand values live in tokens.css, Home reads them instead of copies,
 * and Home reads them instead of copies. The raw color freeze is in rawColors.test.ts; rendered values in a real
 * browser are checked by scripts/ui00-visual-check.py.
 */

const STYLES_DIR = 'src/styles'
const tokens = readFileSync(`${STYLES_DIR}/tokens.css`, 'utf8')
const home = readFileSync(`${STYLES_DIR}/home.css`, 'utf8')

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

/** Declared value of a Home variable in the light .home-page block. */
function homeVariable(name: string): string {
  const lightBlock = home.slice(home.indexOf('.home-page {'), home.indexOf("[data-theme='dark'] .home-page"))
  const match = lightBlock.match(new RegExp(`${name}:\\s*([^;]+);`))
  if (!match) {
    throw new Error(`Missing Home variable ${name}`)
  }
  return match[1].trim()
}

describe('UI-00 design system contract', () => {
  it('defines the Home brand surfaces as shared tokens with the approved values', () => {
    expect(token('--color-hero', 'light')).toBe('#0b1736')
    expect(token('--color-hero', 'dark')).toBe('#060b18')
    expect(token('--color-hero-deep', 'light')).toBe('#060d20')
    expect(token('--color-footer', 'light')).toBe('#070f24')
    expect(token('--color-footer', 'dark')).toBe('#050913')
    expect(token('--color-on-hero', 'light')).toBe('#ffffff')
    expect(token('--color-on-hero-muted', 'light')).toBe('#e3e9f8')
    expect(token('--color-hero-highlight', 'light')).toBe('#ffc83d')
    expect(tokens).toMatch(/--font-display:\s*'Be Vietnam Pro'/)
  })

  it('keeps text on hero and footer surfaces at WCAG 2.2 AA in both modes', () => {
    for (const mode of ['light', 'dark'] as const) {
      for (const surface of ['--color-hero', '--color-footer', '--color-hero-deep']) {
        expect(contrast(token('--color-on-hero', mode), token(surface, mode))).toBeGreaterThanOrEqual(4.5)
        expect(contrast(token('--color-on-hero-muted', mode), token(surface, mode))).toBeGreaterThanOrEqual(4.5)
        expect(contrast(token('--color-hero-highlight', mode), token(surface, mode))).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  it('makes Home read its brand colors and display font from the shared tokens', () => {
    expect(homeVariable('--h-primary')).toBe('var(--color-primary)')
    expect(homeVariable('--h-primary-hover')).toBe('var(--color-primary-hover)')
    expect(homeVariable('--h-primary-text')).toBe('var(--color-primary)')
    expect(homeVariable('--h-accent')).toBe('var(--color-accent)')
    expect(homeVariable('--h-on-accent')).toBe('var(--color-on-accent)')
    expect(homeVariable('--h-icon-bg')).toBe('var(--color-primary-soft)')
    expect(homeVariable('--h-hero')).toBe('var(--color-hero)')
    expect(homeVariable('--h-hero-accent')).toBe('var(--color-hero-highlight)')
    expect(homeVariable('--h-hero-muted')).toBe('var(--color-on-hero-muted)')
    expect(homeVariable('--h-footer')).toBe('var(--color-footer)')
    expect(homeVariable('--h-deep')).toBe('var(--color-hero-deep)')
    expect(homeVariable('--h-cta-from')).toBe('var(--color-navy)')
    expect(homeVariable('--h-cta-to')).toBe('var(--color-primary)')
    expect(homeVariable('--h-display')).toBe('var(--font-display)')
  })
})
