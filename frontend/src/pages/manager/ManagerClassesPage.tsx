import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { managerClassesApi, type ClassLevel, type ManagerClass } from '../../api/managerClassesApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, Card, Input, PageHeader, Select, StatusBadge, Table, useToast, type TableColumn } from '../../components/ui'
import { ConfirmationModal, ManagerError, PreviewNotice } from './ManagerShared'
import { useDebounced, useResource } from './useManagerTools'

const sportsLoader = (signal: AbortSignal) => packagesApi.sports({ signal })
const coachesLoader = (signal: AbortSignal) => managerClassesApi.coaches(undefined, signal)
const levelOptions = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'].map((value) => ({ value, label: value[0] + value.slice(1).toLowerCase() }))

export function ManagerClassesPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [search, setSearch] = useState('')
  const debounced = useDebounced(search)
  const [sportId, setSportId] = useState('')
  const [coachId, setCoachId] = useState('')
  const [level, setLevel] = useState('')
  const [active, setActive] = useState('')
  const [page, setPage] = useState(0)
  const [changing, setChanging] = useState<ManagerClass | null>(null)
  const [sports, retrySports] = useResource(sportsLoader)
  const [coaches, retryCoaches] = useResource(coachesLoader)
  const loader = useCallback((signal: AbortSignal) => managerClassesApi.list({ search: debounced.trim(), sportId: sportId ? Number(sportId) : undefined, coachId: coachId ? Number(coachId) : undefined, level: level as ClassLevel || undefined, active: active ? active === 'true' : undefined, page, size: 20 }, signal), [debounced, sportId, coachId, level, active, page])
  const [classes, reload] = useResource(loader)
  function filter(set: (value: string) => void, value: string) { set(value); setPage(0) }
  const columns: TableColumn<ManagerClass>[] = [
    { key: 'name', header: 'Class / Sport', render: (row) => <div className="manager-cell"><strong>{row.name}</strong><span>{row.code} - {row.sportName}</span></div> },
    { key: 'level', header: 'Level / Age group', render: (row) => <div className="manager-cell"><span>{row.level}</span><span>{row.ageGroupLabel}</span></div> },
    { key: 'coachName', header: 'Coach', render: (row) => row.coachName },
    { key: 'maxMembers', header: 'Capacity', render: (row) => `${row.maxMembers} members` },
    { key: 'active', header: 'Status', render: (row) => <StatusBadge status={row.active ? 'ACTIVE' : 'INACTIVE'} /> },
    { key: 'actions', header: 'Actions', render: (row) => <div className="manager-row-actions"><Button size="sm" variant="secondary" onClick={() => navigate(`/manager/classes/${row.id}/edit`)}>Edit</Button><Button size="sm" variant="secondary" onClick={() => navigate(`/manager/schedule?classId=${row.id}`)}>Schedule</Button><Button size="sm" variant={row.active ? 'danger' : 'secondary'} onClick={() => setChanging(row)}>{row.active ? 'Deactivate' : 'Activate'}</Button></div> },
  ]
  return <div className="page manager-page">
    <PageHeader eyebrow="Class management" title="Class Management" description="Manage classes, coach assignments and weekly schedules." actions={<Button onClick={() => navigate('/manager/classes/new')}>+ Create class</Button>} />
    <PreviewNotice />
    <Card title="Classes">
      <div className="manager-filters">
        <Input label="Search classes" value={search} onChange={(event) => filter(setSearch, event.target.value)} placeholder="Class name or code" />
        <Select label="Sport" placeholder="All sports" value={sportId} options={sports.status === 'ready' ? sports.data.map((item) => ({ value: String(item.id), label: item.name })) : []} onChange={(event) => filter(setSportId, event.target.value)} />
        <Select label="Level" placeholder="All levels" value={level} options={levelOptions} onChange={(event) => filter(setLevel, event.target.value)} />
        <Select label="Coach" placeholder="All coaches" value={coachId} options={coaches.status === 'ready' ? coaches.data.map((item) => ({ value: String(item.id), label: item.fullName })) : []} onChange={(event) => filter(setCoachId, event.target.value)} />
        <Select label="Status" placeholder="All statuses" value={active} options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]} onChange={(event) => filter(setActive, event.target.value)} />
        <Button variant="ghost" onClick={() => { setSearch(''); setSportId(''); setCoachId(''); setLevel(''); setActive(''); setPage(0) }}>Clear filters</Button>
      </div>
      {sports.status === 'error' && <ManagerError error={sports.error} onRetry={retrySports} />}
      {coaches.status === 'error' && <ManagerError error={coaches.error} onRetry={retryCoaches} />}
      {classes.status === 'error' ? <ManagerError error={classes.error} onRetry={reload} /> : <Table caption="Classes" columns={columns} rows={classes.status === 'ready' ? classes.data.content : []} rowKey={(row) => row.id} loading={classes.status === 'loading'} emptyTitle="No matching classes" emptyDescription="Clear filters or create a class to get started." pagination={classes.status === 'ready' ? { page: classes.data.page, pageSize: classes.data.size, totalItems: classes.data.totalElements, onPageChange: setPage } : undefined} />}
    </Card>
    {changing && <ConfirmationModal title={changing.active ? 'Deactivate class' : 'Activate class'} description={`${changing.name}: ${changing.active ? 'Upcoming bookings may prevent deactivation.' : 'Make this class available for scheduling.'}`} onClose={() => setChanging(null)} save={() => managerClassesApi.setActive(changing.id, !changing.active)} onSuccess={() => { setChanging(null); reload(); toast.success('Class status updated') }} confirmLabel={changing.active ? 'Deactivate class' : 'Activate class'} danger={changing.active} />}
  </div>
}
