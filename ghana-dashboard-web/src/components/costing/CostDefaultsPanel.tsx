import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { fetchCostDefaults, updateCostDefaults } from '@/api/costing'
import type { CostDefaults, CostDefaultsInput, OverheadMode } from '@/api/costing'
import { Button, Card, Input, Skeleton } from '@/components/ui'
import { useLanguage } from '@/i18n'
import { cn } from '@/utils/cn'

function ModeToggle({
  value,
  onChange,
  label,
}: {
  value: OverheadMode
  onChange: (mode: OverheadMode) => void
  label: string
}) {
  const { t } = useLanguage()
  return (
    <div className="flex items-center gap-1 rounded-pill border border-line bg-surface p-0.5" role="group" aria-label={label}>
      {(['PER_BATCH', 'PER_KG'] as const).map((mode) => (
        <button
          key={mode}
          type="button"
          aria-pressed={value === mode}
          onClick={() => onChange(mode)}
          className={cn(
            'rounded-pill px-2.5 py-1 text-xs font-medium transition-colors',
            value === mode ? 'bg-brand-600 text-white' : 'text-ink-soft hover:text-ink',
          )}
        >
          {t(mode === 'PER_BATCH' ? 'costing.defaults.mode.perBatch' : 'costing.defaults.mode.perKg')}
        </button>
      ))}
    </div>
  )
}

function OverheadRow({
  label,
  mode,
  value,
  onModeChange,
  onValueChange,
}: {
  label: string
  mode: OverheadMode
  value: string
  onModeChange: (mode: OverheadMode) => void
  onValueChange: (value: string) => void
}) {
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex-1">
        <Input
          label={label}
          type="number"
          min="0"
          step="0.01"
          inputMode="decimal"
          value={value}
          onChange={(e) => onValueChange(e.target.value)}
        />
      </div>
      <ModeToggle value={mode} onChange={onModeChange} label={label} />
    </div>
  )
}

function num(value: string): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function toInput(defaults: CostDefaults): CostDefaultsInput {
  return {
    electricityMode: defaults.electricityMode,
    electricityValue: defaults.electricityValue,
    labourMode: defaults.labourMode,
    labourValue: defaults.labourValue,
    otherMode: defaults.otherMode,
    otherValue: defaults.otherValue,
    khaliValuePerKg: defaults.khaliValuePerKg,
    targetMarginPct: defaults.targetMarginPct,
  }
}

function CostDefaultsForm({ initial }: { initial: CostDefaults }) {
  const { t } = useLanguage()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CostDefaultsInput>(() => toInput(initial))

  const save = useMutation({
    mutationFn: (input: CostDefaultsInput) => updateCostDefaults(input),
    onSuccess: (data) => {
      queryClient.setQueryData(['costing', 'defaults'], data)
      void queryClient.invalidateQueries({ queryKey: ['costing', 'products'] })
      toast.success(t('costing.defaults.saved'))
    },
    onError: () => toast.error(t('costing.defaults.saveFailed')),
  })

  return (
    <Card className="space-y-5 p-5">
      <div>
        <h2 className="text-base font-semibold text-ink">{t('costing.defaults.title')}</h2>
        <p className="text-sm text-ink-soft">{t('costing.defaults.subtitle')}</p>
      </div>

      <div className="space-y-4">
        <OverheadRow
          label={t('costing.defaults.electricity')}
          mode={form.electricityMode}
          value={String(form.electricityValue)}
          onModeChange={(m) => setForm({ ...form, electricityMode: m })}
          onValueChange={(v) => setForm({ ...form, electricityValue: num(v) })}
        />
        <OverheadRow
          label={t('costing.defaults.labour')}
          mode={form.labourMode}
          value={String(form.labourValue)}
          onModeChange={(m) => setForm({ ...form, labourMode: m })}
          onValueChange={(v) => setForm({ ...form, labourValue: num(v) })}
        />
        <OverheadRow
          label={t('costing.defaults.other')}
          mode={form.otherMode}
          value={String(form.otherValue)}
          onModeChange={(m) => setForm({ ...form, otherMode: m })}
          onValueChange={(v) => setForm({ ...form, otherValue: num(v) })}
        />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            label={t('costing.defaults.khaliValue')}
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            value={String(form.khaliValuePerKg)}
            onChange={(e) => setForm({ ...form, khaliValuePerKg: num(e.target.value) })}
          />
          <Input
            label={t('costing.defaults.targetMargin')}
            type="number"
            min="0"
            max="99"
            step="0.1"
            inputMode="decimal"
            value={String(form.targetMarginPct)}
            onChange={(e) => setForm({ ...form, targetMarginPct: num(e.target.value) })}
          />
        </div>
      </div>

      <Button size="sm" disabled={save.isPending} onClick={() => save.mutate(form)}>
        {save.isPending ? t('costing.defaults.saving') : t('costing.defaults.save')}
      </Button>
    </Card>
  )
}

export function CostDefaultsPanel() {
  const defaults = useQuery({ queryKey: ['costing', 'defaults'], queryFn: fetchCostDefaults })

  if (!defaults.data) {
    return (
      <Card className="space-y-4 p-5">
        <Skeleton className="h-5 w-40" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
      </Card>
    )
  }

  return <CostDefaultsForm key={defaults.data.updatedAt} initial={defaults.data} />
}

