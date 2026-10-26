import type { ReactNode } from 'react'
import '@fontsource/be-vietnam-pro/800.css'
import '@fontsource/be-vietnam-pro/800-italic.css'
import { Brand } from '../components/Brand'
import { AppearanceControl } from '../theme/AppearanceControl'
import { UserMenu } from './UserMenu'

export interface WorkspaceHeaderBarProps {
  menuToggle?: ReactNode
}

/** Shared account and brand bar for signed-in role workspaces. */
export function WorkspaceHeaderBar({ menuToggle }: WorkspaceHeaderBarProps) {
  return (
    <div className="workspace-header__bar container">
      <Brand />
      {menuToggle}
      <div className="header-actions">
        <AppearanceControl />
        <UserMenu />
      </div>
    </div>
  )
}
