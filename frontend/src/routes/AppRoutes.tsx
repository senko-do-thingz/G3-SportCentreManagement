import { lazy, Suspense, type ReactNode } from 'react'
import { Route, Routes } from 'react-router-dom'
import { RequireRole } from '../auth/RequireRole'
import { useAuth } from '../auth/useAuth'
import { MemberLayout } from '../layouts/MemberLayout'
import { ALL_AREAS, MEMBER_AREA, requireNavItem, type RoleArea } from '../layouts/navigation'
import { PublicLayout } from '../layouts/PublicLayout'
import { StaffLayout } from '../layouts/StaffLayout'
import { PageLoader } from '../components/ui/Spinner'
import { MemberDashboardPage } from '../pages/member/MemberDashboardPage'
import { MyProfilePage } from '../pages/member/MyProfilePage'
import { PlaceholderPage } from '../pages/PlaceholderPage'
import { ForbiddenPage } from '../pages/public/ForbiddenPage'
import { HomeRedirect } from '../pages/public/HomeRedirect'
import { LoginPage } from '../pages/public/LoginPage'
import { NotFoundPage } from '../pages/public/NotFoundPage'
import { RegisterPage } from '../pages/public/RegisterPage'
import { AccountSwitchRedirect } from './AccountSwitchRedirect'

/**
 * Real screens by full path. Later tasks register their page here, for example
 * '/member/packages': <SportPackagesPage />. Paths without an entry show a placeholder.
 */
const SCREENS: Record<string, ReactNode> = {
  '/member': <MemberDashboardPage />,
  '/member/profile': <MyProfilePage />,
}

/**
 * Sub pages by area base path, relative to the area. '/member/payments/:paymentId' is the payment
 * summary (F3-01) linked from "Pay now" on the dashboard (FE-02). It shows the payments placeholder
 * until FE-11 registers the real screen here, so the link never opens a 404 page. requireNavItem
 * throws if the My Payments menu entry disappears, so the route is never dropped silently.
 */
const SUB_SCREENS: Record<string, { path: string; element: ReactNode }[]> = {
  '/member': [
    {
      path: 'payments/:paymentId',
      element: <PlaceholderPage item={requireNavItem(MEMBER_AREA, 'payments')} eyebrow={MEMBER_AREA.workspaceLabel} />,
    },
  ],
}

/**
 * The UI kit demo page (/ui-kit) exists only in development (npm run dev) and in tests. In a
 * production build import.meta.env.DEV is false, so Vite drops the route and the page code.
 */
const ComponentDemoPage = import.meta.env.DEV
  ? lazy(() => import('../pages/dev/ComponentDemoPage').then((module) => ({ default: module.ComponentDemoPage })))
  : null

function areaRoutes(area: RoleArea) {
  const subRoutes = (SUB_SCREENS[area.basePath] ?? []).map((screen) => (
    <Route key={`${area.basePath}/${screen.path}`} path={screen.path} element={screen.element} />
  ))
  const itemRoutes = area.sections
    .flatMap((section) => section.items)
    .map((item) => {
      const fullPath = item.path ? `${area.basePath}/${item.path}` : area.basePath
      const element = SCREENS[fullPath] ?? <PlaceholderPage item={item} eyebrow={area.workspaceLabel} />
      return item.path ? (
        <Route key={fullPath} path={item.path} element={element} />
      ) : (
        <Route key={fullPath} index element={element} />
      )
    })
  return [...itemRoutes, ...subRoutes]
}

export function AppRoutes() {
  const { user } = useAuth()
  // Keyed by user id: when the signed in account changes, the whole role area (layout, page and
  // any form in it) is mounted again, so nothing typed or loaded for the previous user survives.
  const accountKey = user ? `user-${user.id}` : 'anonymous'
  return (
    <>
      <AccountSwitchRedirect />
      <Routes>
        <Route path="/" element={<HomeRedirect />} />

        <Route element={<PublicLayout />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>

        {import.meta.env.DEV && ComponentDemoPage && (
          <Route
            path="/ui-kit"
            element={
              <Suspense fallback={<PageLoader label="Loading UI kit" />}>
                <ComponentDemoPage />
              </Suspense>
            }
          />
        )}
        <Route path="/403" element={<ForbiddenPage />} />

        {ALL_AREAS.map((area) => (
          <Route
            key={area.basePath}
            path={area.basePath}
            element={
              <RequireRole key={accountKey} roles={[area.role]}>
                {area.role === 'MEMBER' ? <MemberLayout /> : <StaffLayout />}
              </RequireRole>
            }
          >
            {areaRoutes(area)}
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        ))}

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </>
  )
}
