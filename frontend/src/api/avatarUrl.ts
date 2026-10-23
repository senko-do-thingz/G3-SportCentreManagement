import { env } from '../config/env'

const UPLOADED = /^\/api\/v1(\/users\/\d+\/avatar(?:\?v=\d+)?)$/
/** Demo photos shipped with the web app in frontend/public/images/coaches. */
const BUNDLED = /^\/images\/coaches\/[a-z0-9-]+\.(?:jpe?g|png|webp)$/

/**
 * Turns a stored photo link into an image address the browser can load:
 * an absolute http(s) link as is, a demo photo shipped with the app (/images/coaches/name.jpg) as is,
 * or an uploaded photo (/api/v1/users/{id}/avatar?v=...) on the configured API.
 * Anything else (empty, javascript:, relative paths) returns null so the caller shows initials.
 */
export function avatarSrc(url: string | null | undefined): string | null {
  const value = url?.trim()
  if (!value) return null
  if (/^https?:\/\/\S+$/i.test(value) || BUNDLED.test(value)) return value
  const uploaded = UPLOADED.exec(value)
  return uploaded ? `${env.apiBaseUrl}${uploaded[1]}` : null
}
