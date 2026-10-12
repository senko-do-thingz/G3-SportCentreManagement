/** Returns the page indexes to show, with null standing for a gap. */
export function pageWindow(page: number, totalPages: number, span = 1): (number | null)[] {
  if (totalPages <= 0) {
    return []
  }
  const pages = new Set<number>([0, totalPages - 1])
  for (let index = page - span; index <= page + span; index += 1) {
    if (index >= 0 && index < totalPages) {
      pages.add(index)
    }
  }
  const sorted = Array.from(pages).sort((a, b) => a - b)
  const result: (number | null)[] = []
  sorted.forEach((value, index) => {
    if (index > 0 && value - sorted[index - 1] > 1) {
      result.push(null)
    }
    result.push(value)
  })
  return result
}
