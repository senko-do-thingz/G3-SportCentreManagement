import { useId, useState } from 'react'
import { Input, type InputProps } from './Input'

export type PasswordInputProps = Omit<InputProps, 'type' | 'trailing'>

/**
 * Password field with a Show or Hide button inside the input. The button is a plain type="button",
 * so pressing Enter in the field still submits the form, and it follows the disabled state of the
 * field (and of a disabled fieldset around it) while the form is being sent. The button name
 * names the action ("Show password" or "Hide password") and matches its visible text; it does not
 * also set aria-pressed, because a changing name plus a pressed state announce contradictory states.
 */
export function PasswordInput({ disabled, id, ...rest }: PasswordInputProps) {
  const [visible, setVisible] = useState(false)
  const generatedId = useId()
  const inputId = id ?? generatedId
  return (
    <Input
      {...rest}
      id={inputId}
      disabled={disabled}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          className="input-toggle"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-controls={inputId}
          disabled={disabled}
        >
          {visible ? 'Hide' : 'Show'}
        </button>
      }
    />
  )
}
