import { describe, expect, it } from 'vitest'
import { buildVenueTiles, facilityCountText, facilitySummary, isPublicFacility, type PublicFacility } from './homeFacilityModel'

const court = (id: number, sportId: number | null, type: PublicFacility['facilityType'] = 'COURT', name = `Court ${id}`): PublicFacility => ({ id, name, facilityType: type, sportId, capacity: null })
const SOURCES = [
  { sport: 'Swimming', name: 'Indoor Pool', label: 'Pool', wide: true, image: 'a.jpg', photo: 'a' },
  { sport: 'Badminton', name: 'Badminton', label: 'Indoor', wide: false, image: 'b.jpg', photo: 'b' },
  { sport: 'Football', name: 'Football Field', label: 'Outdoor', wide: true, image: 'c.jpg', photo: 'c' },
]
const SPORTS = [{ id: 1, name: 'Swimming', venueType: 'POOL' }, { id: 2, name: 'Badminton', venueType: 'INDOOR' }, { id: 3, name: 'Football', venueType: 'OUTDOOR' }]

describe('home facility model', () => {
  it('validates public facility rows', () => {
    expect(isPublicFacility({ id: 1, name: 'Pool', facilityType: 'POOL', sportId: 2, capacity: 30 })).toBe(true)
    expect(isPublicFacility({ id: 1, name: 'Hall', facilityType: 'HALL', sportId: null, capacity: null })).toBe(true)
    expect(isPublicFacility({ id: 1, name: ' ', facilityType: 'POOL' })).toBe(false)
    expect(isPublicFacility({ id: 1, name: 'Pool', facilityType: 'GYM' })).toBe(false)
    expect(isPublicFacility({ id: 1, name: 'Pool', facilityType: 'POOL', capacity: 0 })).toBe(false)
  })

  it('counts facilities by type', () => {
    expect(facilityCountText([court(1, 2), court(2, 2)])).toBe('2 courts')
    expect(facilityCountText([court(1, 3, 'FIELD')])).toBe('1 field')
    expect(facilityCountText([court(1, 3), court(2, 3, 'FIELD')])).toBe('1 court, 1 field')
  })

  it('uses database names, counts and venue labels, and keeps wide tiles only for the full set', () => {
    const full = buildVenueTiles(SOURCES, SPORTS, [court(1, 1, 'POOL', 'Main Pool'), court(2, 2), court(3, 2), court(4, 3, 'FIELD', 'Turf Pitch')])
    expect(full.map((tile) => [tile.name, tile.note, tile.label, tile.wide])).toEqual([
      ['Main Pool', '1 pool', 'Pool', true], ['Badminton', '2 courts', 'Indoor', false], ['Turf Pitch', '1 field', 'Outdoor', true]])
    const partial = buildVenueTiles(SOURCES, SPORTS, [court(2, 2), court(4, 3, 'FIELD', 'Turf Pitch')])
    expect(partial.map((tile) => [tile.sport, tile.wide])).toEqual([['Badminton', false], ['Football', false]])
  })

  it('keeps every tile without a count when data is missing', () => {
    expect(buildVenueTiles(SOURCES, null, null).map((tile) => [tile.name, tile.note, tile.wide])).toEqual([
      ['Indoor Pool', null, true], ['Badminton', null, false], ['Football Field', null, true]])
  })

  it('summarises all facility types', () => {
    expect(facilitySummary([court(1, 2), court(2, 2), court(3, 3, 'FIELD'), court(4, 1, 'POOL')])).toBe('2 courts, 1 field and 1 pool, one membership card.')
    expect(facilitySummary([court(1, 2)])).toBe('1 court, one membership card.')
    expect(facilitySummary(null)).toBe('Every court and pool, one membership card.')
  })
})
