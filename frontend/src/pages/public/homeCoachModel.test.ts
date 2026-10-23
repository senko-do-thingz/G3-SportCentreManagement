import { describe, expect, it } from 'vitest'
import { coachInitials, coachStory, safeAvatar, specialtyTags, sportSummary } from './homeCoachModel'

describe('coach card helpers', () => {
  it('builds initials from the first and last name', () => {
    expect(coachInitials('An Nguyen')).toBe('AN')
    expect(coachInitials('  pham thu ha ')).toBe('PH')
    expect(coachInitials('Quan')).toBe('Q')
  })

  it('keeps long sport lists on one line', () => {
    expect(sportSummary([])).toBe('Sportify Center coach')
    expect(sportSummary(['Swimming'])).toBe('Swimming')
    expect(sportSummary(['Football', 'Tennis'])).toBe('Football, Tennis')
    expect(sportSummary(['Football', 'Badminton', 'Basketball', 'Volleyball', 'Swimming', 'Tennis'])).toBe('Football, Badminton +4')
  })

  it('splits specialties into at most three tags', () => {
    expect(specialtyTags(' Footwork, Net shots ;Doubles, Smash ')).toEqual(['Footwork', 'Net shots', 'Doubles'])
    expect(specialtyTags(' , ')).toEqual([])
    expect(specialtyTags(null)).toEqual([])
  })

  it('uses the bio, skips the paragraph when only a headline exists, otherwise builds it from the coach record', () => {
    const base = { id: 1, fullName: 'An Nguyen', headline: null, specialties: null, yearsExperience: null, sportIds: [] }
    expect(coachStory({ ...base, bio: '  Former national swimmer.  ' }, ['Swimming'])).toBe('Former national swimmer.')
    expect(coachStory({ ...base, headline: 'Swimming coach', specialties: 'Butterfly', yearsExperience: 1 }, ['Swimming'])).toBeNull()
    expect(coachStory(base, ['Swimming', 'Tennis'])).toBe('An coaches Swimming, Tennis at Sportify Center.')
    expect(coachStory(base, [])).toBe('An coaches at Sportify Center.')
  })

  it('accepts only http and https avatar links', () => {
    expect(safeAvatar('https://cdn.example.test/a.jpg')).toBe('https://cdn.example.test/a.jpg')
    expect(safeAvatar('javascript:alert(1)')).toBeNull()
    expect(safeAvatar('')).toBeNull()
    expect(safeAvatar(null)).toBeNull()
    expect(safeAvatar('/api/v1/users/7/avatar?v=5')).toMatch(/\/users\/7\/avatar\?v=5$/)
  })
})
