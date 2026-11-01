import { useCallback, useState } from 'react'
import { managerUsersApi } from '../../api/managerUsersApi'
import { humanizeCode } from '../../utils/format'
import { Button, EmptyState, Input, Modal, Pagination, Spinner } from '../../components/ui'
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
  return <Modal open title="Choose an actor" description="Filter by actor name and role. Contact details are not shown." onClose={onClose} size="lg">
    <div className="manager-form manager-activity__actor-picker">
      <Input label="Search actors" type="search" value={search} placeholder="Search actor name" onChange={(event) => { setSearch(event.target.value); setPage(0) }} />
      {resource.status === 'error' ? <ManagerError error={resource.error} onRetry={reload} /> : !ready ? <div className="manager-activity__actor-loading"><Spinner label="Loading actors" /></div> : resource.data.content.length === 0 ? <EmptyState title="No matching actors" description="Try another name or clear the search." variant="plain" /> : <>
        <ul className="manager-activity__actor-list" aria-label="Actor accounts">
          {resource.data.content.map((user: ManagerUser) => <li key={user.id}>
            <button type="button" className="manager-activity__actor-option" onClick={() => onSelect(user)} aria-label={`Use ${user.fullName}`}>
              <span className="manager-activity__actor-copy"><strong>{user.fullName}</strong><span>{humanizeCode(user.role)}</span></span>
              <span className="manager-activity__actor-select" aria-hidden="true">Select</span>
            </button>
          </li>)}
        </ul>
        <Pagination page={page} pageSize={20} totalItems={resource.data.totalElements} onPageChange={setPage} />
      </>}
      <div className="manager-form-actions"><Button variant="secondary" onClick={() => onSelect(null)}>All actors</Button><Button variant="ghost" onClick={onClose}>Cancel</Button></div>
    </div>
  </Modal>
}
