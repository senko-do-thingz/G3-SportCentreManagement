export const CALENDAR_DATE_VALIDATE_EVENT = 'calendar-date-field-validate'

/** Validates every shared calendar field in a non-form action scope and focuses the first failure. */
export function validateCalendarDateFields(container: ParentNode): boolean {
  const fields = Array.from(container.querySelectorAll<HTMLInputElement>('.calendar-date-field__text'))
  for (const field of fields) {
    const event = new Event(CALENDAR_DATE_VALIDATE_EVENT, { cancelable: true })
    field.dispatchEvent(event)
    if (event.defaultPrevented) return false
  }
  return true
}
