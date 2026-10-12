/**
 * Moves the keyboard focus to the first field marked invalid, so a user who pressed Enter can fix
 * the problem straight away. Runs after React has rendered the error messages.
 */
export function focusFirstInvalid(form: HTMLFormElement | null): void {
  window.setTimeout(() => {
    const invalid = form?.querySelector<HTMLElement>('[aria-invalid="true"]')
    invalid?.focus()
  }, 0)
}
