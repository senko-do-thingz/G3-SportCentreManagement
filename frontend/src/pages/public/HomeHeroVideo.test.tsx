import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HomeHeroVideo } from './HomeHeroVideo'

const SOURCES = ['a', 'b', 'c'].map((name) => [`https://cdn.test/${name}-1080.mp4`, `https://cdn.test/${name}-720.mp4`])
const srcs = (video: HTMLVideoElement) => [...video.querySelectorAll('source')].map((source) => source.getAttribute('src'))
const failLast = (video: HTMLVideoElement) => fireEvent.error(video.querySelectorAll('source')[1])

function media({ wide = true, reduce = false } = {}) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: query.includes('min-width') ? wide : reduce, media: query, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
}
const videoOf = (container: HTMLElement) => container.querySelector('video') as HTMLVideoElement

describe('HomeHeroVideo', () => {
  beforeEach(() => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => Promise.resolve())
  })
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

  it('plays muted clips in order on wide screens and fades in once playing', () => {
    media()
    const { container } = render(<HomeHeroVideo sources={SOURCES} />)
    const video = videoOf(container)
    expect(srcs(video)).toEqual(SOURCES[0])
    expect(video.muted).toBe(true)
    expect(video).toHaveAttribute('aria-hidden', 'true')
    expect(video).not.toHaveClass('home-hero__video--ready')
    fireEvent.playing(video)
    expect(video).toHaveClass('home-hero__video--ready')
    fireEvent.ended(video)
    expect(srcs(videoOf(container))).toEqual(SOURCES[1])
    fireEvent.ended(videoOf(container))
    fireEvent.ended(videoOf(container))
    expect(srcs(videoOf(container))).toEqual(SOURCES[0])
  })

  it('pauses and resumes from the toggle button', async () => {
    media()
    const user = userEvent.setup()
    render(<HomeHeroVideo sources={SOURCES} />)
    await user.click(screen.getByRole('button', { name: 'Pause background video' }))
    expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Play background video' }))
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Pause background video' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('keeps a clip while its fallback file can still load, skips it when all files fail and removes the video when none can load', () => {
    media()
    const { container } = render(<HomeHeroVideo sources={SOURCES} />)
    fireEvent.error(videoOf(container).querySelectorAll('source')[0])
    expect(srcs(videoOf(container))).toEqual(SOURCES[0])
    failLast(videoOf(container))
    expect(srcs(videoOf(container))).toEqual(SOURCES[1])
    failLast(videoOf(container))
    expect(videoOf(container)).toHaveAttribute('loop')
    failLast(videoOf(container))
    expect(container.querySelector('video')).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it.each([['narrow screens', { wide: false }], ['reduced motion', { reduce: true }]])('keeps only the photo on %s', (_, options) => {
    media(options)
    const { container } = render(<HomeHeroVideo sources={SOURCES} />)
    expect(container).toBeEmptyDOMElement()
  })
})
