import { useState } from 'react'
import { BillingTab } from '@/components/settings/BillingTab'
import { DataExportTab } from '@/components/settings/DataExportTab'
import { DefaultsTab } from '@/components/settings/DefaultsTab'
import { MyAccountTab } from '@/components/settings/MyAccountTab'
import { NotificationsTab } from '@/components/settings/NotificationsTab'
import { OilTypesTab } from '@/components/settings/OilTypesTab'
import { ShopProfileTab } from '@/components/settings/ShopProfileTab'
import { StaffTab } from '@/components/settings/StaffTab'
import { PageHeader, Tabs, type TabItem } from '@/components/ui'
import { useCan } from '@/auth/useCan'
import { useLanguage } from '@/i18n'

type TabId =
  | 'shop'
  | 'billing'
  | 'staff'
  | 'oilTypes'
  | 'defaults'
  | 'notifications'
  | 'export'
  | 'account'

/**
 * Settings is open to every signed-in user, but only the owner sees the business
 * tabs - the backend enforces the same rule, this just avoids showing a staff member
 * eight tabs that would all 403. My Account is the one tab everyone gets.
 */
export function SettingsPage() {
  const { t } = useLanguage()
  const canManage = useCan('manage:settings')
  const tabs: TabItem[] = [
    ...(canManage
      ? [
          { id: 'shop', label: t('settings.tabs.shop') },
          { id: 'billing', label: t('settings.tabs.billing') },
          { id: 'staff', label: t('settings.tabs.staff') },
          { id: 'oilTypes', label: t('settings.tabs.oilTypes') },
          { id: 'defaults', label: t('settings.tabs.defaults') },
          { id: 'notifications', label: t('settings.tabs.notifications') },
          { id: 'export', label: t('settings.tabs.export') },
        ]
      : []),
    { id: 'account', label: t('settings.tabs.account') },
  ]

  const [active, setActive] = useState<TabId>(canManage ? 'shop' : 'account')
  const current = tabs.some((tab) => tab.id === active) ? active : tabs[0].id

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('settings.title')}
        subtitle={canManage ? t('settings.subtitle') : t('settings.accountOnlySubtitle')}
      />

      <Tabs tabs={tabs} activeTab={current} onTabChange={(id) => setActive(id as TabId)} />

      {current === 'shop' && canManage && <ShopProfileTab />}
      {current === 'billing' && canManage && <BillingTab />}
      {current === 'staff' && canManage && <StaffTab />}
      {current === 'oilTypes' && canManage && <OilTypesTab />}
      {current === 'defaults' && canManage && <DefaultsTab />}
      {current === 'notifications' && canManage && <NotificationsTab />}
      {current === 'export' && canManage && <DataExportTab />}
      {current === 'account' && <MyAccountTab />}
    </div>
  )
}
