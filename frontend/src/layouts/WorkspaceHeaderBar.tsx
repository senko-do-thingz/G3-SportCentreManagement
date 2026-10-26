import type { ReactNode } from 'react'
import '@fontsource/be-vietnam-pro/800.css'
import '@fontsource/be-vietnam-pro/800-italic.css'
import { Brand } from '../components/Brand'
import { ThemeToggle } from '../theme/ThemeToggle'
import { UserMenu } from './UserMenu'

export interface WorkspaceHeaderBarProps {
  menuToggle?: ReactNode
  navigation?: ReactNode
}

/** Shared Home-style brand and account header for signed-in workspaces. */
export function WorkspaceHeaderBar({ menuToggle, navigation }: WorkspaceHeaderBarProps) {
  return (
    <div className="home-wrap home-header__bar workspace-header__bar">
      <Brand compact />
      {menuToggle}
      {navigation}
      <div className="home-account workspace-header__account">
        <ThemeToggle className="home-theme" />
        <UserMenu />
      </div>
    </div>
  )
}
