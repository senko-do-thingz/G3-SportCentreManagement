import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { Brand } from '../../components/Brand'
import { Badge, StatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Card, StatCard } from '../../components/ui/Card'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Checkbox } from '../../components/ui/Checkbox'
import { Input } from '../../components/ui/Input'
import { PasswordInput } from '../../components/ui/PasswordInput'
import { Modal } from '../../components/ui/Modal'
import { Notice } from '../../components/ui/Notice'
import { PageHeader } from '../../components/ui/PageHeader'
import { Select } from '../../components/ui/Select'
import { Spinner } from '../../components/ui/Spinner'
import { Table, type TableColumn } from '../../components/ui/Table'
import { Tabs } from '../../components/ui/Tabs'
import { useToast } from '../../components/ui/toastContext'
import { formatDate, formatDateTime, formatMoney, formatTime, humanizeCode } from '../../utils/format'

interface DemoAccount {
  id: number
  name: string
  email: string
  role: string
  phone: string
  status: string
  joinedAt: string
}

const ROLE_CYCLE = ['MEMBER', 'COACH', 'RECEPTIONIST', 'MEMBER', 'MANAGER']
const STATUS_CYCLE = ['ACTIVE', 'ACTIVE', 'PENDING_PAYMENT', 'INACTIVE', 'EXPIRED']

const DEMO_ACCOUNTS: DemoAccount[] = Array.from({ length: 23 }, (_, index) => ({
  id: index + 1,
  name: `Demo User ${String(index + 1).padStart(2, '0')}`,
  email: `user${index + 1}@sportify.demo`,
  role: ROLE_CYCLE[index % ROLE_CYCLE.length],
  phone: `09012345${String(index).padStart(2, '0')}`,
  status: STATUS_CYCLE[index % STATUS_CYCLE.length],
  joinedAt: `2026-0${(index % 9) + 1}-1${index % 9}`,
}))

const PAGE_SIZE = 5

const STATUS_CODES = [
  'ACTIVE',
  'PENDING_PAYMENT',
  'PAID',
  'CONFIRMED',
  'REFUNDED',
  'INACTIVE',
  'EXPIRED',
  'CANCELLED',
  'REJECTED',
]

const COLOR_TOKENS = [
  '--color-primary',
  '--color-primary-soft',
  '--color-navy',
  '--color-accent',
  '--color-highlight',
  '--color-bg',
  '--color-surface',
  '--color-border',
  '--color-text',
  '--color-text-muted',
  '--color-success',
  '--color-warning',
  '--color-danger',
]

/**
 * Living catalogue of the shared UI kit at /ui-kit. Every component built in FE-00 renders here
 * so reviewers and the next tasks can see how to use them.
 */
export function ComponentDemoPage() {
  const toast = useToast()
  const [modalOpen, setModalOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [tableMode, setTableMode] = useState<'data' | 'loading' | 'empty' | 'error'>('data')
  const [name, setName] = useState('')
  const [role, setRole] = useState('')
  const [saving, setSaving] = useState(false)
  const [paging, setPaging] = useState(false)
  const [demoTab, setDemoTab] = useState('overview')

  // Simulates a server round trip so the loading overlay (previous rows kept, dimmed) is visible.
  const handlePageChange = (nextPage: number) => {
    setPaging(true)
    window.setTimeout(() => {
      setPage(nextPage)
      setPaging(false)
    }, 400)
  }

  const columns = useMemo<TableColumn<DemoAccount>[]>(
    () => [
      {
        key: 'name',
        header: 'Name / email',
        render: (row) => (
          <div className="cell-stack">
            <strong>{row.name}</strong>
            <span>{row.email}</span>
          </div>
        ),
      },
      { key: 'role', header: 'Role', render: (row) => humanizeCode(row.role) },
      { key: 'phone', header: 'Phone' },
      { key: 'joinedAt', header: 'Joined', render: (row) => formatDate(row.joinedAt) },
      { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
      {
        key: 'actions',
        header: 'Actions',
        align: 'right',
        render: (row) => (
          <Button variant="secondary" size="sm" onClick={() => toast.info('View profile', row.name)}>
            View profile
          </Button>
        ),
      },
    ],
    [toast],
  )

  const rows = tableMode === 'data' ? DEMO_ACCOUNTS.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE) : []
  const demoError = new ApiError(500, 'Internal server error')

  const handleSave = () => {
    setSaving(true)
    window.setTimeout(() => {
      setSaving(false)
      setModalOpen(false)
      toast.success('Staff account created', name ? `${name} can now log in.` : undefined)
    }, 800)
  }

  return (
    <div className="ui-kit">
      <header className="ui-kit__topbar">
        <Brand to="/" />
        <Link to="/login">Go to log in</Link>
      </header>

      <div className="ui-kit__content">
        <PageHeader
          eyebrow="Frontend foundation"
          title="UI kit"
          description="Shared components, tokens and formatters from FE-00. Import them from src/components/ui."
          actions={<Button onClick={() => setModalOpen(true)}>Open modal</Button>}
        />

        <Card title="Design tokens">
          <div className="ui-kit__swatches">
            {COLOR_TOKENS.map((token) => (
              <div key={token} className="swatch">
                <span className="swatch__color" style={{ background: `var(${token})` }} />
                <code>{token}</code>
              </div>
            ))}
          </div>
        </Card>

        <Card title="Buttons">
          <div className="ui-kit__row">
            <Button>Primary</Button>
            <Button variant="secondary">Secondary</Button>
            <Button variant="ghost">Ghost</Button>
            <Button variant="accent">Explore packages</Button>
            <Button variant="danger">Delete</Button>
            <Button loading>Saving</Button>
            <Button disabled>Disabled</Button>
            <Button size="sm">Small</Button>
            <Button size="lg">Large</Button>
          </div>
        </Card>

        <div className="ui-kit__grid">
          <Card title="Inputs">
            <div className="form-grid">
              <Input label="Full name" placeholder="Taylor Nguyen" value={name} onChange={(event) => setName(event.target.value)} />
              <Input label="Email address" type="email" placeholder="you@example.com" hint="We never share your email." />
              <Input label="Phone" defaultValue="12" error="Phone must be 8-15 digits with optional leading +" />
              <Input label="Disabled" value="Read only value" disabled readOnly />
              <PasswordInput label="Password (Show or Hide)" defaultValue="Train2026" autoComplete="off" />
              <Checkbox label="Remember me" defaultChecked />
              <Checkbox label="I agree to the terms" error="You must accept the terms to create an account" />
            </div>
          </Card>
          <Card title="Select">
            <div className="form-grid">
              <Select
                label="Role"
                placeholder="All roles"
                value={role}
                onChange={(event) => setRole(event.target.value)}
                options={[
                  { value: 'MEMBER', label: 'Member' },
                  { value: 'COACH', label: 'Coach' },
                  { value: 'RECEPTIONIST', label: 'Receptionist' },
                ]}
              />
              <Select
                label="Sport"
                error="Sport is required"
                placeholder="Choose a sport"
                options={[
                  { value: 'FOOTBALL', label: 'Football' },
                  { value: 'BADMINTON', label: 'Badminton' },
                ]}
              />
            </div>
          </Card>
        </div>

        <Card title="Notices">
          <div className="form-grid">
            <Notice tone="info" title="Payment instructions">Transfer the exact amount and keep the receipt.</Notice>
            <Notice tone="success" live="polite">Your profile was saved.</Notice>
            <Notice tone="warning" title="Package expires soon">Renew before the end date to keep your sessions.</Notice>
            <Notice tone="danger" action={<Button size="sm" variant="secondary">Try again</Button>}>
              The payment could not be verified.
            </Notice>
          </div>
        </Card>

        <Card title="Tabs">
          <Tabs
            label="Demo tabs"
            value={demoTab}
            onChange={setDemoTab}
            items={[
              { id: 'overview', label: 'Overview' },
              { id: 'history', label: 'History' },
              { id: 'locked', label: 'Locked', disabled: true },
            ]}
          >
            {(id) => <p className="state__description">Selected panel: {id}. Use the arrow keys, Home and End.</p>}
          </Tabs>
        </Card>

        <Card title="Badges">
          <div className="ui-kit__row">
            <Badge tone="success">Success</Badge>
            <Badge tone="warning">Warning</Badge>
            <Badge tone="danger">Danger</Badge>
            <Badge tone="info">Info</Badge>
            <Badge tone="neutral">Neutral</Badge>
          </div>
          <div className="ui-kit__row">
            {STATUS_CODES.map((code) => (
              <StatusBadge key={code} status={code} />
            ))}
          </div>
        </Card>

        <div className="ui-kit__stats">
          <StatCard label="Membership tier" value="VIP" hint="Active - Until 04 Jul 2027" />
          <StatCard label="Active packages" value="0" hint="Your paid sport access" />
          <StatCard label="Upcoming sessions" value="0" hint="View and book your schedule" />
        </div>

        <Card
          title="Table with pagination"
          actions={
            <Select
              aria-label="Table state"
              value={tableMode}
              onChange={(event) => setTableMode(event.target.value as typeof tableMode)}
              options={[
                { value: 'data', label: 'With data' },
                { value: 'loading', label: 'Loading' },
                { value: 'empty', label: 'Empty' },
                { value: 'error', label: 'Error' },
              ]}
            />
          }
        >
          <Table
            caption="Demo center accounts"
            columns={columns}
            rows={rows}
            rowKey={(row) => row.id}
            loading={tableMode === 'loading' || paging}
            error={tableMode === 'error' ? demoError : undefined}
            onRetry={() => setTableMode('data')}
            emptyTitle="No accounts found"
            emptyDescription="Try another name, email or role."
            pagination={{ page, pageSize: PAGE_SIZE, totalItems: DEMO_ACCOUNTS.length, onPageChange: handlePageChange }}
          />
        </Card>

        <div className="ui-kit__grid">
          <Card title="Toast">
            <div className="ui-kit__row">
              <Button variant="secondary" onClick={() => toast.success('Saved', 'Your changes were saved.')}>
                Success toast
              </Button>
              <Button variant="secondary" onClick={() => toast.error('Payment failed', 'Please try again.')}>
                Error toast
              </Button>
              <Button variant="secondary" onClick={() => toast.info('Heads up', 'Session starts in 30 minutes.')}>
                Info toast
              </Button>
              <Button variant="secondary" onClick={() => toast.warning('Card expiring', 'Your card ends in 7 days.')}>
                Warning toast
              </Button>
            </div>
          </Card>
          <Card title="Spinner">
            <div className="ui-kit__row">
              <Spinner size="sm" inline />
              <Spinner size="md" inline />
              <Spinner size="lg" inline />
            </div>
          </Card>
        </div>

        <div className="ui-kit__grid">
          <Card title="Empty state">
            <EmptyState
              title="No sport packages yet"
              description="Explore sports and find a package that suits you."
              action={<Button>Explore packages</Button>}
            />
          </Card>
          <Card title="Error state">
            <ErrorState error={demoError} onRetry={() => toast.info('Retrying')} />
          </Card>
        </div>

        <Card title="Formatters (src/utils/format.ts)">
          <dl className="definition-list">
            <dt>formatMoney(1350000)</dt>
            <dd>{formatMoney(1350000)}</dd>
            <dt>formatMoney(&apos;80000.00&apos;)</dt>
            <dd>{formatMoney('80000.00')}</dd>
            <dt>formatDate(&apos;2027-07-04&apos;)</dt>
            <dd>{formatDate('2027-07-04')}</dd>
            <dt>formatDateTime(&apos;2026-10-05T17:00:00&apos;)</dt>
            <dd>{formatDateTime('2026-10-05T17:00:00')}</dd>
            <dt>formatTime(&apos;2026-10-05T18:30:00&apos;)</dt>
            <dd>{formatTime('2026-10-05T18:30:00')}</dd>
            <dt>humanizeCode(&apos;PENDING_PAYMENT&apos;)</dt>
            <dd>{humanizeCode('PENDING_PAYMENT')}</dd>
          </dl>
        </Card>
      </div>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Add staff account"
        description="Create a coach or receptionist account."
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={handleSave} loading={saving}>
              Create account
            </Button>
          </>
        }
      >
        <div className="form-grid">
          <Input label="Full name" value={name} onChange={(event) => setName(event.target.value)} />
          <Select
            label="Role"
            options={[
              { value: 'COACH', label: 'Coach' },
              { value: 'RECEPTIONIST', label: 'Receptionist' },
            ]}
          />
        </div>
      </Modal>
    </div>
  )
}
