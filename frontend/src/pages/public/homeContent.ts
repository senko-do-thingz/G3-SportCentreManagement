import hero from '../../assets/home/hero.jpg'
import football from '../../assets/home/football.jpg'
import badminton from '../../assets/home/badminton.jpg'
import basketball from '../../assets/home/basketball.jpg'
import volleyball from '../../assets/home/volleyball.jpg'
import swimming from '../../assets/home/swimming.jpg'
import tennis from '../../assets/home/tennis.jpg'
import { SPORT_PHOTO_IDS } from '../../media/sportPhotos'
import { unsplashUrl } from '../../media/unsplash'

export const homeHero = hero

/**
 * High resolution photos from Unsplash (free to use under the Unsplash License, credited on /credits).
 * They load from the Unsplash CDN in responsive sizes; Photo falls back to the bundled images above when a request fails.
 */
const unsplash = unsplashUrl

/**
 * Background clips for the Home hero from Mixkit (free for commercial and personal use under the Mixkit License,
 * no attribution required). Each clip streams from the Mixkit CDN in Full HD (1080p) with 720p as the fallback;
 * the hero photo shows while they load or when both files fail.
 */
const mixkit = (id: number) => [1080, 720].map((size) => `https://assets.mixkit.co/videos/${id}/${id}-${size}.mp4`)

export const HOME_HERO_VIDEOS = [
  mixkit(44448), // Basketball
  mixkit(43499), // Football
  mixkit(873),   // Tennis
  mixkit(3168),  // Swimming
  mixkit(12321), // Volleyball
  mixkit(13038), // Badminton
] as const

export const HOME_PHOTOS = {
  hero: unsplash('photo-1626224583764-f87db24ac4ea', 2200),
  cta: unsplash('photo-1504450758481-7338eba7524a', 2000),
  /** Log in and Sign up background (UI-07): portrait dunk photo, player on the left. */
  auth: unsplash('photo-1608245449230-4ac19066d2d0', 2200),
}

/** Copy and photographs from context/Home/1.png and 2.png, not live directory records. */
export const HOME_SPORTS = [
  { name: 'Football', description: 'Fitness & ball control', image: football, photo: unsplash(SPORT_PHOTO_IDS.football, 1000) },
  { name: 'Badminton', description: 'Agility & coordination', image: badminton, photo: unsplash(SPORT_PHOTO_IDS.badminton, 1000) },
  { name: 'Basketball', description: 'Teamwork & technique', image: basketball, photo: unsplash(SPORT_PHOTO_IDS.basketball, 1000) },
  { name: 'Volleyball', description: 'Team play & confidence', image: volleyball, photo: unsplash(SPORT_PHOTO_IDS.volleyball, 1000) },
  { name: 'Swimming', description: 'Water skills & endurance', image: swimming, photo: unsplash(SPORT_PHOTO_IDS.swimming, 1000) },
  { name: 'Tennis', description: 'Footwork & precision', image: tennis, photo: unsplash(SPORT_PHOTO_IDS.tennis, 1000) },
]


/**
 * Where you play: tile order, photos and default labels per sport. Names, court counts and which tiles appear come
 * from GET /api/v1/facilities/public (see buildVenueTiles). Photos show empty venues; the bundled sport photos are
 * the fallback. Wide tiles take two grid columns.
 */
export const HOME_FACILITIES = [
  { sport: 'Swimming', name: 'Indoor Pool', label: 'Pool', wide: true, image: swimming, photo: unsplash('photo-1782958995190-75701221aab4', 1400) },
  { sport: 'Basketball', name: 'Basketball', label: 'Indoor', wide: false, image: basketball, photo: unsplash('photo-1649706473901-69f5b7830241', 900) },
  { sport: 'Badminton', name: 'Badminton', label: 'Indoor', wide: false, image: badminton, photo: unsplash('photo-1775993167393-f2add1f8eec2', 900) },
  { sport: 'Tennis', name: 'Tennis', label: 'Outdoor', wide: false, image: tennis, photo: unsplash('photo-1635089877059-500eb3720c61', 900) },
  { sport: 'Volleyball', name: 'Volleyball', label: 'Indoor', wide: false, image: volleyball, photo: unsplash('photo-1791471153826-7f30ebf6b70e', 900) },
  { sport: 'Football', name: 'Football Field', label: 'Outdoor', wide: true, image: football, photo: unsplash('photo-1546717003-caee5f93a9db', 1400) },
] as const

export const HOME_NOTICES = {
  coaches: {
    title: 'Our coaches',
    text: 'Sign in as a member and open a coach-led class to see each coach, their upcoming sessions and verified certifications.',
  },
  recommendations: {
    title: 'Find your starting point',
    text: 'Personalised recommendations are not available yet. You can compare sports, choose a level in the class finder, or ask the front desk about your goals.',
  },
  reception: {
    title: 'Contact reception',
    text: 'Please speak to the front desk at Sportify Center for help with age groups, packages and training goals. Online reception messaging is not available yet.',
  },
  assistant: {
    title: 'AI Assistant',
    text: 'The AI Assistant is not available yet. Please speak to reception for help with sports, packages or bookings.',
  },
  member: {
    title: 'Member booking and purchases',
    text: 'Online booking and personal package purchases are available in the Member area. Your current account has a staff workspace; use its existing tools to assist members.',
  },
}

/** Answers follow the project business rules; they do not promise features that are not implemented. */
export const HOME_FAQ = [
  { question: 'How do I join a class?', answer: 'Create an account, pick a sport and a package, then book your sessions in the Member area.' },
  { question: 'Self-training or coach-led?', answer: 'Self-training is on your own at your pace. Coach-led classes are taught by a qualified coach. Each has its own price for each sport.' },
  { question: 'What does a membership card give me?', answer: 'A discount on eligible sport packages, plus extra perks on higher tiers. Card validity is separate from package validity.' },
  { question: 'Can I come for a single visit?', answer: 'Yes. A single visit has its own price, and membership card discounts do not apply to it.' },
]
