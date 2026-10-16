import { managerPreviewEnabled } from '../config/env'
import { tokenStorage } from './tokenStorage'

/** Only synthetic manager sessions can use the unreleased activity-log contract. */
export function isManagerPreviewSession(): boolean {
  return managerPreviewEnabled() && tokenStorage.getAccessToken() === 'preview-MANAGER'
}
