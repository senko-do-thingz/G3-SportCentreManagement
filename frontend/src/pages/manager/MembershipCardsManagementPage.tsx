import { useState } from 'react'
import { managerCardTiersApi } from '../../api/managerCardTiersApi'
import { Badge, Button, Card, EmptyState, PageHeader, Spinner, StatusBadge, useToast } from '../../components/ui'
import { formatMoney } from '../../utils/format'
import type { ManagerCardTier } from '../../types/manager'
import { EditMembershipCardModal } from './EditMembershipCardModal'
import { DetailList, ManagerError, PreviewNotice } from './ManagerShared'
import { useResource } from './useManagerTools'

const load = (signal: AbortSignal) => managerCardTiersApi.list(signal)

export function MembershipCardsManagementPage() {
  const toast = useToast()
  const [resource, reload] = useResource(load)
  const [selected, setSelected] = useState<ManagerCardTier | null>(null)
  return <div className="page manager-page">
    <PageHeader eyebrow="Center management" title="Membership Cards & Benefits" description="Set card fees, validity and discounts." />
    <PreviewNotice />
    {resource.status === 'loading' && <Card><Spinner label="Loading membership cards" /></Card>}
    {resource.status === 'error' && <Card><ManagerError error={resource.error} onRetry={reload} /></Card>}
    {resource.status === 'ready' && (!resource.data.length ? <Card><EmptyState title="No membership card tiers yet" description="No tiers have been returned by the card catalog." /></Card> : <div className="manager-tier-grid">{resource.data.map((tier) => {
      const standard = tier.code === 'STANDARD'
      return <Card key={tier.id} className={`manager-tier${tier.code === 'VIP' ? ' manager-tier--featured' : ''}`}>
        <p className="eyebrow">{standard ? 'Account access' : tier.code === 'VIP' ? 'Premium membership' : 'Membership card'}</p>
        <div className="manager-tier-heading"><h2>{tier.name}</h2><StatusBadge status={tier.isActive ? 'ACTIVE' : 'INACTIVE'} /></div>
        <p className="manager-tier-price">{standard ? 'Free' : formatMoney(tier.price)}</p>
        <p className="manager-muted">{standard ? 'No paid membership card' : tier.durationMonths ? `${tier.durationMonths} months validity` : 'Validity not provided'}</p>
        <ul className="manager-benefits"><li>{tier.discountPercentage ?? 0}% off eligible sport packages</li><li>{standard ? 'Pay standard sport prices' : 'Self-training and coach-led packages'}</li><li>{standard ? 'No expiry for account access' : 'Single sessions excluded'}</li></ul>
        {tier.description && <p className="manager-muted manager-tier-description">{tier.description}</p>}
        {standard ? <Badge tone="info">Default account tier</Badge> : <Button size="sm" onClick={() => setSelected(tier)}>Edit card benefits</Button>}
      </Card>
    })}</div>)}
    <Card title="Discount policy"><DetailList items={[{ label: 'Eligible services', value: '30-day and 90-day sport packages' }, { label: 'Single-session visits', value: 'Standard price - no card discount' }, { label: 'Discount combinations', value: 'No stacking' }]} /><p className="notice notice--info">A membership card provides discounts. Sport packages are purchased separately. Standard stays free, active and without a discount.</p></Card>
    {selected && <EditMembershipCardModal tier={selected} onClose={() => setSelected(null)} onSuccess={() => { setSelected(null); toast.success('Card benefits saved'); reload() }} />}
  </div>
}
