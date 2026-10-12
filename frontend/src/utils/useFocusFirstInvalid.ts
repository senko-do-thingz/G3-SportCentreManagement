import { useCallback, useEffect, useState, type RefObject } from 'react'

/**
 * Returns a function that moves the keyboard focus to the first field marked invalid
 * ([aria-invalid="true"]) inside the form, so a user who pressed Enter can fix the problem
 * straight away.
 *
 * The request is a state counter and the focus happens in an effect, which runs after React has
 * committed the render that shows the error messages and re-enables the form. A timer could run
 * before that commit, while the fieldset is still disabled, and the focus would be lost.
 */
export function useFocusFirstInvalid(formRef: RefObject<HTMLFormElement | null>): () => void {
  const [focusRequest, setFocusRequest] = useState(0)

  useEffect(() => {
    if (focusRequest === 0) {
      return
    }
    formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
  }, [focusRequest, formRef])

  return useCallback(() => setFocusRequest((current) => current + 1), [])
}
