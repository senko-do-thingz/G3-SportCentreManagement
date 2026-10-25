import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/*
 * UI-00 browser evidence must be reproducible from the PR revision: the Playwright runner and its pinned Python
 * packages are committed, git does not ignore them, and the documented steps serve the SPA routes it opens.
 * The runner itself is run locally (see docs/UI-00-test-cases.md); this test only guards that it stays runnable.
 */

const RUNNER = 'scripts/ui00-visual-check.py'
const REQUIREMENTS = 'scripts/requirements-visual-check.txt'

describe('UI-00 browser check runner', () => {
  it('is committed with pinned Python packages that git does not ignore', () => {
    expect(existsSync(RUNNER)).toBe(true)
    const requirements = readFileSync(REQUIREMENTS, 'utf8').split('\n').filter((line) => line.trim() && !line.startsWith('#'))
    expect(requirements.map((line) => line.split('==')[0])).toEqual(['playwright', 'pillow'])
    for (const line of requirements) expect(line).toMatch(/^[\w-]+==\d+\.\d+\.\d+$/)
    expect(readFileSync('../.gitignore', 'utf8')).toMatch(/^!frontend\/scripts\/\*\.py$/m)
  })

  it('documents a build of the merge base and a server that answers /login and /register', () => {
    const runner = readFileSync(RUNNER, 'utf8')
    const testCases = readFileSync('docs/UI-00-test-cases.md', 'utf8')
    for (const text of [runner, testCases]) {
      expect(text).toContain(REQUIREMENTS)
      expect(text).toContain('git merge-base HEAD origin/main')
      expect(text).toContain('vite preview')
      expect(text).not.toContain('http.server')
    }
    expect(runner).toMatch(/"\/login"[\s\S]*"\/register"/)
  })
})
