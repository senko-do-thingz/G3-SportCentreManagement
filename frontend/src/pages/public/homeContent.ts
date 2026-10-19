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

/** Copy and photographs from context/Home/1.png and 2.png, not live directory records. */
export const HOME_SPORTS = [
  { name: 'Football', description: 'Fitness & ball control', image: football },
  { name: 'Badminton', description: 'Agility & coordination', image: badminton },
  { name: 'Basketball', description: 'Teamwork & technique', image: basketball },
  { name: 'Volleyball', description: 'Team play & confidence', image: volleyball },
  { name: 'Swimming', description: 'Water skills & endurance', image: swimming },
  { name: 'Tennis', description: 'Footwork & precision', image: tennis },
]

export const EXAMPLE_COACHES = [
  { name: 'Daniel Smith', sport: 'Football', skills: 'Passing - Ball control', certificate: 'AFC C Diploma', image: daniel },
  { name: 'Chris Nguyen', sport: 'Badminton', skills: 'Footwork - Racket skills', certificate: 'Level 1 Coaching Certificate', image: chris },
  { name: 'Emma Wilson', sport: 'Swimming', skills: 'Stroke technique - Water skills', certificate: 'Swim Instructor Certificate', image: emma },
]

export const HOME_NOTICES = {
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
