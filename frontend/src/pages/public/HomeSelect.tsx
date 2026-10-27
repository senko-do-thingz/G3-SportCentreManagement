import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react'
import { homeScrollBehavior } from './homeSectionNavigation'

export interface HomeSelectOption { value: string; label: string }

interface HomeSelectProps {
  label: string
  placeholder: string
  value: string
  options: readonly HomeSelectOption[]
  onChange: (value: string) => void
  sports?: boolean
}

/**
 * Themed single-select for the Home class finder (listbox pattern).
 * All filters use a single vertical list with nonwrapping Up/Down and Home/End navigation.
 * Selection remains separate from active-descendant navigation. Enter/Space select; Escape closes.
 */
export function HomeSelect({ label, placeholder, value, options, onChange, sports = false }: HomeSelectProps) {
  const id = useId()
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const assisted = useRef(false)
  const items = [{ value: '', label: placeholder }, ...options]
  // A catalogue refresh must never leave aria-activedescendant pointing past the final option.
  if (active >= items.length) setActive(items.length - 1)
  const selected = items.find((item) => item.value === value) ?? items[0]

  useEffect(() => {
    if (!open) return
    const close = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false) }
    document.addEventListener('pointerdown', close)
    list.current?.focus(sports ? { preventScroll: true } : undefined)
    return () => document.removeEventListener('pointerdown', close)
  }, [open, sports])

  useLayoutEffect(() => {
    if (!open || !sports) return
    function place() {
      const menu = list.current
      const trigger = button.current
      if (!menu || !trigger) return
      menu.style.maxHeight = `${Math.max(64, window.innerHeight - trigger.getBoundingClientRect().bottom - 24)}px`
    }
    const menu = list.current
    const trigger = button.current
    if (menu && trigger && !assisted.current) {
      assisted.current = true
      const rect = trigger.getBoundingClientRect()
      const floor = (document.querySelector('.home-header')?.getBoundingClientRect().bottom ?? 0) + 12
      const needed = menu.scrollHeight + 4 - (window.innerHeight - rect.bottom - 24)
      // Move at most part of one viewport, once per opening. Never flip or detach the menu.
      const top = rect.top < floor ? rect.top - floor : Math.min(Math.max(0, needed), 360, window.innerHeight * 0.4, Math.max(0, rect.top - floor))
      if (rect.height > 0 && Math.abs(top) > 1) window.scrollBy({ top, behavior: homeScrollBehavior() })
    }
    place()
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, { passive: true })
    return () => { window.removeEventListener('resize', place); window.removeEventListener('scroll', place) }
  }, [open, sports, options.length])

  useEffect(() => {
    const menu = list.current
    const option = menu?.children[active] as HTMLElement | undefined
    if (!menu || !option) return
    if (option.offsetTop < menu.scrollTop) menu.scrollTop = option.offsetTop
    else if (option.offsetTop + option.offsetHeight > menu.scrollTop + menu.clientHeight) menu.scrollTop = option.offsetTop + option.offsetHeight - menu.clientHeight
  }, [active, open])

  function show() {
    assisted.current = false
    setActive(Math.max(0, items.findIndex((item) => item.value === value)))
    setOpen(true)
  }
  function choose(index: number) {
    onChange(items[index].value)
    setOpen(false)
    button.current?.focus()
  }
  function onButtonKey(event: KeyboardEvent) {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) { event.preventDefault(); show() }
  }
  function onListKey(event: KeyboardEvent) {
    const last = items.length - 1
    const moves: Record<string, () => void> = {
      ArrowDown: () => setActive((index) => Math.min(last, index + 1)),
      ArrowUp: () => setActive((index) => Math.max(0, index - 1)),
      Home: () => setActive(0),
      End: () => setActive(last),
      Enter: () => choose(active),
      ' ': () => choose(active),
      Escape: () => { setOpen(false); button.current?.focus() },
      // Restore the trigger before native Tab traversal so removal of the list does not lose its position.
      Tab: () => { button.current?.focus(); setOpen(false) },
    }
    const move = moves[event.key]
    if (!move) return
    if (event.key !== 'Tab') event.preventDefault()
    move()
  }

  return (
    <div className={`home-select${open ? ' home-select--open' : ''}`} ref={root}
      onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setOpen(false) }}>
      <span className="home-select__label" id={`${id}-label`}>{label}</span>
      <button
        ref={button}
        type="button"
        className="home-select__button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? `${id}-list` : undefined}
        aria-labelledby={`${id}-label ${id}-value`}
        onClick={() => (open ? setOpen(false) : show())}
        onKeyDown={onButtonKey}
      >
        <span id={`${id}-value`} className={value ? undefined : 'home-select__placeholder'}>{selected.label}</span>
        <svg className="home-select__chevron" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M6 9l6 6 6-6" /></svg>
      </button>
      {open && (
        <ul
          ref={list}
          id={`${id}-list`}
          className={`home-select__list${sports ? ' home-select__list--sports' : ''}`}
          data-placement={sports ? 'below' : undefined}
          role="listbox"
          tabIndex={-1}
          aria-label={label}
          aria-describedby={sports ? `${id}-keyboard` : undefined}
          aria-activedescendant={`${id}-option-${active}`}
          onKeyDown={onListKey}
        >
          {items.map((item, index) => (
            <li
              key={item.value || 'all'}
              id={`${id}-option-${index}`}
              role="option"
              aria-selected={item.value === value}
              className={`home-select__option${index === active ? ' home-select__option--active' : ''}`}
              onMouseEnter={sports ? undefined : () => setActive(index)}
              onPointerMove={sports ? (event) => {
                // Scrolling the page under a stationary pointer must not change keyboard focus.
                if (event.pointerType === 'mouse' && (event.movementX || event.movementY)) setActive(index)
              } : undefined}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(index)}
            >
              {item.label}
              {item.value === value && <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>}
            </li>
          ))}
        </ul>
      )}
      {sports && <span id={`${id}-keyboard`} className="home-sr-only">One column. Up and Down move to the previous and next option without wrapping. Home and End move to the first and last option. Enter or Space selects.</span>}
    </div>
  )
}
