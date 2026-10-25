/*
 * Unsplash photo URLs (free to use under the Unsplash License, credited on /credits).
 * Every remote photo goes through these helpers so the page can offer responsive sizes:
 * the browser downloads the smallest width that fills the slot instead of the full desktop file.
 */

/** Widths offered in srcset. The largest entry is always the width the caller asked for. */
export const UNSPLASH_WIDTHS = [480, 720, 1080, 1600, 2200] as const

const UNSPLASH_HOST = 'https://images.unsplash.com/'

/** Cropped, compressed photo at the given width. */
export function unsplashUrl(id: string, width: number): string {
  return `${UNSPLASH_HOST}${id}?auto=format&fit=crop&w=${width}&q=75`
}

/** Photo id ("photo-...") from an Unsplash URL, or an empty string for any other URL. */
export function unsplashId(url: string): string {
  return url.startsWith(UNSPLASH_HOST) ? (/photo-[\w-]+/.exec(url)?.[0] ?? '') : ''
}

/**
 * srcset for an Unsplash URL built by unsplashUrl: every standard width below the requested one,
 * plus the requested width. Returns undefined for any other URL (bundled files have one size).
 */
export function unsplashSrcSet(url: string): string | undefined {
  const id = unsplashId(url)
  const max = Number(/[?&]w=(\d+)/.exec(url)?.[1])
  if (!id || !Number.isFinite(max) || max <= 0) return undefined
  const widths = [...UNSPLASH_WIDTHS.filter((width) => width < max), max]
  return widths.map((width) => `${unsplashUrl(id, width)} ${width}w`).join(', ')
}

/** Small cropped preview for the credits page; faces keeps portraits centred on the face. */
export function unsplashThumb(id: string, width: number, height: number, faces = false): string {
  return `${UNSPLASH_HOST}${id}?auto=format&fit=crop${faces ? '&crop=faces' : ''}&w=${width}&h=${height}&q=60`
}

/** Original photo page on the Unsplash CDN, linked from the credits page. */
export function unsplashOriginal(id: string): string {
  return `${UNSPLASH_HOST}${id}`
}
