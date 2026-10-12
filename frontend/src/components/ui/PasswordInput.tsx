import { useState } from 'react'
import { Input, type InputProps } from './Input'

export type PasswordInputProps = Omit<InputProps, 'type' | 'trailing'>

/**
 * Password field with a Show or Hide button inside the input. The button is a plain type="button",
 * so pressing Enter in the field still submits the form, and it follows the disabled state of the
 * field (and of a disabled fieldset around it) while the form is being sent.
 */
export function PasswordInput({ disabled, ...rest }: PasswordInputProps) {
  const [visible, setVisible] = useState(false)
  return (
    <Input
      {...rest}
      disabled={disabled}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          className="input-toggle"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
          disabled={disabled}
        >
          {visible ? 'Hide' : 'Show'}
        </button>
      }
    />
  )
}
