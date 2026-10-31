import { managerPreviewEnabled } from '../../config/env'
import { tokenStorage } from '../../api/tokenStorage'

/** Shown only in the opt-in member preview, where these screens read synthetic data. */
export function MemberTrainingPreviewNotice() {
  if (!managerPreviewEnabled() || tokenStorage.getAccessToken() !== 'preview-MEMBER') return null
  return <p className="notice notice--warning" role="status">Frontend member preview - sample data only. Training data is not read from the backend.</p>
}
