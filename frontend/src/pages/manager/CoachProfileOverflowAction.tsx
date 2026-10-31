import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type MouseEvent } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '../../components/ui'
import type { ManagerUser } from '../../types/manager'

interface CoachProfileOverflowActionProps {
  account: ManagerUser
  onEdit: (account: ManagerUser) => void
}

interface PopoverPosition {
  top: number
  left: number
}

function clamp(value: number, minimum: number, maximum: number) {
  return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)))
}

function nextFocusableAfter(trigger: HTMLButtonElement, popover: HTMLDivElement): HTMLElement | null {
  const candidates = Array.from(document.querySelectorAll<HTMLElement>(
    'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
  )).filter((element) => {
    if (popover.contains(element) || element.closest('[hidden], [aria-hidden="true"]') || element.tabIndex < 0) return false
    const style = window.getComputedStyle(element)
    return style.display !== 'none' && style.visibility !== 'hidden'
  })
  const triggerIndex = candidates.indexOf(trigger)
  return triggerIndex >= 0 ? candidates[triggerIndex + 1] ?? null : null
}

export function CoachProfileOverflowAction({ account, onEdit }: CoachProfileOverflowActionProps) {
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState<PopoverPosition | null>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const popoverRef = useRef<HTMLDivElement>(null)
  const editActionRef = useRef<HTMLButtonElement>(null)
  const popoverId = 'coach-profile-actions-' + account.id

  function dismiss() {
    setOpen(false)
    setPosition(null)
  }

  function toggle(event: MouseEvent<HTMLButtonElement>) {
    if (open) {
      dismiss()
      return
    }
    triggerRef.current = event.currentTarget
    setPosition(null)
    setOpen(true)
  }

  useLayoutEffect(() => {
    if (!open) return
    const trigger = triggerRef.current
    const popover = popoverRef.current
    if (!trigger || !popover) return

    const triggerBounds = trigger.getBoundingClientRect()
    const popoverBounds = popover.getBoundingClientRect()
    const styles = window.getComputedStyle(popover)
    const width = popoverBounds.width || popover.offsetWidth || Number.parseFloat(styles.minWidth) || 208
    const height = popoverBounds.height || popover.offsetHeight || popover.scrollHeight || 52
    const padding = 8
    const gap = 8
    const viewportWidth = window.innerWidth
    const viewportHeight = window.innerHeight
    const roomBelow = viewportHeight - triggerBounds.bottom - padding
    const roomAbove = triggerBounds.top - padding
    const openBelow = roomBelow >= height + gap || roomBelow >= roomAbove
    const top = clamp(openBelow ? triggerBounds.bottom + gap : triggerBounds.top - height - gap, padding, viewportHeight - height - padding)
    const left = clamp(triggerBounds.right - width, padding, viewportWidth - width - padding)
    setPosition((current) => current?.top === top && current.left === left ? current : { top, left })
  }, [open, position])

  useLayoutEffect(() => {
    if (open) editActionRef.current?.focus()
  }, [open])

  useEffect(() => {
    if (!open) return

    const trigger = triggerRef.current
    const popover = popoverRef.current
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target
      if (target instanceof Node && !popover?.contains(target) && !trigger?.contains(target)) dismiss()
    }
    const onFocusIn = (event: FocusEvent) => {
      const target = event.target
      if (target instanceof Node && !popover?.contains(target) && !trigger?.contains(target)) dismiss()
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      dismiss()
      trigger?.focus()
    }

    document.addEventListener('pointerdown', onPointerDown, true)
    document.addEventListener('focusin', onFocusIn)
    document.addEventListener('keydown', onKeyDown)
    window.addEventListener('resize', dismiss)
    window.addEventListener('scroll', dismiss, true)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown, true)
      document.removeEventListener('focusin', onFocusIn)
      document.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('resize', dismiss)
      window.removeEventListener('scroll', dismiss, true)
    }
  }, [open])

  function handlePopoverKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Tab') return
    const trigger = triggerRef.current
    const popover = popoverRef.current
    if (!trigger || !popover) return
    event.preventDefault()
    const next = event.shiftKey ? trigger : nextFocusableAfter(trigger, popover)
    dismiss()
    ;(next ?? trigger).focus()
  }

  function editCoachProfile() {
    dismiss()
    triggerRef.current?.focus()
    onEdit(account)
  }

  return <>
    <Button
      variant="secondary"
      size="sm"
      className="manager-users-more"
      aria-label={'More actions for ' + account.fullName}
      aria-controls={open ? popoverId : undefined}
      aria-expanded={open}
      title="More actions"
      onClick={toggle}
    >
      <svg className="manager-users-more__icon" viewBox="0 0 20 20" aria-hidden="true">
        <circle cx="4" cy="10" r="1.6" />
        <circle cx="10" cy="10" r="1.6" />
        <circle cx="16" cy="10" r="1.6" />
      </svg>
    </Button>
    {open && createPortal(
      <div
        id={popoverId}
        ref={popoverRef}
        className="manager-user-actions-popover"
        role="group"
        aria-label={'More actions for ' + account.fullName}
        style={{
          top: position?.top ?? 0,
          left: position?.left ?? 0,
          visibility: position ? 'visible' : 'hidden',
        }}
        onKeyDown={handlePopoverKeyDown}
      >
        <button ref={editActionRef} type="button" onClick={editCoachProfile}>Edit coach profile</button>
      </div>,
      document.body,
    )}
  </>
}