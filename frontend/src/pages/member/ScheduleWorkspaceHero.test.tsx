import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { unsplashUrl } from '../../media/unsplash'
import { MemberScheduleNav } from './MemberScheduleNav'
import { ScheduleWorkspaceHero } from './ScheduleWorkspaceHero'

describe('Member schedule workspace presentation', () => {
  it('uses a responsive high-resolution hero image with an offline fallback', () => {
    const source = unsplashUrl('photo-1554068865-24cecd4e34b8', 2200)
    render(<ScheduleWorkspaceHero title="My Schedule" description="My bookings" imageSrc={source} imageFallback="/local/tennis.jpg" imageAlt="Tennis practice" />)
    const photo = screen.getByRole('img', { name: 'Tennis practice' })
    expect(photo).toHaveAttribute('src', source)
    expect(photo).toHaveAttribute('srcset', expect.stringContaining('2200w'))
    fireEvent.error(photo)
    expect(photo).toHaveAttribute('src', '/local/tennis.jpg')
    expect(photo).not.toHaveAttribute('srcset')
  })

  it('keeps the same ordered tab navigation and marks the selected page', () => {
    render(<MemoryRouter initialEntries={['/member/schedules?tab=find-classes&sportId=2']}><MemberScheduleNav activeTab="find-classes" /></MemoryRouter>)
    expect(screen.getAllByRole('link').map((link) => link.textContent)).toEqual(['My Schedule', 'Center Schedule', 'Find Classes', 'My Bookings'])
    expect(screen.getByRole('link', { name: 'Find Classes' })).toHaveAttribute('aria-current', 'page')
  })
})
