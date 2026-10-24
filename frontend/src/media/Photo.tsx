import { useState, type ImgHTMLAttributes } from 'react'
import { unsplashSrcSet } from './unsplash'

export interface PhotoProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'src' | 'srcSet'> {
  /** High resolution photo, usually from the Unsplash CDN (see unsplashUrl). */
  src: string
  /** Bundled image used when the remote photo cannot load (offline demo, blocked network). */
  fallback: string
  /** Rendered slot width, for example "(min-width: 1024px) 400px, 100vw". Required with remote photos to pick a size. */
  sizes?: string
}

/**
 * Shared photo element. Remote Unsplash photos get a responsive srcset; when the remote request fails the
 * image swaps once to the bundled fallback and drops the srcset so the browser does not retry the CDN.
 * The failure is remembered for that src only: a new src (same element, new sport or photo) is requested again,
 * while a fallback that also fails does not loop. The caller's onError still runs.
 * Images decode off the main thread; callers set loading="lazy" below the fold and fetchPriority="high" for the hero.
 */
export function Photo({ src, fallback, alt = '', sizes, decoding = 'async', onError, ...rest }: PhotoProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const failed = failedSrc === src
  const srcSet = failed ? undefined : unsplashSrcSet(src)
  return (
    <img
      {...rest}
      alt={alt}
      decoding={decoding}
      src={failed ? fallback : src}
      srcSet={srcSet}
      sizes={srcSet ? sizes : undefined}
      onError={(event) => {
        if (!failed) setFailedSrc(src)
        onError?.(event)
      }}
    />
  )
}
