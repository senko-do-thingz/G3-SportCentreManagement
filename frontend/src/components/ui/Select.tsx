import { createPortal } from 'react-dom'
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type ChangeEvent, type KeyboardEvent, type ReactNode, type SelectHTMLAttributes } from 'react'

export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'children'> {
  label?: ReactNode
  hint?: ReactNode
  error?: string
  options: SelectOption[]
  /** Adds a first option with an empty value, for example "All roles". */
  placeholder?: string
}

interface MenuPosition {
  top: number
  left: number
  width: number
  maxHeight: number
}

export function Select({
  label,
  hint,
  error,
  options,
  placeholder,
  id,
  className,
  required,
  value,
  defaultValue,
  onChange,
  onClick,
  onBlur,
  onKeyDown,
  onMouseDown,
  onTouchStart,
  'aria-describedby': extraDescribedBy,
  ...rest
}: SelectProps) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const hintId = hint ? `${selectId}-hint` : undefined
  const errorId = error ? `${selectId}-error` : undefined
  const describedBy = [hintId, errorId, extraDescribedBy].filter(Boolean).join(' ') || undefined
  const items = [...(placeholder !== undefined ? [{ value: '', label: placeholder }] : []), ...options]
  const initialValue = value ?? defaultValue ?? (placeholder !== undefined ? '' : options[0]?.value ?? '')
  const [uncontrolledValue, setUncontrolledValue] = useState(String(initialValue))
  const selectedValue = String(value ?? uncontrolledValue)
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(Math.max(0, items.findIndex((item) => item.value === selectedValue)))
  const activeIndex = Math.min(active, Math.max(items.length - 1, 0))
  const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null)
  const [rootElement, setRootElement] = useState<HTMLDivElement | null>(null)
  const root = useCallback((element: HTMLDivElement | null) => setRootElement(element), [])
  const select = useRef<HTMLSelectElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const skipClick = useRef(false)

  function edgeEnabled(direction: 1 | -1) {
    const indexes = Array.from({ length: items.length }, (_, index) => direction === 1 ? index : items.length - index - 1)
    return indexes.find((index) => !items[index].disabled) ?? 0
  }

  function nextEnabled(current: number, direction: 1 | -1) {
    let index = current + direction
    while (index >= 0 && index < items.length) {
      if (!items[index].disabled) return index
      index += direction
    }
    return current
  }

  useEffect(() => {
    if (!open) return
    const close = (event: PointerEvent) => {
      const target = event.target
      if (!(target instanceof Node) || (!rootElement?.contains(target) && !list.current?.contains(target))) setOpen(false)
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [open, rootElement])

  useLayoutEffect(() => {
    if (!open) return
    function place() {
      const menu = list.current
      const trigger = select.current
      if (!menu || !trigger) return
      const rect = trigger.getBoundingClientRect()
      const below = Math.max(64, window.innerHeight - rect.bottom - 8)
      const above = Math.max(64, rect.top - 8)
      const placeBelow = below >= 200 || below >= above
      const maxHeight = Math.min(320, placeBelow ? below : above)
      const width = Math.min(rect.width, window.innerWidth - 16)
      const left = Math.max(8, Math.min(rect.left, window.innerWidth - width - 8))
      setMenuPosition({
        top: placeBelow ? rect.bottom - 8 : rect.top - maxHeight + 8,
        left,
        width,
        maxHeight,
      })
    }
    place()
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    return () => {
      window.removeEventListener('resize', place)
      window.removeEventListener('scroll', place, true)
    }
  }, [open, options.length])

  useEffect(() => {
    if (!open) return
    const menu = list.current
    const option = menu?.children[activeIndex] as HTMLElement | undefined
    if (!menu || !option) return
    if (option.offsetTop < menu.scrollTop) menu.scrollTop = option.offsetTop
    else if (option.offsetTop + option.offsetHeight > menu.scrollTop + menu.clientHeight) {
      menu.scrollTop = option.offsetTop + option.offsetHeight - menu.clientHeight
    }
  }, [activeIndex, open])

  function show() {
    if (!items.length || select.current?.matches(':disabled')) return
    const selectedIndex = items.findIndex((item) => item.value === select.current?.value)
    setActive(selectedIndex >= 0 && !items[selectedIndex].disabled ? selectedIndex : edgeEnabled(1))
    setOpen(true)
  }

  function choose(index: number) {
    const item = items[index]
    const control = select.current
    if (!item || !control || item.disabled || control.matches(':disabled')) return
    control.value = item.value
    control.dispatchEvent(new Event('change', { bubbles: true }))
    setOpen(false)
    control.focus()
  }

  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    if (value === undefined) setUncontrolledValue(event.target.value)
    const next = items.findIndex((item) => item.value === event.target.value)
    if (next >= 0) setActive(next)
    onChange?.(event)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLSelectElement>) {
    onKeyDown?.(event)
    if (event.defaultPrevented) return
    if (!open && ['ArrowDown', 'ArrowUp', 'Enter', ' ', 'Home', 'End'].includes(event.key)) {
      event.preventDefault()
      show()
      if (event.key === 'Home') setActive(edgeEnabled(1))
      if (event.key === 'End') setActive(edgeEnabled(-1))
      return
    }
    if (!open) return
    const moves: Record<string, () => void> = {
      ArrowDown: () => setActive(nextEnabled(activeIndex, 1)),
      ArrowUp: () => setActive(nextEnabled(activeIndex, -1)),
      Home: () => setActive(edgeEnabled(1)),
      End: () => setActive(edgeEnabled(-1)),
      Enter: () => choose(activeIndex),
      ' ': () => choose(activeIndex),
      Escape: () => setOpen(false),
      Tab: () => setOpen(false),
    }
    const move = moves[event.key]
    if (!move) return
    if (event.key !== 'Tab') event.preventDefault()
    move()
  }

  const menu = open && items.length > 0 ? (
    <ul
      ref={list}
      id={`${selectId}-list`}
      className="select-dropdown__list"
      style={menuPosition ? {
        top: menuPosition.top,
        left: menuPosition.left,
        width: menuPosition.width,
        maxHeight: menuPosition.maxHeight,
      } : undefined}
      role="listbox"
      tabIndex={-1}
      aria-label={typeof label === 'string' ? label : label ? undefined : rest['aria-label'] ?? 'Options'}
      aria-labelledby={label && typeof label !== 'string' ? `${selectId}-label` : undefined}
      aria-activedescendant={`${selectId}-option-${activeIndex}`}
    >
      {items.map((item, index) => (
        <li
          key={item.value || 'placeholder'}
          id={`${selectId}-option-${index}`}
          data-value={item.value}
          role="option"
          aria-selected={item.value === selectedValue}
          aria-disabled={item.disabled || undefined}
          className={[
            'select-dropdown__option',
            index === activeIndex ? 'select-dropdown__option--active' : '',
            item.disabled ? 'select-dropdown__option--disabled' : '',
          ].filter(Boolean).join(' ')}
          onMouseEnter={() => { if (!item.disabled) setActive(index) }}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => choose(index)}
        >
          {item.label}
          {item.value === selectedValue && (
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
              <path d="M5 12.5l4.5 4.5L19 7.5" />
            </svg>
          )}
        </li>
      ))}
    </ul>
  ) : null
  const portalTarget = rootElement?.closest('.modal') ?? (typeof document !== 'undefined' ? document.body : null)

  return (
    <div ref={root} className={['field', 'select-field', className ?? ''].filter(Boolean).join(' ')}>
      {label && (
        <div className="field__label-row">
          <label className="field__label select-field__label" id={`${selectId}-label`} htmlFor={selectId}>
            {label}
            {required && <span className="field__required"> *</span>}
          </label>
        </div>
      )}
      <div className={['select-wrap', 'select-dropdown', open ? 'select-dropdown--open' : ''].filter(Boolean).join(' ')}>
        <select
          ref={select}
          id={selectId}
          className={`input select select-dropdown__trigger${error ? ' input--invalid' : ''}`}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-controls={open ? `${selectId}-list` : undefined}
          aria-activedescendant={open && items[activeIndex] ? `${selectId}-option-${activeIndex}` : undefined}
          required={required}
          value={value}
          defaultValue={value === undefined ? defaultValue : undefined}
          onChange={handleChange}
          onClick={(event) => {
            onClick?.(event)
            if (skipClick.current) {
              skipClick.current = false
              return
            }
            if (!event.defaultPrevented) {
              if (open) setOpen(false)
              else show()
            }
          }}
          onMouseDown={(event) => {
            onMouseDown?.(event)
            event.preventDefault()
            if (!event.currentTarget.matches(':disabled')) event.currentTarget.focus()
          }}
          onTouchStart={(event) => {
            onTouchStart?.(event)
            event.preventDefault()
            skipClick.current = true
            window.setTimeout(() => { skipClick.current = false }, 500)
            if (!event.currentTarget.matches(':disabled')) {
              event.currentTarget.focus()
              if (open) setOpen(false)
              else show()
            }
          }}
          onBlur={(event) => {
            setOpen(false)
            onBlur?.(event)
          }}
          onKeyDown={handleKeyDown}
          {...rest}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((option) => (
            <option key={option.value} value={option.value} disabled={option.disabled}>
              {option.label}
            </option>
          ))}
        </select>
        <svg className="select-dropdown__chevron" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </div>
      {hint && !error && (
        <p className="field__hint" id={hintId}>
          {hint}
        </p>
      )}
      {error && (
        <p className="field__error" id={errorId} role="alert">
          {error}
        </p>
      )}
      {menu && portalTarget && createPortal(menu, portalTarget)}
    </div>
  )
}
