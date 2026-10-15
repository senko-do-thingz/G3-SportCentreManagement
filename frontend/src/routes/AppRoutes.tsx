import { lazy, Suspense, type ReactNode } from 'react'
import { Route, Routes } from 'react-router-dom'
import { RequireRole } from '../auth/RequireRole'
import { useAuth } from '../auth/useAuth'
import { MemberLayout } from '../layouts/MemberLayout'
import { ALL_AREAS, type RoleArea } from '../layouts/navigation'
import { PublicLayout } from '../layouts/PublicLayout'
import { StaffLayout } from '../layouts/StaffLayout'
import { PageLoader } from '../components/ui/Spinner'
import { MemberDashboardPage } from '../pages/member/MemberDashboardPage'
import { MembershipCardsPage } from '../pages/member/MembershipCardsPage'
import { MyMembershipPage } from '../pages/member/MyMembershipPage'
import { MyPaymentsPage } from '../pages/member/MyPaymentsPage'
import { MyProfilePage } from '../pages/member/MyProfilePage'
import { PackageDetailPage } from '../pages/member/PackageDetailPage'
import { PaymentStatusPage } from '../pages/member/PaymentStatusPage'
import { PaymentSummaryPage } from '../pages/member/PaymentSummaryPage'
import { ReceiptPage } from '../pages/member/ReceiptPage'
import { SportPackagesPage } from '../pages/member/SportPackagesPage'
import { PlaceholderPage } from '../pages/PlaceholderPage'
import { CheckInPage } from '../pages/reception/CheckInPage'
import { MemberSearchPage } from '../pages/reception/MemberSearchPage'
import { PaymentRequestsPage } from '../pages/reception/PaymentRequestsPage'
import { ReceptionPaymentPage } from '../pages/reception/ReceptionPaymentPage'
import { ReceptionReceiptPage } from '../pages/reception/ReceptionReceiptPage'
import { RefundDetailPage } from '../pages/reception/RefundDetailPage'
import { RefundRequestsPage } from '../pages/reception/RefundRequestsPage'
import { RegisterRenewPage } from '../pages/reception/RegisterRenewPage'
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
  '/member/packages': <SportPackagesPage />,
  '/member/membership': <MyMembershipPage />,
  '/member/payments': <MyPaymentsPage />,
  '/reception': <MemberSearchPage />,
  '/reception/register': <RegisterRenewPage />,
  '/reception/check-in': <CheckInPage />,
  '/reception/payments': <PaymentRequestsPage />,
  '/reception/refunds': <RefundRequestsPage />,
}

/**
 * Sub pages by area base path, relative to the area.
 * - 'packages/:packageId': Package Detail and Registration (F1-10, FE-03).
 * - 'membership/cards': Membership Cards and the Renew Membership Card overlay (F1-11, FE-04),
 *   linked from the Membership card tab of My Membership (F1-12).
 * - 'payments/:paymentId': Payment Summary (F3-01, S3-Single), linked from "Pay now"
 *   on the dashboard (FE-02) or after registration (FE-03). FE-11 adds payment
 *   method selection and payment status navigation.
 * - 'payments/:paymentId/status': Payment Instructions and Status (F3-02, FE-11).
 * - 'payments/receipts/:invoiceId': Receipt (S3-CardReceipt, S3-MemberReceipt,
 *   S3-SingleReceipt, FE-11).
 * - '/reception' 'payments/:paymentId': Verify and Record Payment (F3-05) and
 *   Payment Confirmed and Receipt (F3-06), opened from Payment Requests (F3-04),
 *   a pending online request or a desk sale (FE-05, FE-12).
 * - '/reception' 'payments/:paymentId/receipt': Printable receipt (S3-Receipt, FE-12).
 * - '/reception' 'refunds/:refundId': Refund Submitted, Approved and Completed
 *   (F3-07, FE-12).
 */
const SUB_SCREENS: Record<string, { path: string; element: ReactNode }[]> = {
  '/member': [
    { path: 'packages/:packageId', element: <PackageDetailPage /> },
    { path: 'membership/cards', element: <MembershipCardsPage /> },
    { path: 'payments/:paymentId', element: <PaymentSummaryPage /> },
    { path: 'payments/:paymentId/status', element: <PaymentStatusPage /> },
    { path: 'payments/receipts/:invoiceId', element: <ReceiptPage /> },
  ],
  '/reception': [
    { path: 'payments/:paymentId', element: <ReceptionPaymentPage /> },
    { path: 'payments/:paymentId/receipt', element: <ReceptionReceiptPage /> },
    { path: 'refunds/:refundId', element: <RefundDetailPage /> },
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
