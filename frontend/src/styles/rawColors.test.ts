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
  it('finds hex, functional and named colors in declarations, keyed by rule and property', () => {
    const counts = cssColors(`
      /* #123456 in a comment is ignored */
      .a { color: #FFF; background: rgba(16, 46, 80, 0.45); border-color: hsl(210 40% 50%); }
      .b:hover:not(:disabled) { outline: 2px solid White; box-shadow: 0 0 0 1px oklch(70% 0.1 250 / 50%); }
      .c { background: url('#fff.svg') no-repeat, color-mix(in srgb, var(--color-red) 80%, transparent); fill: currentColor; }
      .d { --h-bg: #ffffff; --h-overlay: 11, 23, 54; color: color(display-p3 1 0 0) }
      @media (min-width: 768px) { .e,
        .f   { color: lab(50% 40 59.5); } }
    `)
    expect(counts).toEqual({
      '.a { color|#fff': 1,
      '.a { background|rgba(16,46,80,0.45)': 1,
      '.a { border-color|hsl(21040%50%)': 1,
      '.b:hover:not(:disabled) { outline|white': 1,
      '.b:hover:not(:disabled) { box-shadow|oklch(70%0.1250/50%)': 1,
      '.d { --h-bg|#ffffff': 1,
      '.d { color|color(display-p3100)': 1,
      '@media (min-width: 768px) { .e, .f { color|lab(50%4059.5)': 1,
    })
  })

  it('counts a function with a nested variable as one color and keeps nested stylesheet blocks', () => {
    expect(cssColors('.x { box-shadow: 0 22px 48px rgba(var(--h-overlay), 0.22); } @supports (color: red) { .y { color: navy; } }')).toEqual({
      '.x { box-shadow|rgba(var(--h-overlay),0.22)': 1,
      '@supports (color: red) { .y { color|navy': 1,
    })
  })

  it('finds hex and functional colors in script strings and HTML attributes, not plain words', () => {
    expect(scriptColors(`const a = '#0d1724'; const b = "rgba(0, 0, 0, .5)"; link('#main-content'); const word = 'red'`)).toEqual({ '#0d1724': 1, 'rgba(0,0,0,.5)': 1 })
    expect(scriptColors('<meta name="theme-color" content="#F5F8FC" /><div style="color: hsl(0 0% 0%)"></div>')).toEqual({ '#f5f8fc': 1, 'hsl(00%0%)': 1 })
  })

  it('finds named colors in inline styles, style objects, SVG paint attributes and style assignments', () => {
    expect(scriptColors(`
      <div style={{ color: 'white', backgroundColor: "Navy", 'border-color': \`teal\`, padding: '4px' }} />
      <svg><circle fill="red" stroke='black' /></svg>
      <p style="color: tomato; margin: 0">x</p>
      element.style.color = 'gold'
      element.style.setProperty('--h-accent', 'orange')
      const css = \`.x { outline: 1px solid crimson; }\`
    `)).toEqual({ white: 1, navy: 1, teal: 1, red: 1, black: 1, tomato: 1, gold: 1, orange: 1, crimson: 1 })
  })

  it('keeps named color words that are not CSS color values out of the scan', () => {
    expect(scriptColors(`
      const team = 'red'; const options = { tone: 'green', label: 'Blue', title: 'Gold card' }
      <Badge variant="orange">Silver</Badge>
      const hint = 'Pick a color: white, black or red'
    `)).toEqual({})
  })

  it('ignores hex-like link targets and selectors, and still finds the same text as a color', () => {
    expect(scriptColors(`
      <a href="#abc">Skip</a> <Link to='#fed' /> <a href={'#add'} /> <label htmlFor="#bad" /> <div id="#cab" />
      document.querySelector('#bee'); element.closest("#dad"); navigate('#ace')
      const page = '/classes#face'; const search = '?tab=#beef'
    `)).toEqual({})
    expect(scriptColors(`const color = '#abc'; <meta name="theme-color" content="#fed" />`)).toEqual({ '#abc': 1, '#fed': 1 })
  })

  it('flags a changed value, a swapped property, a higher count and a new file, and allows removals', () => {
    const baseline: ColorBaseline = { 'src/styles/a.css': { '.a { color|#fff': 2, '.a { --h-bg|#000': 1 } }
    expect(colorViolations({ 'src/styles/a.css': { '.a { color|#fff': 1 } }, baseline)).toEqual([])
    expect(colorViolations({ 'src/styles/a.css': { '.a { color|#fff': 2, '.a { --h-bg|#111': 1 } }, baseline)).toEqual(['src/styles/a.css: .a { --h-bg|#111 (1 found, 0 allowed)'])
    expect(colorViolations({ 'src/styles/a.css': { '.a { color|#000': 1, '.a { --h-bg|#fff': 1 } }, baseline)).toHaveLength(2)
    expect(colorViolations({ 'src/styles/a.css': { '.a { color|#fff': 3 } }, baseline)).toEqual(['src/styles/a.css: .a { color|#fff (3 found, 2 allowed)'])
    expect(colorViolations({ 'src/styles/nested/b.css': { '.a { color|#fff': 1 } }, baseline)).toEqual(['src/styles/nested/b.css: .a { color|#fff (1 found, 0 allowed)'])
  })

  it('flags colors swapped between two selectors even when every property and value count stays the same', () => {
    const baseline: ColorBaseline = { 'src/styles/a.css': cssColors('.hero { color: #fff; } .footer { color: #000; }') }
    const swapped = { 'src/styles/a.css': cssColors('.hero { color: #000; } .footer { color: #fff; }') }
    expect(colorViolations(swapped, baseline)).toEqual([
      'src/styles/a.css: .footer { color|#fff (1 found, 0 allowed)',
      'src/styles/a.css: .hero { color|#000 (1 found, 0 allowed)',
    ])
    const movedIntoMedia = { 'src/styles/a.css': cssColors('.footer { color: #000; } @media (max-width: 640px) { .hero { color: #fff; } }') }
    expect(colorViolations(movedIntoMedia, baseline)).toEqual(['src/styles/a.css: @media (max-width: 640px) { .hero { color|#fff (1 found, 0 allowed)'])
    const reformatted = { 'src/styles/a.css': cssColors('.hero{color:#FFF}\n.footer {\n  color: #000;\n}') }
    expect(colorViolations(reformatted, baseline)).toEqual([])
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
