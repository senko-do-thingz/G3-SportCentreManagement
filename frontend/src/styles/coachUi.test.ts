import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const stylesheet = readFileSync('src/styles/coachUi.css', 'utf8')

describe('coach presentation foundation', () => {
  it('keeps every selector inside the coach root', () => {
    const selectorBlocks = stylesheet
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/@import[^;]+;/g, '')
      .replace(/@media[^{}]+\{/g, '')
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

  it('enforces flat 0px rectangular surfaces on Coach content matching Sportify Home DNA', () => {
    expect(stylesheet).toMatch(/--coach-radius:\s*0\s*;/)
    expect(stylesheet).toContain('.coach-ui .card')
    expect(stylesheet).toContain('.coach-ui .btn')
    expect(stylesheet).toContain('.coach-ui .input')
    expect(stylesheet).toContain('.coach-ui select')
    expect(stylesheet).toContain('.coach-ui textarea')
  })

  it('supports compound root selectors where coach-ui is on the page container', () => {
    expect(stylesheet).toContain('.coach-ui.coach-schedule-page')
    expect(stylesheet).toContain('.coach-ui.coach-roster-page')
    expect(stylesheet).toContain('.coach-ui.coach-questions-page')
    expect(stylesheet).toContain('.coach-ui.coach-progress-page')
    expect(stylesheet).toContain('.coach-ui.coach-plans-page')
    expect(stylesheet).toContain('.coach-ui.coach-results-page')
  })

  it('provides scoped metric icon styling for Coach summary metrics', () => {
    expect(stylesheet).toContain('.coach-ui.coach-schedule-page .coach-schedule-metric__icon')
    expect(stylesheet).toContain('.coach-ui.coach-roster-page .coach-schedule-metric__icon')
    expect(stylesheet).toContain('.coach-ui.coach-questions-page .coach-schedule-metric__icon')
  })
})
