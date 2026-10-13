/** Date helpers shared by every screen. */

/** Local calendar date of a moment as an ISO date, for example "2026-10-13". */
export function toIsoDate(moment: Date): string {
  const year = moment.getFullYear()
  const month = String(moment.getMonth() + 1).padStart(2, '0')
  const day = String(moment.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
