import { useRef, useState } from 'react'

/** True when a background video is appropriate: wide screen, no reduced-motion request and no data saver. */
function canPlayHeroVideo(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false
  const saveData = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData === true
  return !saveData && window.matchMedia('(min-width: 768px)').matches && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * Muted background clips for the Home hero, played one after another.
 * The hero photo stays underneath, so phones, reduced motion, data saver and failed clips all show the photo.
 * Each clip lists its files from best to fallback quality; the browser tries them in order.
 * A clip whose files all fail is skipped; when every clip fails the video is removed.
 */
export function HomeHeroVideo({ sources }: { sources: readonly (readonly string[])[] }) {
  const video = useRef<HTMLVideoElement>(null)
  const [enabled] = useState(canPlayHeroVideo)
  const [index, setIndex] = useState(0)
  const [failed, setFailed] = useState<number[]>([])
  const [paused, setPaused] = useState(false)
  const [ready, setReady] = useState(false)
  const playable = sources.map((_, i) => i).filter((i) => !failed.includes(i))

  if (!enabled || playable.length === 0) return null

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
        {sources[current].map((src, i, files) => (
          <source key={src} src={src} type="video/mp4" onError={i === files.length - 1 ? () => setFailed((list) => [...list, current]) : undefined} />
        ))}
      </video>
      <button type="button" className="home-hero__video-toggle" aria-label={paused ? 'Play background video' : 'Pause background video'} aria-pressed={paused} onClick={toggle}>
        {paused ? (
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true" focusable="false"><path d="M8 5.5v13l10.5-6.5z" /></svg>
        ) : (
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true" focusable="false"><rect x="6.5" y="5" width="4" height="14" rx="1" /><rect x="13.5" y="5" width="4" height="14" rx="1" /></svg>
        )}
      </button>
    </>
  )
}
