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

  return <Modal open title="Choose an actor" description="Search actors by name. Roles are shown for context; contact details are not shown." onClose={onClose} size="lg">
    <div className="manager-form manager-activity__actor-picker">
      <Input label="Search actors" type="search" value={search} placeholder="Search actor name" onChange={(event) => { setSearch(event.target.value); setPage(0) }} />
      {resource.status === 'error' && search === debounced ? <ManagerError error={resource.error} onRetry={reload} /> : !ready ? <div className="manager-activity__actor-loading"><Spinner label="Loading actors" /></div> : <>
        {resource.data.content.length === 0
          ? <EmptyState title="No matching actors" description={resource.data.totalElements > 0 ? 'No actors are available on this page. Use the pagination controls to move between pages.' : 'Try another name or clear the search.'} variant="plain" />
          : <ul className="manager-activity__actor-list" aria-label="Actor accounts">
            {resource.data.content.map((user: ManagerUser) => <li key={user.id}>
              <button type="button" className="manager-activity__actor-option" onClick={() => onSelect(user)} aria-label={`Use ${user.fullName}`}>
                <span className="manager-activity__actor-copy"><strong>{user.fullName}</strong><span>{humanizeCode(user.role)}</span></span>
                <span className="manager-activity__actor-select" aria-hidden="true">Select</span>
              </button>
            </li>)}
          </ul>}
        {resource.data.totalElements > 0 && (page >= Math.ceil(resource.data.totalElements / 20)
          ? <nav className="pagination" aria-label="Pagination">
            <p className="pagination__summary">The actor list changed while this page was open.</p>
            <div className="pagination__controls">
              <Button variant="secondary" size="sm" onClick={() => setPage(Math.max(0, Math.ceil(resource.data.totalElements / 20) - 1))}>
                Return to page {Math.ceil(resource.data.totalElements / 20)}
              </Button>
            </div>
          </nav>
          : <Pagination page={page} pageSize={20} totalItems={resource.data.totalElements} onPageChange={setPage} />)}
      </>}
      <div className="manager-form-actions"><Button variant="secondary" onClick={() => onSelect(null)}>All actors</Button><Button variant="ghost" onClick={onClose}>Cancel</Button></div>
    </div>
  </Modal>
}
