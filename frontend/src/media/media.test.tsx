import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { buildCredits } from '../pages/public/creditsContent'
import { Photo } from './Photo'
import { sportPhotoUrl } from './sportPhotos'
import { unsplashSrcSet, unsplashUrl } from './unsplash'

/** Every file under a folder, as paths relative to the frontend root. */
function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? walk(path) : [path]
  })
}

const sourceFiles = walk('src').filter((file) => /\.(ts|tsx|css)$/.test(file) && !/\.test\.tsx?$/.test(file) && !file.includes(`${join('src', 'test')}`))
const sources = sourceFiles.map((file) => ({ file, text: readFileSync(file, 'utf8') }))

describe('UI-03 shared media helpers', () => {
  it('builds a responsive srcset up to the requested width for Unsplash photos only', () => {
    const url = unsplashUrl('photo-1530549387789-4c1017266635', 1000)
    expect(unsplashSrcSet(url)).toBe([480, 720, 1000].map((width) => `${unsplashUrl('photo-1530549387789-4c1017266635', width)} ${width}w`).join(', '))
    expect(unsplashSrcSet(unsplashUrl('photo-1530549387789-4c1017266635', 2200))).toContain('1600w, ')
    expect(unsplashSrcSet('/assets/swimming.jpg')).toBeUndefined()
    expect(unsplashSrcSet('https://example.com/photo-1530549387789-4c1017266635?w=1000')).toBeUndefined()
  })

  it('maps sport names to one shared photo, with soccer as football and unknown sports as none', () => {
    expect(sportPhotoUrl(' Swimming ', 720)).toBe(unsplashUrl('photo-1530549387789-4c1017266635', 720))
    expect(sportPhotoUrl('Soccer', 720)).toBe(sportPhotoUrl('football', 720))
    expect(sportPhotoUrl('Yoga', 720)).toBeNull()
  })

  it('renders a remote photo with srcset and sizes, then falls back once to the bundled image without srcset', () => {
    render(<Photo src={unsplashUrl('photo-1554068865-24cecd4e34b8', 720)} fallback="/local/tennis.jpg" alt="Tennis" sizes="50vw" loading="lazy" />)
    const image = screen.getByRole('img', { name: 'Tennis' })
    expect(image).toHaveAttribute('srcset', expect.stringContaining('480w'))
    expect(image).toHaveAttribute('sizes', '50vw')
    expect(image).toHaveAttribute('decoding', 'async')
    expect(image).toHaveAttribute('loading', 'lazy')
    fireEvent.error(image)
    expect(image).toHaveAttribute('src', '/local/tennis.jpg')
    expect(image).not.toHaveAttribute('srcset')
    expect(image).not.toHaveAttribute('sizes')
  })

  it('requests a new src again after the previous one failed on the same element (rerender)', () => {
    const first = unsplashUrl('photo-1554068865-24cecd4e34b8', 720)
    const second = unsplashUrl('photo-1530549387789-4c1017266635', 720)
    const { rerender } = render(<Photo src={first} fallback="/local/tennis.jpg" alt="Sport" sizes="50vw" />)
    const image = screen.getByRole('img', { name: 'Sport' })
    fireEvent.error(image)
    expect(image).toHaveAttribute('src', '/local/tennis.jpg')

    rerender(<Photo src={second} fallback="/local/swimming.jpg" alt="Sport" sizes="50vw" />)
    expect(screen.getByRole('img', { name: 'Sport' })).toBe(image)
    expect(image).toHaveAttribute('src', second)
    expect(image).toHaveAttribute('srcset', unsplashSrcSet(second))
    expect(image).toHaveAttribute('sizes', '50vw')

    fireEvent.error(image)
    expect(image).toHaveAttribute('src', '/local/swimming.jpg')
    rerender(<Photo src={first} fallback="/local/tennis.jpg" alt="Sport" sizes="50vw" />)
    expect(image).toHaveAttribute('src', first)
  })

  it('does not loop when the fallback fails too, and forwards onError to the caller', () => {
    const onError = vi.fn()
    render(<Photo src={unsplashUrl('photo-1554068865-24cecd4e34b8', 720)} fallback="/local/missing.jpg" alt="Tennis" onError={onError} />)
    const image = screen.getByRole('img', { name: 'Tennis' })
    fireEvent.error(image)
    fireEvent.error(image)
    fireEvent.error(image)
    expect(image).toHaveAttribute('src', '/local/missing.jpg')
    expect(image).not.toHaveAttribute('srcset')
    expect(onError).toHaveBeenCalledTimes(3)
  })

  it('renders a bundled image without srcset and as decorative when no alt is given', () => {
    const { container } = render(<Photo src="/local/hero.jpg" fallback="/local/hero.jpg" />)
    const image = container.querySelector('img') as HTMLImageElement
    expect(image).toHaveAttribute('alt', '')
    expect(image).not.toHaveAttribute('srcset')
  })
})

describe('UI-03 browser check runners', () => {
  it('are committed, not git-ignored, with pinned Python packages and a Node script that needs no npm package', () => {
    for (const file of ['scripts/ui03-media-check.py', 'scripts/ui03-real-media-check.mjs']) expect(existsSync(file)).toBe(true)
    const requirements = readFileSync('scripts/requirements-visual-check.txt', 'utf8').split(/\r?\n/).filter((line) => line.trim() && !line.startsWith('#'))
    expect(requirements.map((line) => line.split('==')[0])).toEqual(['playwright', 'pillow'])
    for (const line of requirements) expect(line).toMatch(/^[\w-]+==\d+\.\d+\.\d+$/)
    expect(readFileSync('../.gitignore', 'utf8')).toMatch(/^!frontend\/scripts\/\*\.py$/m)
    const imports = [...readFileSync('scripts/ui03-real-media-check.mjs', 'utf8').matchAll(/^import .* from '([^']+)'/gm)].map((match) => match[1])
    expect(imports.length).toBeGreaterThan(0)
    expect(imports.filter((name) => !name.startsWith('node:'))).toEqual([])
  })

  it('document a merge-base build served by vite preview, with the evidence they produce', () => {
    const python = readFileSync('scripts/ui03-media-check.py', 'utf8')
    const node = readFileSync('scripts/ui03-real-media-check.mjs', 'utf8')
    const testCases = readFileSync('docs/UI-03-test-cases.md', 'utf8')
    for (const text of [python, testCases]) {
      expect(text).toContain('scripts/requirements-visual-check.txt')
      expect(text).toContain('git merge-base HEAD origin/main')
    }
    for (const text of [python, node, testCases]) expect(text).toContain('vite preview')
    expect(testCases).toContain('node scripts/ui03-real-media-check.mjs')
    expect(existsSync('docs/screenshots/ui-03/report.json')).toBe(true)
    expect(existsSync('docs/screenshots/ui-03/real/report.json')).toBe(true)
  })
})

describe('UI-03 media rules', () => {
  it('keeps no unused files in src/assets', () => {
    const unused = walk('src/assets').filter((asset) => {
      const name = asset.split(/[\\/]/).pop() as string
      return !sources.some(({ text }) => text.includes(name))
    })
    expect(unused).toEqual([])
  })

  it('builds Unsplash URLs only through src/media/unsplash.ts', () => {
    const offenders = sources.filter(({ file, text }) => text.includes('images.unsplash.com') && relative('src', file).replaceAll('\\', '/') !== 'media/unsplash.ts')
    expect(offenders.map(({ file }) => file)).toEqual([])
  })

  it('credits every Unsplash photo used in the source code', () => {
    const credited = buildCredits().flatMap((group) => group.items.map((item) => item.link)).join(' ')
    const used = new Set(sources.flatMap(({ text }) => text.match(/photo-\d{10,}-[0-9a-f]+/g) ?? []))
    expect(used.size).toBeGreaterThan(0)
    expect([...used].filter((id) => !credited.includes(id))).toEqual([])
  })

  it('gives every img element in the app an alt attribute', () => {
    const missing = sources
      .filter(({ file }) => file.endsWith('.tsx'))
      .flatMap(({ file, text }) => (text.match(/<img\b[^>]*>/g) ?? []).filter((tag) => !/\balt=/.test(tag)).map((tag) => `${file}: ${tag}`))
    expect(missing).toEqual([])
  })
})
