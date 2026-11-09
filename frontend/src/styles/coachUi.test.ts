import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const stylesheet = readFileSync('src/styles/coachUi.css', 'utf8')

describe('coach presentation foundation', () => {
  it('keeps every selector inside the coach root', () => {
    const selectorBlocks = stylesheet
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/@import[^;]+;/g, '')
      .match(/[^{}]+(?=\{)/g) ?? []
    const selectors = selectorBlocks.flatMap(block => block.split(',').map(selector => selector.trim()))

    expect(selectors.length).toBeGreaterThan(0)
    expect(selectors.every(selector => selector.startsWith('.coach-ui'))).toBe(true)
  })

  it('uses shared theme tokens and includes responsive and keyboard patterns', () => {
    expect(stylesheet).toContain('--coach-surface: var(--color-surface)')
    expect(stylesheet).toContain('--coach-blue: var(--color-primary)')
    expect(stylesheet).toContain('font-family: var(--coach-display)')
    expect(stylesheet).toContain('border-radius: var(--coach-radius)')
    expect(stylesheet).toContain('repeat(auto-fit')
    expect(stylesheet).toContain('.coach-ui__focus:focus-visible')
    expect(stylesheet).not.toMatch(/#[\da-f]{3,8}\b/i)
  })
})
