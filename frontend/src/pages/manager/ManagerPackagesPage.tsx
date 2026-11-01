import { useCallback, useMemo, useState } from 'react'
import { managerPackagesApi } from '../../api/managerPackagesApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, Card, Input, PageHeader, Select, StatusBadge, Table, useToast, type TableColumn } from '../../components/ui'
import { formatMoney } from '../../utils/format'
import type { ManagerPackage } from '../../types/manager'
import { ConfirmationModal, ManagerError, PreviewNotice } from './ManagerShared'
import { PackageEditorModal } from './PackageEditorModal'
import { useResource } from './useManagerTools'

const PAGE_SIZE = 20
const loadSports = (signal: AbortSignal) => packagesApi.sports({ signal })

function packageStatus(value: boolean | null) {
  return value === null ? null : value ? 'ACTIVE' : 'INACTIVE'
}

export function ManagerPackagesPage() {
  const toast = useToast()
  const [sportId, setSportId] = useState('')
  const [format, setFormat] = useState('')
  const [active, setActive] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<ManagerPackage | 'create' | null>(null)
  const [changing, setChanging] = useState<ManagerPackage | null>(null)
  const [sports, retrySports] = useResource(loadSports)
  const load = useCallback((signal: AbortSignal) => managerPackagesApi.list({ sportId, format, active }, signal), [sportId, format, active])
  const [resource, reload] = useResource(load)

  function filter(setter: (value: string) => void, value: string) {
    setter(value)
    setPage(0)
  }

  function saved() {
    setEditing(null)
    setChanging(null)
    toast.success('Sport package saved', 'Existing paid registrations keep their original price and benefits. Your filters have been preserved.')
    reload()
  }

  const rows = useMemo(() => {
    if (resource.status !== 'ready') return []
    const term = search.trim().toLocaleLowerCase()
    if (!term) return resource.data
    return resource.data.filter((item) => [item.name, item.code, item.sportName].some((value) => value.toLocaleLowerCase().includes(term)))
  }, [resource, search])

  const columns: TableColumn<ManagerPackage>[] = [
    {
      key: 'name', header: 'Package', width: '24%',
      render: (row) => <div className="manager-catalog__identity">
        <span className="manager-catalog__label">Package</span>
        <strong>{row.name}</strong>
        <span className="manager-catalog__secondary">{row.sportName} <span aria-hidden="true">·</span> {row.code}</span>
      </div>,
    },
    {
      key: 'trainingFormat', header: 'Training type', width: '15%',
      render: (row) => <div className="manager-catalog__cell">
        <span className="manager-catalog__label">Training type</span>
        <span>{row.trainingFormat === 'COACH_LED' ? 'Coach-led' : 'Self-training'}</span>
      </div>,
    },
    {
      key: 'durationDays', header: 'Package terms', width: '21%',
      render: (row) => <div className="manager-catalog__cell">
        <span className="manager-catalog__label">Package terms</span>
        <strong>{row.durationDays === 1 ? 'Single visit' : row.durationDays + ' days'}</strong>
        <span className="manager-catalog__secondary">{row.sessionCount ?? 'Not provided'} {row.sessionCount === 1 ? 'session' : 'sessions'} <span aria-hidden="true">·</span> {row.sessionMinutes ?? 'Not provided'} min each</span>
      </div>,
    },
    {
      key: 'priceAmount', header: 'Price', align: 'right', width: '15%',
      render: (row) => <div className="manager-catalog__cell manager-catalog__cell--amount">
        <span className="manager-catalog__label">Price</span>
        <strong className="manager-catalog__amount">{formatMoney(row.priceAmount)}</strong>
      </div>,
    },
    {
      key: 'isActive', header: 'Status', width: '10%',
      render: (row) => <div className="manager-catalog__cell">
        <span className="manager-catalog__label">Status</span>
        <StatusBadge status={packageStatus(row.isActive)} />
      </div>,
    },
    {
      key: 'actions', header: 'Actions', width: '15%',
      render: (row) => {
        const canChangeStatus = row.isActive !== null && row.sessionCount !== null && row.sessionMinutes !== null && [60, 90].includes(row.sessionMinutes)
        return <div className="manager-catalog__cell manager-catalog__actions">
          <span className="manager-catalog__label">Actions</span>
          <div className="manager-row-actions">
            <Button variant="secondary" size="sm" onClick={() => setEditing(row)}>Edit</Button>
            <Button variant={row.isActive ? 'danger' : 'secondary'} size="sm" disabled={!canChangeStatus} title={!canChangeStatus ? 'Status cannot be changed until the package data is complete.' : undefined} onClick={() => setChanging(row)}>
              {row.isActive ? 'Deactivate' : 'Activate'}
            </Button>
          </div>
        </div>
      },
    },
  ]

  return <div className="page manager-page manager-catalog manager-catalog--packages">
    <PageHeader
      title="Sport packages"
      description="Manage package prices and training terms for each sport."
      actions={<Button disabled={sports.status !== 'ready' || !sports.data.length} onClick={() => setEditing('create')}>+ Create package</Button>}
    />
    <PreviewNotice />
    <Card className="manager-catalog__panel" title="Package catalog" actions={<span className="manager-catalog__count">{resource.status === 'ready' ? rows.length + (rows.length === 1 ? ' package' : ' packages') : 'Loading catalog'}</span>}>
      <div className="manager-catalog__filters manager-filters">
        <Input label="Search packages" type="search" value={search} placeholder="Name, code or sport" hint="Search the loaded catalog." onChange={(event) => { setSearch(event.target.value); setPage(0) }} />
        <Select label="Sport" placeholder="All sports" options={sports.status === 'ready' ? sports.data.map((sport) => ({ value: String(sport.id), label: sport.name })) : []} disabled={sports.status !== 'ready'} value={sportId} onChange={(event) => filter(setSportId, event.target.value)} />
        <Select label="Training type" placeholder="All training types" options={[{ value: 'SELF_TRAINING', label: 'Self-training' }, { value: 'COACH_LED', label: 'Coach-led' }]} value={format} onChange={(event) => filter(setFormat, event.target.value)} />
        <Select label="Status" placeholder="All statuses" options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]} value={active} onChange={(event) => filter(setActive, event.target.value)} />
        <Button variant="ghost" onClick={() => { setSearch(''); setSportId(''); setFormat(''); setActive(''); setPage(0) }}>Clear filters</Button>
      </div>
      {sports.status === 'error' && <div className="manager-catalog__inline-error"><ManagerError error={sports.error} onRetry={retrySports} /></div>}
      {resource.status === 'error'
        ? <div className="manager-catalog__inline-error"><ManagerError error={resource.error} onRetry={reload} /></div>
        : <div className="manager-catalog__table">
          <Table
            columns={columns}
            rows={rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)}
            rowKey={(row) => row.id}
            loading={resource.status === 'loading'}
            caption="Sport package catalog"
            scrollLabel="Sport package catalog"
            emptyTitle={search || sportId || format || active ? 'No matching packages' : 'No sport packages yet'}
            emptyDescription="Adjust the search or filters, or create a package for an available sport."
            pagination={resource.status === 'ready' ? { page, pageSize: PAGE_SIZE, totalItems: rows.length, onPageChange: setPage } : undefined}
          />
        </div>
      }
      <div className="manager-catalog__policy">
        <span className="manager-catalog__policy-mark" aria-hidden="true">i</span>
        <p>Package edits affect new registrations. Existing paid registrations keep the price and benefits they were purchased with.</p>
      </div>
    </Card>
    {editing !== null && sports.status === 'ready' && <PackageEditorModal item={editing === 'create' ? undefined : editing} sports={sports.data} onClose={() => setEditing(null)} onSuccess={saved} />}
    {changing && <ConfirmationModal
      title={changing.isActive ? 'Deactivate package' : 'Activate package'}
      description={(changing.isActive ? 'Deactivate ' : 'Activate ') + changing.name + '? Existing paid registrations will keep their benefits.'}
      onClose={() => setChanging(null)}
      save={() => managerPackagesApi.update(changing.id, { name: changing.name, durationDays: changing.durationDays, sessionCount: changing.sessionCount!, sessionMinutes: changing.sessionMinutes!, price: Number(changing.priceAmount), active: changing.isActive === false })}
      onSuccess={saved}
      confirmLabel={changing.isActive ? 'Deactivate package' : 'Activate package'}
      danger={Boolean(changing.isActive)}
    />}
  </div>
}
