import { useEffect, useId, useRef, type ReactNode, type RefObject } from 'react'
import { createPortal } from 'react-dom'

export interface ModalProps {
  open: boolean
  onClose: () => void
  title: ReactNode
  description?: ReactNode
  children?: ReactNode
  /** Buttons shown at the bottom right, for example Cancel and Save. */
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
  /** Set to false for flows that must not be dismissed by Escape or a backdrop click. */
  dismissible?: boolean
}

/** Open modals in opening order; the last one is on top. */
const openModals: RefObject<HTMLDivElement | null>[] = []

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/** True when the element, or a parent up to the dialog, is hidden from the user. */
function isHidden(element: HTMLElement, root: HTMLElement): boolean {
  let node: HTMLElement | null = element
  while (node && node !== root.parentElement) {
    if (node.hidden || node.getAttribute('aria-hidden') === 'true' || node.hasAttribute('inert')) {
      return true
    }
    const style = window.getComputedStyle(node)
    if (style.display === 'none' || style.visibility === 'hidden') {
      return true
    }
    node = node.parentElement
  }
  return false
}

/** Focusable elements of the dialog in tab order, without hidden ones. */
function focusableIn(dialog: HTMLElement): HTMLElement[] {
  return Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((element) => !isHidden(element, dialog))
}

export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  size = 'md',
  dismissible = true,
}: ModalProps) {
  const titleId = useId()
  const descriptionId = useId()
  const dialogRef = useRef<HTMLDivElement>(null)
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  // Focus, scroll lock and focus restore depend only on open, so toggling dismissible while the
  // modal is open (for example during a submit) does not lose the element to restore focus to.
  useEffect(() => {
    if (!open) {
      return
    }
    const previouslyFocused = document.activeElement as HTMLElement | null
    const dialog = dialogRef.current
    const firstFocusable = dialog ? focusableIn(dialog)[0] : undefined
    ;(firstFocusable ?? dialog)?.focus()

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    openModals.push(dialogRef)

    return () => {
      const index = openModals.lastIndexOf(dialogRef)
      if (index >= 0) {
        openModals.splice(index, 1)
      }
      if (openModals.length === 0) {
        document.body.style.overflow = previousOverflow
      }
      previouslyFocused?.focus?.()
    }
  }, [open])

  useEffect(() => {
    if (!open) {
      return
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      // With stacked modals only the top one reacts to the keyboard.
      if (openModals[openModals.length - 1] !== dialogRef) {
        return
      }
      const dialog = dialogRef.current
      if (event.key === 'Escape' && dismissible) {
        event.stopPropagation()
        onCloseRef.current()
        return
      }
      // Keep keyboard focus inside the dialog.
      if (event.key === 'Tab' && dialog) {
        const items = focusableIn(dialog)
        if (items.length === 0) {
          event.preventDefault()
          dialog.focus()
          return
        }
        const first = items[0]
        const last = items[items.length - 1]
        const active = document.activeElement
        const outside = !(active instanceof Node) || !dialog.contains(active)
        if (outside) {
          // Focus escaped the dialog (for example a click on the page behind): bring it back.
          event.preventDefault()
          ;(event.shiftKey ? last : first).focus()
        } else if (event.shiftKey && (active === first || active === dialog)) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && active === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    // Focus moved outside the top dialog by other means (script, assistive technology): pull it back.
    const handleFocusIn = (event: FocusEvent) => {
      const dialog = dialogRef.current
      if (!dialog || openModals[openModals.length - 1] !== dialogRef) {
        return
      }
      const target = event.target
      if (target instanceof Node && !dialog.contains(target) && !target.contains(dialog)) {
        ;(focusableIn(dialog)[0] ?? dialog).focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('focusin', handleFocusIn)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('focusin', handleFocusIn)
    }
  }, [open, dismissible])

  if (!open) {
    return null
  }

  return createPortal(
    <div
      className="modal-backdrop"
      onMouseDown={(event) => {
        if (dismissible && event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <div
        ref={dialogRef}
        className={`modal modal--${size}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
      >
        <div className="modal__header">
          <div>
            <h2 className="modal__title" id={titleId}>
              {title}
            </h2>
            {description && (
              <p className="modal__description" id={descriptionId}>
                {description}
              </p>
            )}
          </div>
          {dismissible && (
            <button type="button" className="modal__close" onClick={onClose} aria-label="Close dialog">
              <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true">
                <path d="M5 5l10 10M15 5L5 15" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
            </button>
          )}
        </div>
        {children && <div className="modal__body">{children}</div>}
        {footer && <div className="modal__footer">{footer}</div>}
      </div>
    </div>,
    document.body,
  )
}
