import badminton from '../../assets/home/badminton.jpg'
import basketball from '../../assets/home/basketball.jpg'
import football from '../../assets/home/football.jpg'
import swimming from '../../assets/home/swimming.jpg'
import tennis from '../../assets/home/tennis.jpg'
import volleyball from '../../assets/home/volleyball.jpg'
import facility from '../../assets/home/hero.jpg'
import { sportPhotoUrl } from '../../media/sportPhotos'

const SPORT_IMAGES: Record<string, string> = {
  badminton,
  basketball,
  football,
  soccer: football,
  swimming,
  tennis,
  volleyball,
}

/** Use bundled sport photography so member browsing stays visual without a network connection. */
export function sportImageFor(name: string): string {
  return SPORT_IMAGES[name.trim().toLowerCase()] ?? facility
}

/**
 * Remote photo and bundled fallback for a sport, for the shared Photo component. Sports without a remote photo
 * use the bundled image for both, so the page still renders offline.
 */
export function sportPhotoFor(name: string, width: number): { src: string; fallback: string } {
  const fallback = sportImageFor(name)
  return { src: sportPhotoUrl(name, width) ?? fallback, fallback }
}
