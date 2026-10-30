import { WorkspaceHeader } from './WorkspaceHeader'
import type { WorkspaceHeaderProps } from './WorkspaceHeader'

type PaymentWorkspaceHeaderProps = Omit<WorkspaceHeaderProps, 'variant' | 'className'>

/** Payment routes keep the same shared role header and add a stable hook for their page styles. */
export function PaymentWorkspaceHeader(props: PaymentWorkspaceHeaderProps) {
  const variant = props.navigation ? 'member' : 'staff'
  return <WorkspaceHeader {...props} variant={variant} className="payment-workspace-header" />
}
