import type { IconName } from '@/utils/icons'

export interface NavItem {
  id: string
  label: string
  path: string
  icon: IconName
  ownerOnly?: boolean
  badge?: number | string
}

export type NavSection = 'main' | 'more'

// Main sidebar items (desktop)
export const sidebarNavItems: NavItem[] = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    path: '/',
    icon: 'dashboard',
  },
  {
    id: 'sales-orders',
    label: 'Sales & Orders',
    path: '/sales',
    icon: 'sales',
  },
  {
    id: 'inventory',
    label: 'Inventory',
    path: '/inventory',
    icon: 'inventory',
  },
  {
    id: 'payments',
    label: 'Payments',
    path: '/payments',
    icon: 'payments',
  },
  {
    id: 'pnl',
    label: 'Profit & Loss',
    path: '/pnl',
    icon: 'pnl',
    ownerOnly: true,
  },
  {
    id: 'expenses',
    label: 'Expenses',
    path: '/expenses',
    icon: 'expenses',
    ownerOnly: true,
  },
  {
    id: 'costing',
    label: 'Costing',
    path: '/costing',
    icon: 'costing',
    ownerOnly: true,
  },
  {
    id: 'beej',
    label: 'Beej',
    path: '/beej',
    icon: 'beej',
  },
  {
    id: 'production',
    label: 'Production',
    path: '/production',
    icon: 'production',
  },
  {
    id: 'expiry-alerts',
    label: 'Expiry Alerts',
    path: '/expiry-alerts',
    icon: 'expiry',
  },
  {
    id: 'returns',
    label: 'Returns',
    path: '/returns',
    icon: 'returns',
  },
  {
    id: 'khata',
    label: 'Khata',
    path: '/khata',
    icon: 'khata',
  },
  {
    id: 'customers',
    label: 'Customers',
    path: '/customers',
    icon: 'customers',
  },
  {
    id: 'documents',
    label: 'Documents',
    path: '/documents',
    icon: 'documents',
  },
  {
    id: 'licences',
    label: 'Licences',
    path: '/licences',
    icon: 'licences',
    ownerOnly: true,
  },
  {
    id: 'insights',
    label: 'Insights',
    path: '/insights',
    icon: 'insights',
  },
  {
    id: 'settings',
    label: 'Settings',
    path: '/settings',
    icon: 'settings',
  },
]

// Mobile bottom nav items
export const bottomNavItems: NavItem[] = [
  {
    id: 'home',
    label: 'Home',
    path: '/',
    icon: 'home',
  },
  {
    id: 'sale',
    label: 'Sale',
    path: '/sales',
    icon: 'sale',
  },
  {
    id: 'stock',
    label: 'Stock',
    path: '/inventory',
    icon: 'stock',
  },
  {
    id: 'khata',
    label: 'Khata',
    path: '/khata',
    icon: 'khata',
  },
  {
    id: 'more',
    label: 'More',
    path: '/more',
    icon: 'more',
  },
]

// More drawer items (mobile)
export const moreDrawerItems: NavItem[] = [
  {
    id: 'payments',
    label: 'Payments',
    path: '/payments',
    icon: 'payments',
  },
  {
    id: 'pnl',
    label: 'Profit & Loss',
    path: '/pnl',
    icon: 'pnl',
    ownerOnly: true,
  },
  {
    id: 'expenses',
    label: 'Expenses',
    path: '/expenses',
    icon: 'expenses',
    ownerOnly: true,
  },
  {
    id: 'costing',
    label: 'Costing',
    path: '/costing',
    icon: 'costing',
    ownerOnly: true,
  },
  {
    id: 'beej',
    label: 'Beej',
    path: '/beej',
    icon: 'beej',
  },
  {
    id: 'production',
    label: 'Production',
    path: '/production',
    icon: 'production',
  },
  {
    id: 'expiry-alerts',
    label: 'Expiry Alerts',
    path: '/expiry-alerts',
    icon: 'expiry',
  },
  {
    id: 'returns',
    label: 'Returns',
    path: '/returns',
    icon: 'returns',
  },
  {
    id: 'customers',
    label: 'Customers',
    path: '/customers',
    icon: 'customers',
  },
  {
    id: 'documents',
    label: 'Documents',
    path: '/documents',
    icon: 'documents',
  },
  {
    id: 'licences',
    label: 'Licences',
    path: '/licences',
    icon: 'licences',
    ownerOnly: true,
  },
  {
    id: 'insights',
    label: 'Insights',
    path: '/insights',
    icon: 'insights',
  },
  {
    id: 'settings',
    label: 'Settings',
    path: '/settings',
    icon: 'settings',
  },
  {
    id: 'change-password',
    label: 'Change Password',
    path: '/change-password',
    icon: 'password',
  },
  {
    id: 'logout',
    label: 'Logout',
    path: '/logout',
    icon: 'logout',
  },
]

