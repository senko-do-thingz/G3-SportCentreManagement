import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { receptionApi, type MemberSummary } from '../../api/receptionApi'
import { Button, EmptyState, ErrorState, Input, StatusBadge } from '../../components/ui'
import { Spinner } from '../../components/ui/Spinner'
import { EMPTY_VALUE, initials } from '../../utils/format'

/** Shared member blocks of the receptionist screens (FE-05). */

export function MemberHeader({ member, children }: { member: MemberSummary; children?: ReactNode }) {
  const contact = [member.email, member.phone].filter(Boolean).join(' - ') || EMPTY_VALUE
  return (
    <div className="member-header">
      <span className="avatar member-header__avatar" aria-hidden="true">
        {initials(member.fullName)}
      </span>
      <div className="member-header__text">
        <p className="member-header__code">{member.memberCode ?? `Member #${member.userId}`}</p>
        <p className="member-header__name">
          <span>{member.fullName}</span>
          {member.status && <StatusBadge status={member.status} />}
        </p>
        <p className="member-header__contact">{contact}</p>
      </div>
      {children}
    </div>
  )
}

interface Submitted {
  query: string
  attempt: number
}

type SearchResult =
  | { status: 'ready'; members: MemberSummary[]; total: number }
  | { status: 'error'; error: unknown }

type SearchState = { status: 'idle' } | { status: 'loading'; query: string } | ({ query: string } & SearchResult)

export interface MemberPickerProps {
  /** Search run when the picker mounts, for example the q of the URL. */
  initialQuery?: string
  /**
   * Hands a new query to the page instead of searching, so the page can keep it in its URL. The page
   * must then mount a new picker with that query as initialQuery (for example with key={query}).
   * Searching the same query again runs here.
   */
  onSearch?: (query: string) => void
  /** how is 'auto' for the automatic single result, so the page can replace the history entry. */
  onSelect: (member: MemberSummary, how: 'auto' | 'click') => void
  /** Selects the member at once when the search returns exactly one result. */
  autoSelectSingle?: boolean
  /** Shown in the "No member found" state, for example a Create member button. */
  notFoundAction?: ReactNode
  buttonLabel?: string
}

export const SEARCH_REQUIRED_MESSAGE = 'Enter a name, phone number or email'

/** Search box with results for GET /members. Only the last search may update the list. */
export function MemberPicker({
  initialQuery = '',
  onSearch,
  onSelect,
  autoSelectSingle = false,
  notFoundAction,
  buttonLabel = 'Search',
}: MemberPickerProps) {
  const [input, setInput] = useState(initialQuery)
  const [inputError, setInputError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState<Submitted | null>(
    initialQuery.trim() ? { query: initialQuery.trim(), attempt: 0 } : null,
  )
  // Every answer is tagged with the search it belongs to, so an older answer is never shown.
  const [settled, setSettled] = useState<{ search: Submitted; result: SearchResult } | null>(null)
  const autoSelectRef = useRef<((member: MemberSummary, how: 'auto' | 'click') => void) | null>(null)

  useEffect(() => {
    autoSelectRef.current = autoSelectSingle ? onSelect : null
  }, [autoSelectSingle, onSelect])

  useEffect(() => {
    if (!submitted) {
      return
    }
    const controller = new AbortController()
    receptionApi.searchMembers({ query: submitted.query, signal: controller.signal }).then(
      (page) => {
        if (controller.signal.aborted) return
        setSettled({ search: submitted, result: { status: 'ready', members: page.content, total: page.totalElements } })
        if (page.content.length === 1) {
          autoSelectRef.current?.(page.content[0], 'auto')
        }
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setSettled({ search: submitted, result: { status: 'error', error } })
        }
      },
    )
    return () => controller.abort()
  }, [submitted])

  let state: SearchState = { status: 'idle' }
  if (submitted) {
    state =
      settled && settled.search === submitted
        ? { query: submitted.query, ...settled.result }
        : { status: 'loading', query: submitted.query }
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const query = input.trim()
    if (!query) {
      setInputError(SEARCH_REQUIRED_MESSAGE)
      return
    }
    setInputError(null)
    if (onSearch && query !== initialQuery.trim()) {
      // The page keeps the query in its URL and mounts a new picker for it, which runs the search.
      onSearch(query)
      return
    }
    setSubmitted((current) => ({ query, attempt: (current?.attempt ?? 0) + 1 }))
  }

  return (
    <div className="member-picker">
      <form className="member-picker__form" role="search" aria-label="Find member" onSubmit={submit} noValidate>
        <Input
          aria-label="Name, phone or email"
          placeholder="Name, phone or email"
          value={input}
          maxLength={100}
          onChange={(event) => setInput(event.target.value)}
          error={inputError ?? undefined}
        />
        <Button type="submit" loading={state.status === 'loading'}>
          {buttonLabel}
        </Button>
      </form>
      {state.status === 'loading' && <Spinner label="Searching members" />}
      {state.status === 'error' && (
        <ErrorState
          title="We could not search members"
          error={state.error}
          onRetry={() => setSubmitted((current) => current && { ...current, attempt: current.attempt + 1 })}
        />
      )}
      {state.status === 'ready' && state.members.length === 0 && (
        <EmptyState
          title="Member not found"
          description={`No member matches "${state.query}". Check the spelling, or create a new member profile.`}
          action={notFoundAction}
        />
      )}
      {state.status === 'ready' && state.members.length > 0 && !(autoSelectSingle && state.members.length === 1) && (
        <div className="member-results">
          <p className="member-results__count">
            {state.total === 1 ? '1 member found' : `${state.total} members found`}
            {state.total > state.members.length && ` - showing the first ${state.members.length}, refine the search`}
          </p>
          <ul className="member-results__list" aria-label="Search results">
            {state.members.map((member) => (
              <li key={member.userId} className="member-results__item">
                <MemberHeader member={member}>
                  <Button
                    variant="secondary"
                    size="sm"
                    aria-label={`Select ${member.fullName} ${member.memberCode ?? ''}`.trim()}
                    onClick={() => onSelect(member, 'click')}
                  >
                    Select
                  </Button>
                </MemberHeader>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
