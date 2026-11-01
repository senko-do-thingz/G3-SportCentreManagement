import { useState } from 'react'
import { managerCardTiersApi } from '../../api/managerCardTiersApi'
import { Badge, Button, Card, EmptyState, PageHeader, Spinner, StatusBadge, Table, useToast, type TableColumn } from '../../components/ui'
import { formatMoney } from '../../utils/format'
import type { ManagerCardTier } from '../../types/manager'
import { EditMembershipCardModal } from './EditMembershipCardModal'
import { ManagerError, PreviewNotice } from './ManagerShared'
import { useResource } from './useManagerTools'

const load = (signal: AbortSignal) => managerCardTiersApi.list(signal)

function tierStatus(value: boolean | null) {
  return value === null ? null : value ? 'ACTIVE' : 'INACTIVE'
}

function tierValidity(tier: ManagerCardTier) {
  if (tier.code === 'STANDARD') return 'Not applicable'
  return tier.durationMonths === null ? 'Not provided' : tier.durationMonths + ' months'
}

export function MembershipCardsManagementPage() {
  const toast = useToast()
  const [resource, reload] = useResource(load)
  const [selected, setSelected] = useState<ManagerCardTier | null>(null)

  const columns: TableColumn<ManagerCardTier>[] = [
    {
      key: 'name', header: 'Tier', width: '34%',
      render: (tier) => <div className="manager-catalog__identity">
        <span className="manager-catalog__label">Tier</span>
        <strong>{tier.name}</strong>
        <span className="manager-catalog__secondary">{tier.code}</span>
        {tier.description && <span className="manager-catalog__description">{tier.description}</span>}
      </div>,
    },
    {
      key: 'price', header: 'Card fee', align: 'right', width: '16%',
      render: (tier) => <div className="manager-catalog__cell manager-catalog__cell--amount">
        <span className="manager-catalog__label">Card fee</span>
        <strong className="manager-catalog__amount">{formatMoney(tier.price)}</strong>
      </div>,
    },
    {
      key: 'durationMonths', header: 'Validity', width: '15%',
      render: (tier) => <div className="manager-catalog__cell">
        <span className="manager-catalog__label">Validity</span>
        <span>{tierValidity(tier)}</span>
      </div>,
    },
    {
      key: 'discountPercentage', header: 'Package discount', width: '15%',
      render: (tier) => <div className="manager-catalog__cell">
        <span className="manager-catalog__label">Package discount</span>
        <span>{tier.discountPercentage === null ? 'Not provided' : tier.discountPercentage + '%'}</span>
      </div>,
    },
    {
      key: 'isActive', header: 'Status', width: '11%',
      render: (tier) => <div className="manager-catalog__cell">
        <span className="manager-catalog__label">Status</span>
        <StatusBadge status={tierStatus(tier.isActive)} />
      </div>,
    },
    {
      key: 'actions', header: 'Actions', width: '9%',
      render: (tier) => <div className="manager-catalog__cell manager-catalog__actions">
        <span className="manager-catalog__label">Actions</span>
        {tier.code === 'STANDARD'
          ? <Badge tone="info">Default account tier</Badge>
          : <Button variant="secondary" size="sm" onClick={() => setSelected(tier)}>Edit card benefits</Button>}
      </div>,
    },
  ]

  return <div className="page manager-page manager-catalog manager-catalog--tiers">
    <PageHeader title="Membership cards" description="Review tier fees, validity and package discounts." />
    <PreviewNotice />
    {resource.status === 'loading' && <Card className="manager-catalog__state"><Spinner label="Loading membership cards" /></Card>}
    {resource.status === 'error' && <Card className="manager-catalog__state"><ManagerError error={resource.error} onRetry={reload} /></Card>}
    {resource.status === 'ready' && (!resource.data.length
      ? <Card className="manager-catalog__state"><EmptyState title="No membership card tiers yet" description="No tiers have been returned by the card catalog." /></Card>
      : <Card className="manager-catalog__panel" title="Membership tiers" actions={<span className="manager-catalog__count">{resource.data.length} {resource.data.length === 1 ? 'tier' : 'tiers'}</span>}>
        <div className="manager-catalog__table">
          <Table columns={columns} rows={resource.data} rowKey={(tier) => tier.id} caption="Membership card tiers" scrollLabel="Membership card tiers" emptyTitle="No membership card tiers yet" emptyDescription="No tiers have been returned by the card catalog." />
        </div>
        <div className="manager-catalog__policy">
          <span className="manager-catalog__policy-mark" aria-hidden="true">i</span>
          <p>Standard is the protected account tier. Card discounts apply to eligible 30-day and 90-day sport packages; single visits are excluded and discounts do not stack.</p>
        </div>
      </Card>)}
    {selected && <EditMembershipCardModal tier={selected} onClose={() => setSelected(null)} onSuccess={() => { setSelected(null); toast.success('Card benefits saved'); reload() }} />}
  </div>
}
