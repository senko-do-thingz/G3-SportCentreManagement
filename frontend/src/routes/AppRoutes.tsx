import { lazy, Suspense, type ReactNode } from 'react'
import { Route, Routes } from 'react-router-dom'
import { RequireRole } from '../auth/RequireRole'
import { MemberLayout } from '../layouts/MemberLayout'
import { ALL_AREAS, type RoleArea } from '../layouts/navigation'
import { PublicLayout } from '../layouts/PublicLayout'
import { StaffLayout } from '../layouts/StaffLayout'
import { PageLoader } from '../components/ui/Spinner'
import { PlaceholderPage } from '../pages/PlaceholderPage'
import { ForbiddenPage } from '../pages/public/ForbiddenPage'
import { HomeRedirect } from '../pages/public/HomeRedirect'
import { LoginPage } from '../pages/public/LoginPage'
import { NotFoundPage } from '../pages/public/NotFoundPage'
import { RegisterPage } from '../pages/public/RegisterPage'

/**
 * Real screens by full path. Later tasks register their page here, for example
 * '/member/packages': <SportPackagesPage />. Paths without an entry show a placeholder.
 */
const SCREENS: Record<string, ReactNode> = {}

/**
 * The UI kit demo page (/ui-kit) exists only in development (npm run dev) and in tests. In a
 * production build import.meta.env.DEV is false, so Vite drops the route and the page code.
 */
const ComponentDemoPage = import.meta.env.DEV
  ? lazy(() => import('../pages/dev/ComponentDemoPage').then((module) => ({ default: module.ComponentDemoPage })))
  : null

function areaRoutes(area: RoleArea) {
  return area.sections
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
}

export function AppRoutes() {
  return (
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
            <RequireRole roles={[area.role]}>
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
  )
}
