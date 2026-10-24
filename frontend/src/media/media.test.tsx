import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
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

  it('renders a bundled image without srcset and as decorative when no alt is given', () => {
    const { container } = render(<Photo src="/local/hero.jpg" fallback="/local/hero.jpg" />)
    const image = container.querySelector('img') as HTMLImageElement
    expect(image).toHaveAttribute('alt', '')
    expect(image).not.toHaveAttribute('srcset')
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
