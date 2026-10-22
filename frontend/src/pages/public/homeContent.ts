import hero from '../../assets/home/hero.jpg'
import football from '../../assets/home/football.jpg'
import badminton from '../../assets/home/badminton.jpg'
import basketball from '../../assets/home/basketball.jpg'
import volleyball from '../../assets/home/volleyball.jpg'
import swimming from '../../assets/home/swimming.jpg'
import tennis from '../../assets/home/tennis.jpg'
import daniel from '../../assets/home/coach-football.jpg'
import chris from '../../assets/home/coach-badminton.jpg'
import emma from '../../assets/home/coach-swimming.jpg'

export const homeHero = hero

/**
 * High resolution photos from Unsplash (free to use under the Unsplash License, no attribution required).
 * They load from the Unsplash CDN; HomePhoto falls back to the bundled images above when a request fails.
 */
const unsplash = (id: string, width: number) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=75`

export const HOME_PHOTOS = {
  hero: unsplash('photo-1626224583764-f87db24ac4ea', 2200),
  cta: unsplash('photo-1504450758481-7338eba7524a', 2000),
}

/** Copy and photographs from context/Home/1.png and 2.png, not live directory records. */
export const HOME_SPORTS = [
  { name: 'Football', description: 'Fitness & ball control', image: football, photo: unsplash('photo-1574629810360-7efbbe195018', 1000) },
  { name: 'Badminton', description: 'Agility & coordination', image: badminton, photo: unsplash('photo-1722087642932-9b070e9a066e', 1000) },
  { name: 'Basketball', description: 'Teamwork & technique', image: basketball, photo: unsplash('photo-1563506644863-444710df1e03', 1000) },
  { name: 'Volleyball', description: 'Team play & confidence', image: volleyball, photo: unsplash('photo-1547347298-4074fc3086f0', 1000) },
  { name: 'Swimming', description: 'Water skills & endurance', image: swimming, photo: unsplash('photo-1530549387789-4c1017266635', 1000) },
  { name: 'Tennis', description: 'Footwork & precision', image: tennis, photo: unsplash('photo-1554068865-24cecd4e34b8', 1000) },
]

export const EXAMPLE_COACHES = [
  { name: 'Daniel Smith', sport: 'Football', skills: 'Passing - Ball control', certificate: 'AFC C Diploma', image: daniel, photo: unsplash('photo-1585757318177-0570a997dc3a', 800) },
  { name: 'Chris Nguyen', sport: 'Badminton', skills: 'Footwork - Racket skills', certificate: 'Level 1 Coaching Certificate', image: chris, photo: unsplash('photo-1761286753703-570ed91dd90e', 800) },
  { name: 'Emma Wilson', sport: 'Swimming', skills: 'Stroke technique - Water skills', certificate: 'Swim Instructor Certificate', image: emma, photo: unsplash('photo-1600965962361-9035dbfd1c50', 800) },
]

export const HOME_NOTICES = {
  coaches: {
    title: 'Our coaches',
    text: 'These are illustrative coach profiles. Sign in as a member and open a coach-led class to see current coaches and their verified certifications.',
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
