import { useEffect, useRef, useState } from 'react'

type NetworkInfo = { saveData?: boolean; effectiveType?: string }
const connection = () => (navigator as Navigator & { connection?: NetworkInfo }).connection

/** True when a background video is appropriate: wide screen, no reduced-motion request and no data saver. */
function canPlayHeroVideo(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false
  const saveData = connection()?.saveData === true
  return !saveData && window.matchMedia('(min-width: 768px)').matches && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * Full HD only on large screens with a connection reported as fast (effectiveType 4g). An absent Network Information
 * API or an unknown type is not proof of a fast network, so the lighter file is tried first.
 */
function prefersFullHd(): boolean {
  return window.matchMedia('(min-width: 1600px)').matches && connection()?.effectiveType === '4g'
}

/** Files of one clip in the order the browser should try them (each clip lists best quality first). */
function orderClipFiles(files: readonly string[], fullHd: boolean): readonly string[] {
  return fullHd ? files : [...files].reverse()
}

/**
 * True once the page has loaded, so the video does not compete with the hero photo and first paint. The ready state
 * is checked again after the listener is added: the load event may fire between the first render and the effect.
 */
function usePageLoaded(): boolean {
  const [loaded, setLoaded] = useState(() => typeof document === 'undefined' || document.readyState === 'complete')
  useEffect(() => {
    if (loaded) return
    const done = () => setLoaded(true)
    window.addEventListener('load', done, { once: true })
    if (document.readyState === 'complete') done()
    return () => window.removeEventListener('load', done)
  }, [loaded])
  return loaded
}

/**
 * Muted background clips for the Home hero, played one after another.
 * The hero photo stays underneath, so phones, reduced motion, data saver and failed clips all show the photo.
 * Each clip lists its files from best to fallback quality; the browser tries Full HD first only on large screens
 * with a connection reported as 4g, and the clip mounts after the page load event so it never delays the hero photo.
 * A clip whose files all fail is skipped; when every clip fails the video is removed.
 */
export interface HomeHeroVideoProps {
  sources: readonly (readonly string[])[]
  showToggle?: boolean
}

export function HomeHeroVideo({ sources, showToggle = true }: HomeHeroVideoProps) {
  const video = useRef<HTMLVideoElement>(null)
  const [enabled] = useState(canPlayHeroVideo)
  const [fullHd] = useState(() => enabled && prefersFullHd())
  const loaded = usePageLoaded()
  const [index, setIndex] = useState(0)
  const [failed, setFailed] = useState<number[]>([])
  const [paused, setPaused] = useState(false)
  const [ready, setReady] = useState(false)
  const playable = sources.map((_, i) => i).filter((i) => !failed.includes(i))

  if (!enabled || !loaded || playable.length === 0) return null

  const current = playable.includes(index) ? index : playable[0]
  const next = () => {
    const position = playable.indexOf(current)
    setIndex(playable[(position + 1) % playable.length])
  }

  function toggle() {
    const el = video.current
    if (!el) return
    if (paused) {
      setPaused(false)
      void el.play()?.catch(() => setPaused(true))
    } else {
      el.pause()
      setPaused(true)
    }
  }

  return (
    <>
      <video
        ref={video}
        key={current}
        className={`home-hero__video${ready ? ' home-hero__video--ready' : ''}`}
        autoPlay
        muted
        playsInline
        loop={playable.length === 1}
        preload="auto"
        aria-hidden="true"
        tabIndex={-1}
        onPlaying={() => setReady(true)}
        onEnded={next}
      >
        {orderClipFiles(sources[current], fullHd).map((src, i, files) => (
          <source key={src} src={src} type="video/mp4" onError={i === files.length - 1 ? () => setFailed((list) => [...list, current]) : undefined} />
        ))}
      </video>
      {showToggle && (
        <button type="button" className="home-hero__video-toggle" aria-label={paused ? 'Play background video' : 'Pause background video'} aria-pressed={paused} onClick={toggle}>
          {paused ? (
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true" focusable="false"><path d="M8 5.5v13l10.5-6.5z" /></svg>
          ) : (
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true" focusable="false"><rect x="6.5" y="5" width="4" height="14" rx="1" /><rect x="13.5" y="5" width="4" height="14" rx="1" /></svg>
          )}
        </button>
      )}
    </>
  )
}
