export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'accent'
export type ButtonSize = 'sm' | 'md' | 'lg'

/** Class names for an element that should look like a button, for example a router Link. */
export function buttonClassName(
  variant: ButtonVariant = 'primary',
  size: ButtonSize = 'md',
  fullWidth = false,
  extra?: string,
): string {
  return ['btn', `btn--${variant}`, `btn--${size}`, fullWidth ? 'btn--full' : '', extra ?? '']
    .filter(Boolean)
    .join(' ')
}
