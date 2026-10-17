import { useCallback, useState } from 'react'
import { managerUsersApi } from '../../api/managerUsersApi'
import { humanizeCode } from '../../utils/format'
import { Button, Input, Modal, Table, type TableColumn } from '../../components/ui'
import type { ManagerUser } from '../../types/manager'
import { ManagerError } from './ManagerShared'
import { useDebounced, useResource } from './useManagerTools'

export function ActorPickerModal({ onSelect, onClose }: { onSelect: (user: ManagerUser | null) => void; onClose: () => void }) {
  const [search, setSearch] = useState('')
  const debounced = useDebounced(search)
  const [page, setPage] = useState(0)
  const load = useCallback((signal: AbortSignal) => managerUsersApi.list({ page, size: 20, search: debounced.trim() }, signal), [page, debounced])
  const [resource, reload] = useResource(load)
  const ready = resource.status === 'ready' && search === debounced
  const columns: TableColumn<ManagerUser>[] = [
    { key: 'fullName', header: 'Account', render: (user) => <div className="manager-cell"><strong>{user.fullName}</strong><span>{user.email}</span></div> },
    { key: 'role', header: 'Role', render: (user) => humanizeCode(user.role) },
    { key: 'select', header: 'Select', render: (user) => <Button size="sm" variant="secondary" onClick={() => onSelect(user)} aria-label={`Use ${user.fullName}`}>Select</Button> },
  ]
  return <Modal open title="Choose an actor" description="Search all center accounts. System activity remains available under All actors." onClose={onClose} size="lg">
    <div className="manager-form">
      <Input label="Search actors" type="search" value={search} placeholder="Search name, email or phone" onChange={(event) => { setSearch(event.target.value); setPage(0) }} />
      {resource.status === 'error' ? <ManagerError error={resource.error} onRetry={reload} /> : <Table columns={columns} rows={ready ? resource.data.content : []} rowKey={(user) => user.id} loading={!ready} caption="Actor accounts" emptyTitle="No matching actors" pagination={ready ? { page, pageSize: 20, totalItems: resource.data.totalElements, onPageChange: setPage } : undefined} />}
      <div className="manager-form-actions"><Button variant="secondary" onClick={() => onSelect(null)}>All actors</Button><Button variant="ghost" onClick={onClose}>Cancel</Button></div>
    </div>
  </Modal>
}
