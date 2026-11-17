import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { tokenStorage } from '../../api/tokenStorage'
import { managerPreviewEnabled } from '../../config/env'

export type MemberScheduleTab = 'center' | 'my-schedule' | 'find-classes' | 'bookings'

const TABS: { value: MemberScheduleTab; label: string }[] = [
  { value: 'my-schedule', label: 'My Schedule' },
  { value: 'center', label: 'Center Schedule' },
  { value: 'find-classes', label: 'Find Classes' },
  { value: 'bookings', label: 'My Bookings' },
]

export function MemberScheduleNav({ activeTab }: { activeTab?: MemberScheduleTab }) {
  const location = useLocation()
  const [params] = useSearchParams()
  const returnTo = params.get('returnTo') ?? ''
  const tabFromReturnTo = returnTo.includes('tab=center')
    ? 'center'
    : returnTo.includes('tab=my-schedule') || returnTo.startsWith('/member/schedule')
    ? 'my-schedule'
    : returnTo.includes('tab=find-classes') || returnTo.startsWith('/member/classes')
    ? 'find-classes'
    : returnTo.includes('tab=bookings') || returnTo.startsWith('/member/bookings')
    ? 'bookings'
    : undefined

  const routeActive = location.pathname === '/member/schedule'
    ? 'my-schedule'
    : location.pathname === '/member/classes'
    ? 'find-classes'
    : location.pathname === '/member/bookings'
    ? 'bookings'
    : (params.get('tab') as MemberScheduleTab) || tabFromReturnTo || 'center'
  const active = activeTab ?? routeActive
  return <>{managerPreviewEnabled() && tokenStorage.getAccessToken() === 'preview-MEMBER' && <p className="notice notice--warning" role="status">Frontend member preview - sample data only. Browsing never changes backend data.</p>}<nav className="browse-nav schedule-workspace__tabs" aria-label="Schedules and bookings">{TABS.map((tab) => <Link key={tab.value} to={`/member/schedules?tab=${tab.value}`} className={`browse-tab${active === tab.value ? ' browse-tab--active' : ''}`} aria-current={active === tab.value ? 'page' : undefined}>{tab.label}</Link>)}</nav></>
}
