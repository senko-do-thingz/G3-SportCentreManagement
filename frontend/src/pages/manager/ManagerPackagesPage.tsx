import { useCallback, useState } from 'react'
import { managerPackagesApi } from '../../api/managerPackagesApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, Card, PageHeader, Select, StatusBadge, Table, useToast, type TableColumn } from '../../components/ui'
import { formatMoney } from '../../utils/format'
import type { ManagerPackage } from '../../types/manager'
import { ConfirmationModal, ManagerError, PreviewNotice } from './ManagerShared'
import { PackageEditorModal } from './PackageEditorModal'
import { useResource } from './useManagerTools'

const loadSports = (signal: AbortSignal) => packagesApi.sports({ signal })

export function ManagerPackagesPage() {
  const toast = useToast()
  const [sportId, setSportId] = useState('')
  const [format, setFormat] = useState('')
  const [active, setActive] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<ManagerPackage | 'create' | null>(null)
  const [changing, setChanging] = useState<ManagerPackage | null>(null)
  const [sports, retrySports] = useResource(loadSports)
  const load = useCallback((signal: AbortSignal) => managerPackagesApi.list({ sportId, format, active }, signal), [sportId, format, active])
  const [resource, reload] = useResource(load)
  function filter(setter: (value: string) => void, value: string) { setter(value); setPage(0) }
  function saved() { setEditing(null); setChanging(null); toast.success('Sport package saved', 'Existing paid registrations keep their original price and benefits. Your filters have been preserved.'); reload() }
  const columns: TableColumn<ManagerPackage>[] = [
    { key: 'name', header: 'Package', render: (row) => <div className="manager-cell"><strong>{row.sportName}</strong><span>{row.name} - {row.code}</span></div> },
    { key: 'trainingFormat', header: 'Training type', render: (row) => row.trainingFormat === 'COACH_LED' ? 'Coach-led' : 'Self-training' },
    { key: 'durationDays', header: 'Validity / sessions', render: (row) => <div className="manager-cell"><span className="manager-cell-primary">{row.durationDays === 1 ? 'Selected session' : `${row.durationDays} days`}</span><span>{row.sessionCount ?? '-'} {row.sessionCount === 1 ? 'session' : 'sessions'} - {row.sessionMinutes ?? '-'} min each</span></div> },
    { key: 'priceAmount', header: 'Price', render: (row) => <strong className="manager-price">{formatMoney(row.priceAmount)}</strong> },
    { key: 'isActive', header: 'Status', render: (row) => <StatusBadge status={row.isActive ? 'ACTIVE' : 'INACTIVE'} /> },
    { key: 'actions', header: 'Actions', render: (row) => <div className="manager-row-actions"><Button variant="secondary" size="sm" onClick={() => setEditing(row)}>Edit</Button><Button variant={row.isActive ? 'danger' : 'secondary'} size="sm" disabled={row.sessionCount === null || ![60, 90].includes(row.sessionMinutes ?? 0)} onClick={() => setChanging(row)}>{row.isActive ? 'Deactivate' : 'Activate'}</Button></div> },
  ]
  return <div className="page manager-page">
    <PageHeader eyebrow="Center management" title="Sport Packages & Pricing" description="Separate prices for each sport and training option." actions={<Button disabled={sports.status !== 'ready' || !sports.data.length} onClick={() => setEditing('create')}>+ Create package</Button>} />
    <PreviewNotice />
    <Card title="Sport packages">
      <div className="manager-filters">
        <Select label="Sport" placeholder="All sports" options={sports.status === 'ready' ? sports.data.map((sport) => ({ value: String(sport.id), label: sport.name })) : []} disabled={sports.status !== 'ready'} value={sportId} onChange={(event) => filter(setSportId, event.target.value)} />
        <Select label="Training type" placeholder="All training types" options={[{ value: 'SELF_TRAINING', label: 'Self-training' }, { value: 'COACH_LED', label: 'Coach-led' }]} value={format} onChange={(event) => filter(setFormat, event.target.value)} />
        <Select label="Status" placeholder="All statuses" options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]} value={active} onChange={(event) => filter(setActive, event.target.value)} />
        <Button variant="ghost" onClick={() => { setSportId(''); setFormat(''); setActive(''); setPage(0) }}>Clear filters</Button>
      </div>
      {sports.status === 'error' && <ManagerError error={sports.error} onRetry={retrySports} />}
      {resource.status === 'error' ? <ManagerError error={resource.error} onRetry={reload} /> : <Table columns={columns} rows={resource.status === 'ready' ? resource.data.slice(page * 20, (page + 1) * 20) : []} rowKey={(row) => row.id} loading={resource.status === 'loading'} caption="Sport packages" emptyTitle={sportId || format || active ? 'No matching packages' : 'No sport packages yet'} emptyDescription="Clear filters or create a package for an available sport." pagination={resource.status === 'ready' ? { page, pageSize: 20, totalItems: resource.data.length, onPageChange: setPage } : undefined} />}
    </Card>
    <p className="notice notice--info">Each package applies to one sport. Existing paid registrations retain their original price and benefits. The catalog is paged locally.</p>
    {editing !== null && sports.status === 'ready' && <PackageEditorModal item={editing === 'create' ? undefined : editing} sports={sports.data} onClose={() => setEditing(null)} onSuccess={saved} />}
    {changing && <ConfirmationModal title={changing.isActive ? 'Deactivate package' : 'Activate package'} description={`${changing.isActive ? 'Deactivate' : 'Activate'} ${changing.name}? Existing paid registrations will keep their benefits.`} onClose={() => setChanging(null)} save={() => managerPackagesApi.update(changing.id, { name: changing.name, durationDays: changing.durationDays, sessionCount: changing.sessionCount!, sessionMinutes: changing.sessionMinutes!, priceAmount: Number(changing.priceAmount), isActive: !changing.isActive })} onSuccess={saved} confirmLabel={changing.isActive ? 'Deactivate package' : 'Activate package'} danger={Boolean(changing.isActive)} />}
  </div>
}
