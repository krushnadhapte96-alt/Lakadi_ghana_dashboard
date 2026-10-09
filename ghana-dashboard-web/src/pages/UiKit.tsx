import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { IconButton } from '@/components/ui/IconButton'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Skeleton } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { Checkbox } from '@/components/ui/Checkbox'
import { Switch } from '@/components/ui/Switch'
import { RadioGroup } from '@/components/ui/RadioGroup'
import { Icon } from '@/utils/icons'

export function UiKitPage() {
  return (
    <div>
      <PageHeader
        title="UI Kit"
        subtitle="Reusable component library"
      />

      <div className="grid gap-8">
        {/* Buttons */}
        <section>
          <h2 className="mb-4 text-lg font-semibold text-ink">Buttons</h2>
          <div className="flex flex-wrap gap-4">
            <Button variant="primary" size="sm">
              Primary Small
            </Button>
            <Button variant="primary" size="md">
              Primary Medium
            </Button>
            <Button variant="primary" size="lg">
              Primary Large
            </Button>
            <Button variant="secondary" size="md">
              Secondary
            </Button>
            <Button variant="ghost" size="md">
              Ghost
            </Button>
            <Button variant="danger" size="md">
              Danger
            </Button>
            <IconButton variant="primary" size="md">
              <Icon name="plus" className="size-4" />
            </IconButton>
          </div>
        </section>

        {/* Inputs */}
        <section>
          <h2 className="mb-4 text-lg font-semibold text-ink">Inputs</h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Input label="Text Input" placeholder="Enter text" hint="Hint text" />
            <Input
              label="Password"
              type="password"
              placeholder="Enter password"
            />
            <Input label="Email" type="email" placeholder="Enter email" />
          </div>
        </section>

        {/* Badges */}
        <section>
          <h2 className="mb-4 text-lg font-semibold text-ink">Badges</h2>
          <div className="flex flex-wrap gap-2">
            <Badge variant="default">Default</Badge>
            <Badge variant="primary">Primary</Badge>
            <Badge variant="success">Success</Badge>
            <Badge variant="warning">Warning</Badge>
            <Badge variant="danger">Danger</Badge>
            <Badge variant="info">Info</Badge>
            <Badge variant="primary" dot>
              With Dot
            </Badge>
          </div>
        </section>

        {/* Toggle Inputs */}
        <section>
          <h2 className="mb-4 text-lg font-semibold text-ink">Toggle Inputs</h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Checkbox label="Checkbox" />
            <Switch label="Switch" />
            <RadioGroup
              label="Radio Group"
              options={[
                { value: 'option1', label: 'Option 1' },
                { value: 'option2', label: 'Option 2' },
                { value: 'option3', label: 'Option 3' },
              ]}
            />
          </div>
        </section>

        {/* Cards */}
        <section>
          <h2 className="mb-4 text-lg font-semibold text-ink">Cards</h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Card className="p-4">
              <h3 className="font-semibold text-ink">Card Title</h3>
              <p className="mt-2 text-sm text-ink-soft">Card content goes here</p>
            </Card>
            <Card className="p-4">
              <h3 className="font-semibold text-ink">Card Title</h3>
              <p className="mt-2 text-sm text-ink-soft">Card content goes here</p>
            </Card>
            <Card className="p-4">
              <h3 className="font-semibold text-ink">Card Title</h3>
              <p className="mt-2 text-sm text-ink-soft">Card content goes here</p>
            </Card>
          </div>
        </section>

        {/* Skeleton */}
        <section>
          <h2 className="mb-4 text-lg font-semibold text-ink">Skeleton</h2>
          <div className="flex gap-4">
            <Skeleton className="h-10 w-20" />
            <Skeleton className="h-10 w-32" variant="text" />
            <Skeleton className="h-10 w-10" variant="circular" />
          </div>
        </section>

        {/* Empty State */}
        <section>
          <h2 className="mb-4 text-lg font-semibold text-ink">Empty State</h2>
          <EmptyState
            icon={<Icon name="dashboard" className="size-8" />}
            title="No Data"
            description="There is no data to display"
            action={<Button variant="primary">Create New</Button>}
          />
        </section>
      </div>
    </div>
  )
}

