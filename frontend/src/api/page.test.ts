import { describe, expect, it } from 'vitest'
import { normalizePage } from './page'

describe('normalizePage', () => {
  it('keeps PageResponse as is', () => {
    const page = { content: [1, 2], page: 1, size: 2, totalElements: 6, totalPages: 3 }
    expect(normalizePage(page)).toEqual(page)
  })

  it('maps Spring Page number to page', () => {
    expect(normalizePage({ content: ['a'], number: 2, size: 10, totalElements: 21, totalPages: 3 })).toEqual({
      content: ['a'],
      page: 2,
      size: 10,
      totalElements: 21,
      totalPages: 3,
    })
  })
})
