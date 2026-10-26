import { unsplashId, unsplashOriginal, unsplashThumb } from '../../media/unsplash'
import { HOME_FACILITIES, HOME_HERO_VIDEOS, HOME_PHOTOS, HOME_SPORTS } from './homeContent'

export interface CreditItem {
  /** Where the media appears on the site. */
  usedFor: string
  /** Small preview image, or null for media without a still preview (video, font). */
  thumb: string | null
  /** Page the visitor can open to see the original. */
  link: string
  linkLabel: string
}

export interface CreditGroup {
  title: string
  provider: string
  license: { name: string; url: string }
  note: string
  items: CreditItem[]
}

const UNSPLASH_LICENSE = { name: 'Unsplash License', url: 'https://unsplash.com/license' }
const unsplashItem = (usedFor: string, url: string): CreditItem => {
  const id = unsplashId(url) || url
  return { usedFor, thumb: unsplashThumb(id, 320, 200), link: unsplashOriginal(id), linkLabel: 'View original on Unsplash' }
}

/** Demo coach portraits shipped in frontend/public/images/coaches (see scripts/demo-data/download-coach-photos.ps1). */
export const COACH_DEMO_PHOTOS = [
  { name: 'Nguyễn Minh Khoa', file: 'khoa-nguyen.jpg', id: 'photo-1681097561932-36d0df02b379' },
  { name: 'Trần Thu Hà', file: 'ha-tran.jpg', id: 'photo-1683848644087-c3cb69b77d4f' },
  { name: 'Lê Quốc Bảo', file: 'bao-le.jpg', id: 'photo-1676378385638-135ca6ef4619' },
  { name: 'Phạm Hoàng Long', file: 'long-pham.jpg', id: 'photo-1750698545009-679820502908' },
  { name: 'Võ Ngọc Trâm', file: 'tram-vo.jpg', id: 'photo-1744725845508-054bf3b6d80c' },
  { name: 'Đặng Gia Huy', file: 'huy-dang.jpg', id: 'photo-1612338458140-298a970a9ba5' },
] as const

const MIXKIT_PAGES: Record<string, { label: string; page: string }> = {
  '44448': { label: 'Skilled basketball player shooting baskets, training alone', page: 'https://mixkit.co/free-stock-video/skilled-basketball-player-shooting-baskets-training-alone-44448/' },
  '43499': { label: 'Goal play in a semi-professional soccer game', page: 'https://mixkit.co/free-stock-video/goal-play-in-a-semi-professional-soccer-game-43499/' },
  '873': { label: 'Tennis player serving', page: 'https://mixkit.co/free-stock-video/tennis-player-serving-873/' },
  '3168': { label: 'Man swimming in a pool', page: 'https://mixkit.co/free-stock-video/man-swimming-in-a-pool-3168/' },
  '12321': { label: 'Volleyball match view towards the net', page: 'https://mixkit.co/free-stock-video/volleyball-match-view-towards-the-net-12321/' },
  '13038': { label: 'Siblings playing badminton in the sunset', page: 'https://mixkit.co/free-stock-video/siblings-playing-badminton-in-the-sunset-13038/' },
}

/** Every third-party photo, video and font on the public pages, built from the same constants the pages use. */
export function buildCredits(): CreditGroup[] {
  const videoIds = HOME_HERO_VIDEOS.map((files) => /videos\/(\d+)\//.exec(files[0])?.[1] ?? '')
  return [
    {
      title: 'Photos',
      provider: 'Unsplash',
      license: UNSPLASH_LICENSE,
      note: 'Free to use, including commercially; attribution is not required but is given here.',
      items: [
        unsplashItem('Home - hero background', HOME_PHOTOS.hero),
        unsplashItem('Log in and Sign up - background (photo by August Phlieger)', HOME_PHOTOS.auth),
        ...HOME_SPORTS.map((sport) => unsplashItem(`Sports - ${sport.name}`, sport.photo)),
        ...HOME_FACILITIES.map((venue) => unsplashItem(`Where you play - ${venue.name}`, venue.photo)),
        unsplashItem('Home - Ready to play banner', HOME_PHOTOS.cta),
      ],
    },
    {
      title: 'Demo coach portraits',
      provider: 'Unsplash',
      license: UNSPLASH_LICENSE,
      note: 'Stock models used for the class demonstration only. They are not real staff of the centre.',
      items: COACH_DEMO_PHOTOS.map((coach) => ({ ...unsplashItem(`Coaches - ${coach.name}`, coach.id), thumb: unsplashThumb(coach.id, 200, 200, true) })),
    },
    {
      title: 'Background videos',
      provider: 'Mixkit',
      license: { name: 'Mixkit License', url: 'https://mixkit.co/license/#videoFree' },
      note: 'Free stock video, no attribution required.',
      items: videoIds.map((id) => ({ usedFor: `Home - hero video: ${MIXKIT_PAGES[id]?.label ?? `clip ${id}`}`, thumb: null, link: MIXKIT_PAGES[id]?.page ?? 'https://mixkit.co/', linkLabel: `Mixkit ${id}` })),
    },
    {
      title: 'Font',
      provider: 'Google Fonts / Fontsource',
      license: { name: 'SIL Open Font License 1.1', url: 'https://openfontlicense.org' },
      note: 'Headings use Be Vietnam Pro, bundled with the site through the @fontsource/be-vietnam-pro package.',
      items: [{ usedFor: 'All Home headings', thumb: null, link: 'https://fonts.google.com/specimen/Be+Vietnam+Pro', linkLabel: 'Be Vietnam Pro' }],
    },
  ]
}
