import { PageHeader } from '@/components/ui/PageHeader'
import { EmptyState } from '@/components/ui/EmptyState'
import { Icon } from '@/utils/icons'

export interface PlaceholderPageProps {
  module: string
  description?: string
}

export function PlaceholderPage({ module, description }: PlaceholderPageProps) {

  return (
    <div>
      <PageHeader
        title={module}
        subtitle={description || `${module} module coming soon`}
      />
      <EmptyState
        icon={<Icon name="dashboard" className="size-8" />}
        title={`${module} Module`}
        description="This module is under development and will be available soon."
      />
    </div>
  )
}

