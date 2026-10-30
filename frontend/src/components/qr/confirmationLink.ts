import { DEMO_TOKEN } from '../../api/demoQrApi'

export function confirmationToken(hash: string): string | null {
  return hash.startsWith('#token=') && DEMO_TOKEN.test(hash.slice(7)) ? hash.slice(7) : null
}
