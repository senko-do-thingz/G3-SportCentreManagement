import { useEffect, useRef, useState, type FormEvent, type RefObject } from 'react'
import { ApiError, getErrorMessage, splitFieldErrors } from '../../api/errors'
import { useFocusFirstInvalid } from '../../utils/useFocusFirstInvalid'

export { useResource } from '../../hooks/useResource'

export function useDebounced(value: string, delay = 300): string {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return settled
}

export function useManagerForm(
  formRef: RefObject<HTMLFormElement | null>,
  fields: readonly string[],
  validate: () => Record<string, string>,
  save: () => Promise<unknown>,
  onSuccess: () => void,
) {
  const focusInvalid = useFocusFirstInvalid(formRef)
  const sending = useRef(false)
  const mounted = useRef(false)
  const [saving, setSaving] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (sending.current) return
    const invalid = validate()
    setErrors(invalid)
    setError(null)
    if (Object.keys(invalid).length) { focusInvalid(); return }
    sending.current = true
    setSaving(true)
    try {
      await save()
      if (mounted.current) onSuccess()
    } catch (cause) {
      if (!mounted.current) return
      if (cause instanceof ApiError) {
        const mapped = splitFieldErrors(cause.fieldErrors, fields)
        setErrors(mapped.fields)
        setError([getErrorMessage(cause), mapped.formMessage].filter(Boolean).join(' '))
      } else setError(getErrorMessage(cause))
      focusInvalid()
    } finally {
      sending.current = false
      if (mounted.current) setSaving(false)
    }
  }

  function clear(field: string) {
    setErrors((current) => {
      const next = { ...current }
      delete next[field]
      return next
    })
  }
  return { errors, error, saving, submit, clear }
}
