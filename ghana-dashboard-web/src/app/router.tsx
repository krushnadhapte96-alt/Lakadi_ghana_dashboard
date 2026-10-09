import { lazy, Suspense } from 'react'
import type { ComponentType, ReactNode } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { ProtectedRoute, RoleGuard } from '@/auth/guards'
import { AppLayout } from '@/components/layout/AppLayout'
import { PageTransition } from '@/components/layout/PageTransition'

// Pages split into their own chunks so the counter tablet only downloads what is on screen.
const ChangePasswordPage = lazyNamed(() => import('@/pages/ChangePassword'), 'ChangePasswordPage')
const HomePage = lazyNamed(() => import('@/pages/Home'), 'HomePage')
const LoginPage = lazyNamed(() => import('@/pages/Login'), 'LoginPage')
const RegisterPage = lazyNamed(() => import('@/pages/Register'), 'RegisterPage')
const SalesPage = lazyNamed(() => import('@/pages/Sales'), 'SalesPage')
const InventoryPage = lazyNamed(() => import('@/pages/Inventory'), 'InventoryPage')
const PaymentsPage = lazyNamed(() => import('@/pages/Payments'), 'PaymentsPage')
const PnlPage = lazyNamed(() => import('@/pages/Pnl'), 'PnlPage')
const ExpensesPage = lazyNamed(() => import('@/pages/Expenses'), 'ExpensesPage')
const CostingPage = lazyNamed(() => import('@/pages/Costing'), 'CostingPage')
const BeejPage = lazyNamed(() => import('@/pages/Beej'), 'BeejPage')
const ProductionPage = lazyNamed(() => import('@/pages/Production'), 'ProductionPage')
const ExpiryAlertsPage = lazyNamed(() => import('@/pages/ExpiryAlerts'), 'ExpiryAlertsPage')
const ReturnsPage = lazyNamed(() => import('@/pages/Returns'), 'ReturnsPage')
const KhataPage = lazyNamed(() => import('@/pages/Khata'), 'KhataPage')
const CustomersPage = lazyNamed(() => import('@/pages/Customers'), 'CustomersPage')
const DocumentsPage = lazyNamed(() => import('@/pages/Documents'), 'default')
const LicencesPage = lazyNamed(() => import('@/pages/Licences'), 'LicencesPage')
const InsightsPage = lazyNamed(() => import('@/pages/Insights'), 'InsightsPage')
const SettingsPage = lazyNamed(() => import('@/pages/Settings'), 'SettingsPage')
// Dev-only preview page. The route is registered only in development; in the production bundle the
// path simply does not exist.
const UiKitPage = import.meta.env.DEV
  ? lazyNamed(() => import('@/pages/UiKit'), 'UiKitPage')
  : () => null

function lazyNamed<T extends Record<string, unknown>>(
  loader: () => Promise<T>,
  name: keyof T,
): ComponentType {
  return lazy(() =>
    loader().then((module) => ({ default: module[name] as ComponentType })),
  )
}

function suspense(node: ReactNode): ReactNode {
  return (
    <Suspense fallback={<div className="p-6 text-sm text-ink-soft">Loading...</div>}>
      {node}
    </Suspense>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const router = createBrowserRouter([
  {
    element: (
      <ProtectedRoute>
        <AppLayout>
          <PageTransition />
        </AppLayout>
      </ProtectedRoute>
    ),
    children: [
      { path: '/', element: suspense(<HomePage />) },
      { path: '/sales', element: suspense(<SalesPage />) },
      { path: '/inventory', element: suspense(<InventoryPage />) },
      { path: '/payments', element: suspense(<PaymentsPage />) },
      {
        path: '/pnl',
        element: suspense(
          <RoleGuard permission="read:pnl">
            <PnlPage />
          </RoleGuard>,
        ),
      },
      {
        path: '/expenses',
        element: suspense(
          <RoleGuard permission="read:expenses">
            <ExpensesPage />
          </RoleGuard>,
        ),
      },
      {
        path: '/costing',
        element: suspense(
          <RoleGuard permission="read:costing">
            <CostingPage />
          </RoleGuard>,
        ),
      },
      { path: '/beej', element: suspense(<BeejPage />) },
      { path: '/production', element: suspense(<ProductionPage />) },
      { path: '/expiry-alerts', element: suspense(<ExpiryAlertsPage />) },
      { path: '/returns', element: suspense(<ReturnsPage />) },
      { path: '/khata', element: suspense(<KhataPage />) },
      { path: '/customers', element: suspense(<CustomersPage />) },
      { path: '/documents', element: suspense(<DocumentsPage />) },
      {
        path: '/licences',
        element: suspense(
          <RoleGuard permission="manage:settings">
            <LicencesPage />
          </RoleGuard>,
        ),
      },
      { path: '/insights', element: suspense(<InsightsPage />) },
      // Not wrapped in RoleGuard: the page shows only My Account to a staff member,
      // and every settings endpoint it uses is owner-guarded server-side.
      { path: '/settings', element: suspense(<SettingsPage />) },
      { path: '/change-password', element: suspense(<ChangePasswordPage />) },
      ...(import.meta.env.DEV
        ? [
            {
              path: '/ui-kit',
              element: suspense(
                <RoleGuard permission="manage:users">
                  <UiKitPage />
                </RoleGuard>,
              ),
            },
          ]
        : []),
    ],
  },
  { path: '/login', element: suspense(<LoginPage />) },
  { path: '/register', element: suspense(<RegisterPage />) },
])

// Re-export for barrel imports
export { AppLayout } from '@/components/layout/AppLayout'

