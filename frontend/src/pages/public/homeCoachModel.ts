import { avatarSrc } from '../../api/avatarUrl'
/** Public coach record from GET /coaches/public (no contact data). */
export interface PublicCoach {
  id: number
  fullName: string
  headline: string | null
  specialties: string | null
  yearsExperience: number | null
  bio?: string | null
  avatarUrl?: string | null
  sportIds: number[]
}

/** "Anna Le Nguyen" -> "AN": first and last initials, used because coaches have no photo in the database. */
export function coachInitials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/)
  const first = parts[0]?.[0] ?? ''
  const last = parts.length > 1 ? parts[parts.length - 1][0] : ''
  return (first + last).toUpperCase()
}

/** Two sport names, then "+N" for the rest, so a coach of every sport keeps a one-line label. */
export function sportSummary(names: string[]): string {
  if (!names.length) return 'Sportify Center coach'
  return names.length > 2 ? `${names.slice(0, 2).join(', ')} +${names.length - 2}` : names.join(', ')
}


/** Specialties are stored as one comma separated text; the card shows up to three of them as tags. */
export function specialtyTags(specialties: string | null | undefined): string[] {
  return (specialties ?? '').split(/[,;]/).map((tag) => tag.trim()).filter(Boolean).slice(0, 3)
}

/**
 * Card paragraph: the coach bio. Without a bio and without a headline, one sentence built from the record,
 * so a card is never empty. Returns null when the headline already introduces the coach.
 */
export function coachStory(coach: PublicCoach, sportNames: string[]): string | null {
  if (coach.bio?.trim()) return coach.bio.trim()
  if (coach.headline?.trim()) return null
  const firstName = coach.fullName.trim().split(/\s+/)[0]
  return sportNames.length ? `${firstName} coaches ${sportNames.join(', ')} at Sportify Center.` : `${firstName} coaches at Sportify Center.`
}

/** Absolute http(s) links and photos uploaded by a manager are shown; anything else falls back to initials. */
export function safeAvatar(url: string | null | undefined): string | null {
  return avatarSrc(url)
}
