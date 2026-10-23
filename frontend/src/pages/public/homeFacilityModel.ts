/** Row from GET /api/v1/facilities/public (active facilities only). */
export interface PublicFacility {
  id: number
  name: string
  facilityType: 'COURT' | 'POOL' | 'FIELD' | 'HALL' | 'STUDIO'
  sportId: number | null
  capacity: number | null
}

/** Photo and position for one sport tile; the facts on the tile come from the database. */
export interface VenueTileSource {
  sport: string
  name: string
  label: string
  wide: boolean
  image: string
  photo: string
}

export interface VenueTile extends VenueTileSource {
  /** Court count line ("2 courts"), or null when the facility list is not available. */
  note: string | null
}

const FACILITY_TYPES = ['COURT', 'POOL', 'FIELD', 'HALL', 'STUDIO']
const NOUNS: Record<PublicFacility['facilityType'], [string, string]> = {
  COURT: ['court', 'courts'],
  POOL: ['pool', 'pools'],
  FIELD: ['field', 'fields'],
  HALL: ['hall', 'halls'],
  STUDIO: ['studio', 'studios'],
}
const VENUE_LABELS: Record<string, string> = { INDOOR: 'Indoor', OUTDOOR: 'Outdoor', POOL: 'Pool' }

export function isPublicFacility(entry: unknown): entry is PublicFacility {
  if (!entry || typeof entry !== 'object') return false
  const row = entry as Record<string, unknown>
  const optionalId = (value: unknown) => value === null || value === undefined || (Number.isSafeInteger(value) && Number(value) > 0)
  return Number.isSafeInteger(row.id) && Number(row.id) > 0 && typeof row.name === 'string' && row.name.trim() !== ''
    && FACILITY_TYPES.includes(String(row.facilityType)) && optionalId(row.sportId) && optionalId(row.capacity)
}

const plural = (count: number, [one, many]: [string, string]) => `${count} ${count === 1 ? one : many}`

/** "2 courts", "1 pool", or "1 court, 1 field" when one sport has different kinds of facility. */
export function facilityCountText(facilities: readonly PublicFacility[]): string {
  const counts = new Map<PublicFacility['facilityType'], number>()
  facilities.forEach((facility) => counts.set(facility.facilityType, (counts.get(facility.facilityType) ?? 0) + 1))
  return [...counts].map(([type, count]) => plural(count, NOUNS[type])).join(', ')
}

/**
 * Builds the Home facility tiles from the database.
 * Without facility data (loading or error) every tile is kept with no count line.
 * With data, a sport tile is shown only when that sport has at least one active facility; a single facility uses its
 * own name ("Indoor Pool"), several use the sport name. The Indoor/Outdoor/Pool label follows the sport venue type.
 * The two-wide-tile layout is kept only for the full set of tiles; otherwise every tile has the same width.
 */
export function buildVenueTiles(
  sources: readonly VenueTileSource[],
  sports: readonly { id: number; name: string; venueType?: string | null }[] | null,
  facilities: readonly PublicFacility[] | null,
): VenueTile[] {
  if (!facilities || !sports) return sources.map((source) => ({ ...source, note: null }))
  const tiles = sources.flatMap((source) => {
    const sport = sports.find((entry) => entry.name.toLowerCase() === source.sport.toLowerCase())
    const own = sport ? facilities.filter((facility) => facility.sportId === sport.id) : []
    if (!own.length) return []
    return [{
      ...source,
      name: own.length === 1 ? own[0].name : source.sport,
      label: VENUE_LABELS[String(sport?.venueType)] ?? source.label,
      note: facilityCountText(own),
    }]
  })
  const full = tiles.length === sources.length
  return tiles.map((tile) => ({ ...tile, wide: full && tile.wide }))
}

/** Section subtitle from the database, e.g. "6 courts, 1 field and 1 pool, one membership card." */
export function facilitySummary(facilities: readonly PublicFacility[] | null): string {
  if (!facilities?.length) return 'Every court and pool, one membership card.'
  const parts = (['COURT', 'FIELD', 'HALL', 'STUDIO', 'POOL'] as const)
    .map((type) => [type, facilities.filter((facility) => facility.facilityType === type).length] as const)
    .filter(([, count]) => count > 0)
    .map(([type, count]) => plural(count, NOUNS[type]))
  const list = parts.length > 1 ? `${parts.slice(0, -1).join(', ')} and ${parts[parts.length - 1]}` : parts[0]
  return `${list}, one membership card.`
}
