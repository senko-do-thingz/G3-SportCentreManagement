import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HomeHeroVideo } from './HomeHeroVideo'

const SOURCES = ['a', 'b', 'c'].map((name) => [`https://cdn.test/${name}-1080.mp4`, `https://cdn.test/${name}-720.mp4`])
const srcs = (video: HTMLVideoElement) => [...video.querySelectorAll('source')].map((source) => source.getAttribute('src'))
const failLast = (video: HTMLVideoElement) => fireEvent.error(video.querySelectorAll('source')[1])

/** Screen and network: every min-width query matches when wide; connection null removes the Network Information API. */
function media({ wide = true, reduce = false, connection = { effectiveType: '4g' } as object | null } = {}) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: query.includes('min-width') ? wide : reduce, media: query, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
  vi.stubGlobal('navigator', connection === null ? { ...navigator } : { ...navigator, connection })
}
const FULL_HD_FIRST = SOURCES[0]
const HD_FIRST = [...SOURCES[0]].reverse()
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

  it('does not render toggle button when showToggle is false', () => {
    media()
    const { container } = render(<HomeHeroVideo sources={SOURCES} showToggle={false} />)
    expect(videoOf(container)).not.toBeNull()
    expect(screen.queryByRole('button', { name: /background video/i })).toBeNull()
    expect(container.querySelector('.home-hero__video-toggle')).toBeNull()
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

  it('tries the 720p file first below 1600 px wide even on a 4g connection', () => {
    media()
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: query === '(min-width: 768px)', media: query, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    const { container } = render(<HomeHeroVideo sources={SOURCES} />)
    expect(srcs(videoOf(container))).toEqual(HD_FIRST)
  })

  it.each([
    ['a 4g connection', { effectiveType: '4g' }, FULL_HD_FIRST],
    ['no Network Information API', null, HD_FIRST],
    ['a connection without effectiveType', {}, HD_FIRST],
    ['an unknown effectiveType', { effectiveType: 'unknown' }, HD_FIRST],
    ['a 3g connection', { effectiveType: '3g' }, HD_FIRST],
    ['a 2g connection', { effectiveType: '2g' }, HD_FIRST],
  ])('picks the first file on a large screen with %s', (_, connection, expected) => {
    media({ connection })
    const { container } = render(<HomeHeroVideo sources={SOURCES} />)
    expect(srcs(videoOf(container))).toEqual(expected)
  })

  it.each([['4g', { effectiveType: '4g', saveData: true }], ['unknown network', { saveData: true }]])('keeps only the photo when data saver is on (%s)', (_, connection) => {
    media({ connection })
    const { container } = render(<HomeHeroVideo sources={SOURCES} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('waits for the page load event before adding the video', () => {
    media()
    const state = vi.spyOn(document, 'readyState', 'get').mockReturnValue('interactive')
    const { container } = render(<HomeHeroVideo sources={SOURCES} />)
    expect(container.querySelector('video')).toBeNull()
    state.mockReturnValue('complete')
    act(() => { window.dispatchEvent(new Event('load')) })
    expect(srcs(videoOf(container))).toEqual(SOURCES[0])
  })

  it('still adds the video when the load event fired between the first render and the effect', () => {
    media()
    // First render sees "interactive"; by the time the effect runs the page is complete and load has already fired.
    vi.spyOn(document, 'readyState', 'get').mockReturnValueOnce('interactive').mockReturnValue('complete')
    const { container } = render(<HomeHeroVideo sources={SOURCES} />)
    expect(srcs(videoOf(container))).toEqual(SOURCES[0])
  })
})
