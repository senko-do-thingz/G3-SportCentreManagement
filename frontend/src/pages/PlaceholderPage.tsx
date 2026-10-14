import { EmptyState } from '../components/ui/EmptyState'
import { PageHeader } from '../components/ui/PageHeader'
import type { NavItem } from '../layouts/navigation'

export interface PlaceholderPageProps {
  item: Pick<NavItem, 'title' | 'description' | 'task' | 'screens'>
  eyebrow?: string
}

/** Temporary page for a menu entry whose screen is built by a later task. */
export function PlaceholderPage({ item, eyebrow }: PlaceholderPageProps) {
  return (
    <div className="page">
      <PageHeader eyebrow={eyebrow} title={item.title} description={item.description} />
      <EmptyState
        title="This screen is coming soon"
        description={`Built in task ${item.task} (Figma ${item.screens.join(', ')}).`}
      />
    </div>
  )
}
