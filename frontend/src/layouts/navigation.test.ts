import { describe, expect, it } from 'vitest'
import { MEMBER_AREA, requireNavItem } from './navigation'

/** FE-02 review round 1 (m7): routes that depend on a menu entry fail loudly when it is missing. */
describe('requireNavItem', () => {
  it('TC-FE02-110 returns the menu item with that path', () => {
    expect(requireNavItem(MEMBER_AREA, 'payments').title).toBe('My Payments and Receipts')
  })

  it('TC-FE02-111 throws a clear error when the item is missing', () => {
    expect(() => requireNavItem(MEMBER_AREA, 'missing')).toThrow(
      'Menu item "missing" is missing from /member in layouts/navigation.ts',
    )
  })
})
