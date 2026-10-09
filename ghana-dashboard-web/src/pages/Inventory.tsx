import { useState } from 'react'
import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Icon } from '@/utils/icons'
import { useLanguage } from '@/i18n'

type InventoryTab = 'oil-products' | 'khali' | 'packaging' | 'movements'

export function InventoryPage() {
  const { t } = useLanguage()
  const [activeTab, setActiveTab] = useState<InventoryTab>('oil-products')

  const tabs = [
    { id: 'oil-products' as InventoryTab, label: t('inventory.oilProducts'), count: 0 },
    { id: 'khali' as InventoryTab, label: t('inventory.khali'), count: 0 },
    { id: 'packaging' as InventoryTab, label: t('inventory.packaging'), count: 0 },
    { id: 'movements' as InventoryTab, label: t('inventory.movements'), count: 0 },
  ]

  const renderContent = () => {
    switch (activeTab) {
      case 'oil-products':
        return (
          <EmptyState
            icon={<Icon name="inventory" className="size-8" />}
            title={t('inventory.noOilProducts')}
            description={t('inventory.noOilProductsDesc')}
            action={<Button variant="primary">{t('inventory.addProduct')}</Button>}
          />
        )
      case 'khali':
        return (
          <EmptyState
            icon={<Icon name="inventory" className="size-8" />}
            title={t('inventory.noKhali')}
            description={t('inventory.noKhaliDesc')}
            action={<Button variant="primary">{t('inventory.addKhali')}</Button>}
          />
        )
      case 'packaging':
        return (
          <EmptyState
            icon={<Icon name="inventory" className="size-8" />}
            title={t('inventory.noPackaging')}
            description={t('inventory.noPackagingDesc')}
            action={
              <Button variant="primary">
                <Icon name="plus" className="size-4" />
                {t('inventory.addPackagingItem')}
              </Button>
            }
          />
        )
      case 'movements':
        return (
          <EmptyState
            icon={<Icon name="inventory" className="size-8" />}
            title={t('inventory.noMovements')}
            description={t('inventory.noMovementsDesc')}
          />
        )
      default:
        return null
    }
  }

  return (
    <div>
      <PageHeader
        title={t('nav.inventory')}
        subtitle={t('inventory.subtitle')}
        actions={
          <>
            <Button variant="secondary" size="sm">
              <Icon name="upload" className="size-4" />
              {t('inventory.import')}
            </Button>
            <Button variant="primary" size="sm">
              <Icon name="plus" className="size-4" />
              {t('inventory.addProduct')}
            </Button>
          </>
        }
      />

      <div className="border-b border-line">
        <nav className="flex gap-1">
          {tabs.map((tab) => {
            const isActive = activeTab === tab.id
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={`px-4 py-3 text-sm font-medium transition-colors ${
                  isActive 
                    ? 'border-b-2 border-brand-500 text-brand-600' 
                    : 'text-ink-soft hover:text-ink'
                }`}
              >
                {tab.label}
                {tab.count > 0 && (
                  <span className="ml-2 rounded-full bg-surface-3 px-2 py-0.5 text-xs">
                    {tab.count}
                  </span>
                )}
              </button>
            )
          })}
        </nav>
      </div>

      <div className="mt-6">{renderContent()}</div>
    </div>
  )
}

