import { unsplashUrl } from './unsplash'

/**
 * One Unsplash photo per sport, shared by Home and the member class pages so every screen shows the
 * same picture for a sport. Bundled low resolution copies in src/assets/home are the offline fallback.
 */
export const SPORT_PHOTO_IDS: Record<string, string> = {
  football: 'photo-1574629810360-7efbbe195018',
  badminton: 'photo-1722087642932-9b070e9a066e',
  basketball: 'photo-1563506644863-444710df1e03',
  volleyball: 'photo-1728971124745-423b12df446d',
  swimming: 'photo-1530549387789-4c1017266635',
  tennis: 'photo-1554068865-24cecd4e34b8',
}

/** Remote photo for a sport name at the given width, or null for a sport without one. */
export function sportPhotoUrl(name: string, width: number): string | null {
  const key = name.trim().toLowerCase()
  const id = SPORT_PHOTO_IDS[key === 'soccer' ? 'football' : key]
  return id ? unsplashUrl(id, width) : null
}
