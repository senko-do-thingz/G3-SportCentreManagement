import { useCallback, useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type MouseEvent } from 'react'
import { createPortal } from 'react-dom'
import { managerUsersApi } from '../../api/managerUsersApi'
import { useAuth } from '../../auth/useAuth'
import { ROLE_LABELS } from '../../auth/roles'
import { Badge, Button, Card, Input, Modal, PageHeader, Select, StatusBadge, Table, useToast, type BadgeTone, type TableColumn } from '../../components/ui'
import type { ManagerUser } from '../../types/manager'
import { formatManagerDateTime } from './managerFormat'
import { AddStaffAccountModal } from './AddStaffAccountModal'
import { EditCoachProfileModal } from './EditCoachProfileModal'
import { ConfirmationModal, DetailList, ManagerError, PreviewNotice } from './ManagerShared'
import { useDebounced, useResource } from './useManagerTools'

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
  const [coachMenu, setCoachMenu] = useState<{ user: ManagerUser; top: number; left: number } | null>(null)
  const coachMenuRef = useRef<HTMLDivElement>(null)
  const coachMenuItemRef = useRef<HTMLButtonElement>(null)
  const coachMenuTriggerRef = useRef<HTMLButtonElement>(null)
  const load = useCallback((signal: AbortSignal) => managerUsersApi.list({ page, size: 20, role, status, search: debounced.trim() }, signal), [page, role, status, debounced])
  const [resource, reload] = useResource(load)
  const ready = resource.status === 'ready' && search === debounced
  const matchingAccounts = ready ? resource.data.totalElements : null
  const menuAccountIsVisible = Boolean(coachMenu && ready && resource.status === 'ready' && resource.data.content.some((account) => account.id === coachMenu.user.id && account.role === 'COACH'))
  const changed = () => { setChanging(null); setCoachMenu(null); toast.success('Account status updated'); reload() }
  function toggleCoachMenu(event: MouseEvent<HTMLButtonElement>, account: ManagerUser) {
    if (coachMenu?.user.id === account.id && menuAccountIsVisible) {
      setCoachMenu(null)
      return
    }
    const bounds = event.currentTarget.getBoundingClientRect()
    const menuWidth = 192
    const menuHeight = 48
    const maxTop = Math.max(8, window.innerHeight - menuHeight - 8)
    const preferredTop = bounds.bottom + menuHeight + 8 <= window.innerHeight ? bounds.bottom + 4 : bounds.top - menuHeight - 4
    const top = Math.max(8, Math.min(preferredTop, maxTop))
    const maxLeft = Math.max(8, window.innerWidth - menuWidth - 8)
    const left = Math.max(8, Math.min(bounds.right - menuWidth, maxLeft))
    coachMenuTriggerRef.current = event.currentTarget
    setCoachMenu({ user: account, top, left })
  }
  function handleCoachMenuKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      coachMenuItemRef.current?.focus()
      return
    }
    if (event.key !== 'Tab') return

    const trigger = coachMenuTriggerRef.current
    if (!trigger) return
    const focusable = Array.from(document.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    )).filter((element) => {
      if (coachMenuRef.current?.contains(element) || element.closest('[hidden]') || element.tabIndex < 0) return false
      const style = window.getComputedStyle(element)
      return style.display !== 'none' && style.visibility !== 'hidden'
    })
    const nextIndex = focusable.indexOf(trigger) + 1
    const next = event.shiftKey ? trigger : focusable[nextIndex] ?? trigger
    event.preventDefault()
    setCoachMenu(null)
    next.focus()
  }
  useEffect(() => {
    if (!coachMenu) return
    coachMenuItemRef.current?.focus()
    const dismissOutside = (event: PointerEvent) => {
      const target = event.target
      if (target instanceof Node && !coachMenuRef.current?.contains(target) && !coachMenuTriggerRef.current?.contains(target)) setCoachMenu(null)
    }
    const dismiss = () => setCoachMenu(null)
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setCoachMenu(null)
        coachMenuTriggerRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', dismissOutside)
    document.addEventListener('keydown', handleKeyDown)
    window.addEventListener('resize', dismiss)
    window.addEventListener('scroll', dismiss, true)
    return () => {
      document.removeEventListener('pointerdown', dismissOutside)
      document.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('resize', dismiss)
      window.removeEventListener('scroll', dismiss, true)
    }
  }, [coachMenu])
  const columns: TableColumn<ManagerUser>[] = [
    { key: 'fullName', header: 'Account', width: '27%', render: (row) => <div className="manager-user-identity"><span className="manager-user-avatar" aria-hidden="true">{userInitials(row.fullName)}</span><span className="manager-user-copy"><strong>{row.fullName}</strong><span>{row.email}</span></span></div> },
    { key: 'role', header: 'Role', width: '15%', render: (row) => <Badge tone={ROLE_TONES[row.role as keyof typeof ROLE_LABELS] ?? 'neutral'}>{ROLE_LABELS[row.role as keyof typeof ROLE_LABELS] ?? row.role}</Badge> },
    { key: 'phone', header: 'Phone' },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
    { key: 'actions', header: 'Actions', width: '32%', render: (row) => <div className="manager-row-actions">
      <Button variant="secondary" size="sm" onClick={() => setSelected(row)}>View profile</Button>
      <Button variant={row.status === 'ACTIVE' ? 'danger' : 'secondary'} size="sm" disabled={row.id === user?.id && row.status === 'ACTIVE'} title={row.id === user?.id ? 'You cannot deactivate your own account.' : undefined} onClick={() => setChanging(row)}>{row.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}</Button>
      {row.role === 'COACH' && <Button variant="ghost" size="sm" className="manager-user-more" aria-label={`More actions for ${row.fullName}`} aria-haspopup="menu" aria-controls={coachMenu?.user.id === row.id ? 'manager-coach-actions-menu' : undefined} aria-expanded={coachMenu?.user.id === row.id} title="More actions" onClick={(event) => toggleCoachMenu(event, row)}>
        <svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="10" cy="4" r="1.5" /><circle cx="10" cy="10" r="1.5" /><circle cx="10" cy="16" r="1.5" /></svg>
      </Button>}
    </div> },
  ]
  return <div className="page manager-page manager-users-page">
    <section className="manager-users-hero" aria-label="User management">
      <PageHeader eyebrow="Center management / Access & team" title="User Management" description="Manage staff access and keep your center's account directory up to date." actions={<Button variant="primary" className="manager-users-add-staff" onClick={() => setAdding(true)}>+ Add staff</Button>} />
      <div className="manager-users-hero-note"><span>STAFF DIRECTORY</span><span>Review account roles, contact details and access status.</span></div>
    </section>
    <PreviewNotice />
    <Card className="manager-users-directory" title="Center accounts" actions={<span className="manager-users-count" aria-live="polite">{matchingAccounts === null ? 'Loading accounts' : `${matchingAccounts} ${matchingAccounts === 1 ? 'account' : 'accounts'} found`}</span>}>
      <div className="manager-filters manager-users-filters">
        <Input label="Search accounts" type="search" placeholder="Search name, email or phone" value={search} onChange={(event) => { setCoachMenu(null); setSearch(event.target.value); setPage(0) }} />
        <Select label="Role" placeholder="All roles" options={Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }))} value={role} onChange={(event) => { setCoachMenu(null); setRole(event.target.value); setPage(0) }} />
        <Select label="Account status" placeholder="All statuses" options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} value={status} onChange={(event) => { setCoachMenu(null); setStatus(event.target.value); setPage(0) }} />
        <Button variant="ghost" onClick={() => { setCoachMenu(null); setSearch(''); setRole(''); setStatus(''); setPage(0) }}>Clear filters</Button>
      </div>
      {resource.status === 'error' ? <ManagerError error={resource.error} onRetry={reload} /> : <Table columns={columns} rows={ready ? resource.data.content : []} rowKey={(row) => row.id} loading={!ready} caption="Center accounts" emptyTitle={search || role || status ? 'No matching accounts' : 'No accounts yet'} emptyDescription="Try a different search or add a staff account." pagination={ready ? { page, pageSize: 20, totalItems: resource.data.totalElements, onPageChange: (nextPage) => { setCoachMenu(null); setPage(nextPage) } } : undefined} />}
      <p className="manager-footnote">Account status controls login and is separate from membership or payment status. Managers cannot deactivate their own account.</p>
    </Card>
    {adding && <AddStaffAccountModal onClose={() => setAdding(false)} onSuccess={() => { setAdding(false); toast.success('Staff account created', 'The account is active. Your current filters have been preserved.'); reload() }} />}
    {changing && <ConfirmationModal title={changing.status === 'ACTIVE' ? 'Deactivate account' : 'Activate account'} description={`${changing.status === 'ACTIVE' ? 'Deactivate' : 'Activate'} ${changing.fullName}'s account? ${changing.status === 'ACTIVE' ? 'They will no longer be able to log in.' : 'They will be able to log in again.'}`} onClose={() => setChanging(null)} save={() => managerUsersApi.changeStatus(changing.id, changing.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')} onSuccess={changed} confirmLabel={changing.status === 'ACTIVE' ? 'Deactivate account' : 'Activate account'} danger={changing.status === 'ACTIVE'} />}
    {editingCoach && <EditCoachProfileModal userId={editingCoach.id} name={editingCoach.fullName} onClose={() => setEditingCoach(null)} onSuccess={() => { setEditingCoach(null); toast.success('Coach profile updated', 'The Home page shows the new photo and details.') }} />}
    {selected && <Modal open title="Account profile" description="Account information from the current user list." onClose={() => setSelected(null)} footer={<Button variant="secondary" onClick={() => setSelected(null)}>Close</Button>}><DetailList items={[{ label: 'Full name', value: selected.fullName }, { label: 'Email', value: selected.email }, { label: 'Phone', value: selected.phone }, { label: 'Role', value: ROLE_LABELS[selected.role as keyof typeof ROLE_LABELS] ?? selected.role }, { label: 'Status', value: <StatusBadge status={selected.status} /> }, { label: 'Created', value: formatManagerDateTime(selected.createdAt) }]} /></Modal>}
    {coachMenu && menuAccountIsVisible && createPortal(<div id="manager-coach-actions-menu" ref={coachMenuRef} className="manager-user-menu" role="menu" aria-label={`More actions for ${coachMenu.user.fullName}`} style={{ top: coachMenu.top, left: coachMenu.left }} onKeyDown={handleCoachMenuKeyDown} onBlur={(event) => { const next = event.relatedTarget; if (!(next instanceof Node) || !event.currentTarget.contains(next)) setCoachMenu(null) }}>
      <button ref={coachMenuItemRef} type="button" role="menuitem" onClick={() => { const account = coachMenu.user; setCoachMenu(null); coachMenuTriggerRef.current?.focus(); setEditingCoach(account) }}>Edit coach profile</button>
    </div>, document.body)}
  </div>
}
