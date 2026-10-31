import { describe, expect, it } from 'vitest'
import { COACH_AREA, MEMBER_AREA, RECEPTIONIST_AREA, areaItems } from './navigation'

describe('Flow 4 role navigation', () => {
  it('exposes training only in the intended role workspaces', () => {
    expect(areaItems(MEMBER_AREA).find((item) => item.path === 'training')?.screens).toEqual(['F4-REQ-05', 'F4-REQ-06'])
    expect(areaItems(COACH_AREA).find((item) => item.path === 'training')?.screens).toEqual(['F4-REQ-01', 'F4-REQ-02', 'F4-REQ-03', 'F4-REQ-04'])
    expect(areaItems(RECEPTIONIST_AREA).find((item) => item.path === 'training-attendance')?.screens).toEqual(['F4-REQ-04', 'F4-REQ-07'])
  })
})
