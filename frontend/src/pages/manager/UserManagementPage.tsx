import '@fontsource/be-vietnam-pro/700.css'
import '@fontsource/be-vietnam-pro/800.css'
import { useCallback, useState } from 'react'
import { managerUsersApi } from '../../api/managerUsersApi'
import { useAuth } from '../../auth/useAuth'
import { ROLE_LABELS } from '../../auth/roles'
import { Badge, Button, Input, Modal, PageHeader, Select, StatusBadge, Table, useToast, type BadgeTone, type TableColumn } from '../../components/ui'
import type { ManagerUser } from '../../types/manager'
import { formatManagerDateTime } from './managerFormat'
import { AddStaffAccountModal } from './AddStaffAccountModal'
import { CoachProfileOverflowAction } from './CoachProfileOverflowAction'
import { EditCoachProfileModal } from './EditCoachProfileModal'
import { ConfirmationModal, DetailList, ManagerError, PreviewNotice } from './ManagerShared'
import { useDebounced, useResource } from './useManagerTools'
import '../../styles/managerUsers.css'

const ROLE_TONES: Partial<Record<keyof typeof ROLE_LABELS, BadgeTone>> = {
  MANAGER: 'warning',
  COACH: 'info',
  RECEPTIONIST: 'success',
  MEMBER: 'neutral',
}

function userInitials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map((part) => part[0] ?? '').join('').toUpperCase() || 'U'
}

export function UserManagementPage() {
  const { user } = useAuth()
  const toast = useToast()
  const [search, setSearch] = useState('')
  const debounced = useDebounced(search)
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [adding, setAdding] = useState(false)
  const [selected, setSelected] = useState<ManagerUser | null>(null)
  const [changing, setChanging] = useState<ManagerUser | null>(null)
  const [editingCoach, setEditingCoach] = useState<ManagerUser | null>(null)
  const load = useCallback((signal: AbortSignal) => managerUsersApi.list({ page, size: 20, role, status, search: debounced.trim() }, signal), [page, role, status, debounced])
  const [resource, reload] = useResource(load)
  const ready = resource.status === 'ready' && search === debounced
  const countLabel = ready
    ? resource.data.totalElements + (resource.data.totalElements === 1 ? ' account found' : ' accounts found')
    : resource.status === 'error' ? 'Account count unavailable' : 'Loading accounts'
  const changed = () => { setChanging(null); toast.success('Account status updated'); reload() }

  const columns: TableColumn<ManagerUser>[] = [
    {
      key: 'fullName',
      header: 'Account',
      width: '30%',
      render: (row) => <div className="manager-user-identity">
        <span className="manager-user-avatar" aria-hidden="true">{userInitials(row.fullName)}</span>
        <span className="manager-user-copy">
          <strong>{row.fullName || 'Unnamed account'}</strong>
          <span>{row.email}</span>
        </span>
      </div>,
    },
    {
      key: 'role',
      header: 'Role',
      width: '15%',
      render: (row) => <Badge tone={ROLE_TONES[row.role as keyof typeof ROLE_LABELS] ?? 'neutral'}>
        {ROLE_LABELS[row.role as keyof typeof ROLE_LABELS] ?? row.role}
      </Badge>,
    },
    { key: 'phone', header: 'Phone', width: '14%' },
    { key: 'status', header: 'Status', width: '13%', render: (row) => <StatusBadge status={row.status} /> },
    {
      key: 'actions',
      header: 'Actions',
      width: '28%',
      render: (row) => {
        const isOwnActiveAccount = row.id === user?.id && row.status === 'ACTIVE'
        return <div className="manager-row-actions manager-users-row-actions">
          <Button variant="secondary" size="sm" onClick={() => setSelected(row)}>View profile</Button>
          <Button
            variant={row.status === 'ACTIVE' ? 'danger' : 'secondary'}
            size="sm"
            disabled={isOwnActiveAccount}
            title={isOwnActiveAccount ? 'You cannot deactivate your own account.' : undefined}
            onClick={() => setChanging(row)}
          >
            {row.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
          </Button>
          {row.role === 'COACH' && <CoachProfileOverflowAction account={row} onEdit={setEditingCoach} />}
        </div>
      },
    },
  ]

  return <div className="page manager-page manager-users-screen">
    <div className="manager-users-header">
      <PageHeader
        title="User Management"
        description="Manage staff access and keep your center's account directory up to date."
        actions={<Button variant="primary" onClick={() => setAdding(true)}>+ Add staff</Button>}
      />
    </div>
    <PreviewNotice />
    <section className="manager-users-directory" aria-labelledby="manager-users-directory-title">
      <div className="manager-users-directory__heading">
        <div>
          <h2 id="manager-users-directory-title">Center accounts</h2>
        </div>
        <span className="manager-users-count" aria-live="polite">{countLabel}</span>
      </div>
      <div className="manager-filters manager-users-filters">
        <Input
          label="Search accounts"
          type="search"
          placeholder="Search name, email or phone"
          value={search}
          onChange={(event) => { setSearch(event.target.value); setPage(0) }}
        />
        <Select
          label="Role"
          placeholder="All roles"
          options={Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }))}
          value={role}
          onChange={(event) => { setRole(event.target.value); setPage(0) }}
        />
        <Select
          label="Account status"
          placeholder="All statuses"
          options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]}
          value={status}
          onChange={(event) => { setStatus(event.target.value); setPage(0) }}
        />
        <Button variant="ghost" onClick={() => { setSearch(''); setRole(''); setStatus(''); setPage(0) }}>
          Clear filters
        </Button>
      </div>
      {resource.status === 'error'
        ? <ManagerError error={resource.error} onRetry={reload} />
        : <Table
          columns={columns}
          rows={ready ? resource.data.content : []}
          rowKey={(row) => row.id}
          loading={!ready}
          caption="Center accounts"
          scrollLabel="Scrollable center accounts table"
          emptyTitle={search || role || status ? 'No matching accounts' : 'No accounts yet'}
          emptyDescription="Try a different search or add a staff account."
          pagination={ready ? { page, pageSize: 20, totalItems: resource.data.totalElements, onPageChange: setPage } : undefined}
        />}
      <p className="manager-footnote">Account status controls login and is separate from membership or payment status. Managers cannot deactivate their own account.</p>
    </section>
    {adding && <AddStaffAccountModal onClose={() => setAdding(false)} onSuccess={() => { setAdding(false); toast.success('Staff account created', 'The account is active. Your current filters have been preserved.'); reload() }} />}
    {changing && <ConfirmationModal
      title={changing.status === 'ACTIVE' ? 'Deactivate account' : 'Activate account'}
      description={(changing.status === 'ACTIVE' ? 'Deactivate ' : 'Activate ') + changing.fullName + "'s account? " + (changing.status === 'ACTIVE' ? 'They will no longer be able to log in.' : 'They will be able to log in again.')}
      onClose={() => setChanging(null)}
      save={() => managerUsersApi.changeStatus(changing.id, changing.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')}
      onSuccess={changed}
      confirmLabel={changing.status === 'ACTIVE' ? 'Deactivate account' : 'Activate account'}
      danger={changing.status === 'ACTIVE'}
    />}
    {editingCoach && <EditCoachProfileModal
      userId={editingCoach.id}
      name={editingCoach.fullName}
      onClose={() => setEditingCoach(null)}
      onSuccess={() => { setEditingCoach(null); toast.success('Coach profile updated', 'The Home page shows the new photo and details.') }}
    />}
    {selected && <Modal
      open
      title="Account profile"
      description="Account information from the current user list."
      onClose={() => setSelected(null)}
      footer={<Button variant="secondary" onClick={() => setSelected(null)}>Close</Button>}
    >
      <DetailList items={[
        { label: 'Full name', value: selected.fullName },
        { label: 'Email', value: selected.email },
        { label: 'Phone', value: selected.phone },
        { label: 'Role', value: ROLE_LABELS[selected.role as keyof typeof ROLE_LABELS] ?? selected.role },
        { label: 'Status', value: <StatusBadge status={selected.status} /> },
        { label: 'Created', value: formatManagerDateTime(selected.createdAt) },
      ]} />
    </Modal>}
  </div>
}
