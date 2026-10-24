import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { colorViolations, cssColors, scriptColors, type ColorBaseline } from './rawColors'

/*
 * UI-00 raw color freeze. Every color literal in the frontend (all stylesheets at any depth, component scripts,
 * public scripts and index.html) must match the reviewed baseline in rawColorBaseline.json. A new color, a changed
 * value, a color moved to another property or another file fails. Colors may be removed freely.
 *
 * After an approved token change, regenerate the baseline (PowerShell):
 *   $env:UPDATE_COLOR_BASELINE='1'; npx vitest run src/styles/rawColors.test.ts; Remove-Item Env:UPDATE_COLOR_BASELINE
 * The baseline diff in the pull request is what the UI-00 owner reviews.
 */

const BASELINE_FILE = 'src/styles/rawColorBaseline.json'

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? walk(path) : [path]
  })
}

/** Files in scope, as forward-slash paths relative to the frontend root. */
function scannedFiles(): string[] {
  const isTest = (file: string) => /\.test\.tsx?$/.test(file) || file.startsWith('src/test/') || file === 'src/styles/rawColors.ts'
  return [...walk('src'), ...walk('public'), 'index.html']
    .map((file) => file.replaceAll('\\', '/'))
    .filter((file) => /\.(css|tsx?|jsx?|mjs|html)$/.test(file) && !isTest(file))
    .sort()
}

function scanProject(): ColorBaseline {
  const result: ColorBaseline = {}
  for (const file of scannedFiles()) {
    const text = readFileSync(file, 'utf8')
    const counts = file.endsWith('.css') ? cssColors(text) : scriptColors(text)
    if (Object.keys(counts).length) {
      result[file] = Object.fromEntries(Object.entries(counts).sort(([a], [b]) => a.localeCompare(b)))
    }
  }
  return result
}

describe('UI-00 raw color scanner', () => {
  it('finds hex, functional and named colors in declarations, keyed by property', () => {
    const counts = cssColors(`
      /* #123456 in a comment is ignored */
      .a { color: #FFF; background: rgba(16, 46, 80, 0.45); border-color: hsl(210 40% 50%); }
      .b:hover:not(:disabled) { outline: 2px solid White; box-shadow: 0 0 0 1px oklch(70% 0.1 250 / 50%); }
      .c { background: url('#fff.svg') no-repeat, color-mix(in srgb, var(--color-red) 80%, transparent); fill: currentColor; }
      .d { --h-bg: #ffffff; --h-overlay: 11, 23, 54; color: color(display-p3 1 0 0); }
      @media (min-width: 768px) { .e { color: lab(50% 40 59.5); } }
    `)
    expect(counts).toEqual({
      'color|#fff': 1,
      'background|rgba(16,46,80,0.45)': 1,
      'border-color|hsl(21040%50%)': 1,
      'outline|white': 1,
      'box-shadow|oklch(70%0.1250/50%)': 1,
      '--h-bg|#ffffff': 1,
      'color|color(display-p3100)': 1,
      'color|lab(50%4059.5)': 1,
    })
  })

  it('counts a function with a nested variable as one color and keeps nested stylesheet blocks', () => {
    expect(cssColors('.x { box-shadow: 0 22px 48px rgba(var(--h-overlay), 0.22); } @supports (color: red) { .y { color: navy; } }')).toEqual({
      'box-shadow|rgba(var(--h-overlay),0.22)': 1,
      'color|navy': 1,
    })
  })

  it('finds hex and functional colors in script strings and HTML attributes, not anchors or plain words', () => {
    expect(scriptColors(`const a = '#0d1724'; const b = "rgba(0, 0, 0, .5)"; link('#main-content'); const word = 'red'`)).toEqual({ '#0d1724': 1, 'rgba(0,0,0,.5)': 1 })
    expect(scriptColors('<meta name="theme-color" content="#F5F8FC" /><div style="color: hsl(0 0% 0%)"></div>')).toEqual({ '#f5f8fc': 1, 'hsl(00%0%)': 1 })
  })

  it('flags a changed value, a swapped property, a higher count and a new file, and allows removals', () => {
    const baseline: ColorBaseline = { 'src/styles/a.css': { 'color|#fff': 2, '--h-bg|#000': 1 } }
    expect(colorViolations({ 'src/styles/a.css': { 'color|#fff': 1 } }, baseline)).toEqual([])
    expect(colorViolations({ 'src/styles/a.css': { 'color|#fff': 2, '--h-bg|#111': 1 } }, baseline)).toEqual(['src/styles/a.css: --h-bg|#111 (1 found, 0 allowed)'])
    expect(colorViolations({ 'src/styles/a.css': { 'color|#000': 1, '--h-bg|#fff': 1 } }, baseline)).toHaveLength(2)
    expect(colorViolations({ 'src/styles/a.css': { 'color|#fff': 3 } }, baseline)).toEqual(['src/styles/a.css: color|#fff (3 found, 2 allowed)'])
    expect(colorViolations({ 'src/styles/nested/b.css': { 'color|#fff': 1 } }, baseline)).toEqual(['src/styles/nested/b.css: color|#fff (1 found, 0 allowed)'])
  })
})

describe('UI-00 raw color freeze', () => {
  it('scans nested folders, component scripts, public scripts and index.html', () => {
    const files = scannedFiles()
    expect(files).toEqual(expect.arrayContaining(['src/styles/home.css', 'src/index.css', 'public/appearance.js', 'index.html']))
    expect(files.some((file) => file.startsWith('src/components/') && file.endsWith('.tsx'))).toBe(true)
    expect(files.some((file) => file.includes('.test.'))).toBe(false)
  })

  it('has no color outside the reviewed baseline', () => {
    const actual = scanProject()
    if (process.env.UPDATE_COLOR_BASELINE === '1') {
      writeFileSync(BASELINE_FILE, `${JSON.stringify(actual, null, 2)}\n`)
    }
    const baseline = JSON.parse(readFileSync(BASELINE_FILE, 'utf8')) as ColorBaseline
    expect(colorViolations(actual, baseline)).toEqual([])
  })
})
