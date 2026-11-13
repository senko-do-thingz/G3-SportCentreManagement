import { useState } from 'react'
import { managerCardTiersApi } from '../../api/managerCardTiersApi'
import { Badge, Button, Card, EmptyState, Spinner, StatusBadge, Table, useToast, type TableColumn, StatIcon } from '../../components/ui'
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

  const tiers = resource.status === 'ready' ? resource.data : []
  const publishedTiers = resource.status === 'ready' ? String(tiers.length).padStart(2, '0') : '--'
  const activeCount = resource.status === 'ready' ? String(tiers.filter((t) => t.isActive).length).padStart(2, '0') : '--'
  const maxDiscount = resource.status === 'ready' && tiers.length ? Math.max(...tiers.map((t) => t.discountPercentage ?? 0)) + '%' : '--'
  const defaultTierLabel = resource.status === 'ready' ? (tiers.find((t) => t.code === 'STANDARD')?.name ?? 'None') : '--'

  const columns: TableColumn<ManagerCardTier>[] = [
    {
      key: 'name', header: 'Tier', width: '24%',
      render: (tier) => <div className="manager-catalog__identity">
        <span className="manager-catalog__label">Tier</span>
        <strong>{tier.name}</strong>
        <span className="manager-catalog__secondary">{tier.code}</span>
        {tier.description && <span className="manager-catalog__description">{tier.description}</span>}
      </div>,
    },
    {
      key: 'price', header: 'Card fee', align: 'right', width: '14%',
      render: (tier) => <div className="manager-catalog__cell manager-catalog__cell--amount">
        <span className="manager-catalog__label">Card fee</span>
        <strong className="manager-catalog__amount">{formatMoney(tier.price)}</strong>
      </div>,
    },
    {
      key: 'durationMonths', header: 'Validity', width: '13%',
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
      key: 'isActive', header: 'Status', width: '10%',
      render: (tier) => <div className="manager-catalog__cell">
        <span className="manager-catalog__label">Status</span>
        <StatusBadge status={tierStatus(tier.isActive)} />
      </div>,
    },
    {
      key: 'actions', header: 'Actions', width: '24%',
      render: (tier) => <div className="manager-catalog__cell manager-catalog__actions">
        <span className="manager-catalog__label">Actions</span>
        {tier.code === 'STANDARD'
          ? <Badge tone="info">Default account tier</Badge>
          : <Button variant="secondary" size="sm" onClick={() => setSelected(tier)}>Edit card benefits</Button>}
      </div>,
    },
  ]

  return <div className="page manager-page manager-catalog manager-catalog--tiers manager-concept-page">
    <div className="concept-header">
      <div className="concept-header__main">
        <div className="concept-eyebrow">MANAGER WORKSPACE</div>
        <h1 className="concept-title">Membership Cards</h1>
        <p className="concept-sub">Review tier fees, validity and package discounts while protecting standard tiers.</p>
      </div>
    </div>

    <div className="concept-hero">
      <div>
        <small>TIER BENEFITS</small>
        <h2>TIERED PRIVILEGES. <span className="concept-goldtext">PREDICTABLE VALUE.</span></h2>
        <p>Review discounts and duration rules across published tiers.</p>
      </div>
    </div>

    <div className="concept-stats">
      <div className="concept-stat">
        <StatIcon name="card" />
        <div>
          <strong>{publishedTiers}</strong>
          <small>Published tiers</small>
        </div>
      </div>
      <div className="concept-stat">
        <StatIcon name="check" />
        <div>
          <strong>{activeCount}</strong>
          <small>Active tiers</small>
        </div>
      </div>
      <div className="concept-stat">
        <StatIcon name="percent" />
        <div>
          <strong>{maxDiscount}</strong>
          <small>Max package discount</small>
        </div>
      </div>
      <div className="concept-stat">
        <StatIcon name="star" />
        <div>
          <strong>{defaultTierLabel}</strong>
          <small>Standard tier</small>
        </div>
      </div>
    </div>

    <PreviewNotice />
    {resource.status === 'loading' && <Card className="manager-catalog__state"><Spinner label="Loading membership cards" /></Card>}
    {resource.status === 'error' && <Card className="manager-catalog__state"><ManagerError error={resource.error} onRetry={reload} /></Card>}
    {resource.status === 'ready' && (!resource.data.length
      ? <Card className="manager-catalog__state"><EmptyState title="No membership card tiers yet" description="No tiers have been returned by the card catalog." /></Card>
      : <Card className="manager-catalog__panel" title="Membership tiers" actions={<span className="manager-catalog__count">{resource.data.length} {resource.data.length === 1 ? 'tier' : 'tiers'}</span>}>
        <p className="manager-catalog__scroll-hint">Swipe or scroll horizontally to view all columns.</p>
        <div className="manager-catalog__table">
          <Table columns={columns} rows={resource.data} rowKey={(tier) => tier.id} caption="Membership card tiers" scrollLabel="Membership card tiers" emptyTitle="No membership card tiers yet" emptyDescription="No tiers have been returned by the card catalog." />
        </div>
        <div className="manager-catalog__policy">
          <span className="manager-catalog__policy-mark" aria-hidden="true">i</span>
          <p>Standard is the protected account tier. Active cards use their tier's current discount for future eligible 30-day and 90-day package registrations; single-session visits are excluded and discounts do not stack. A tier change does not change a card fee already paid or the stored price and discount on registrations already made.</p>
        </div>
      </Card>)}
    {selected && <EditMembershipCardModal tier={selected} onClose={() => setSelected(null)} onSuccess={() => { setSelected(null); toast.success('Card benefits saved'); reload() }} />}
  </div>
}
