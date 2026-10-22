import { useState, type ImgHTMLAttributes } from 'react'

interface HomePhotoProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'src'> {
  /** High resolution photo, usually from the Unsplash CDN. */
  src: string
  /** Bundled image used when the remote photo cannot load (offline demo, blocked network). */
  fallback: string
}

/** Shows a remote photo and swaps to the bundled image once if the remote request fails. */
export function HomePhoto({ src, fallback, alt = '', ...rest }: HomePhotoProps) {
  const [failed, setFailed] = useState(false)
  return <img {...rest} alt={alt} src={failed ? fallback : src} onError={() => setFailed(true)} />
}
