import { managerPreviewEnabled } from '../config/env'
import { tokenStorage } from './tokenStorage'

/** Proposed API payloads must never be sent using a real authenticated account. */
export function isManagerPreviewSession(): boolean {
  return managerPreviewEnabled() && tokenStorage.getAccessToken() === 'preview-MANAGER'
}
