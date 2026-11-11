import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const css = readFileSync('src/styles/memberCommerce.css', 'utf8')

function printBlock(source: string): string {
  const start = source.indexOf('@media print')
  if (start < 0) return ''
  let depth = 0
  for (let index = source.indexOf('{', start); index < source.length; index += 1) {
    if (source[index] === '{') depth += 1
    if (source[index] === '}') {
      depth -= 1
      if (depth === 0) return source.slice(start, index + 1)
    }
  }
  return source.slice(start)
}

describe('Member Commerce style isolation', () => {
  it('keeps print-only button hiding inside the Member Commerce scope', () => {
    const print = printBlock(css)
    expect(print).toContain('.member-commerce button')
    expect(print).toContain('.member-commerce .btn')
    expect(print).not.toMatch(/(^|,)\s*button\s*(,|\{)/m)
    expect(print).not.toMatch(/(^|,)\s*\.btn\s*(,|\{)/m)
  })

  it('does not declare global element or root selectors', () => {
    const withoutComments = css.replace(/\/\*[\s\S]*?\*\//g, '')
    expect(withoutComments).not.toMatch(/(^|})\s*(body|:root|button|table)\s*(,|\{)/m)
  })
})
