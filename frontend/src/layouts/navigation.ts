import type { Role } from '../api/types'

/**
 * Menu of every role. Each item is a route that shows a placeholder until the task in
 * `task` replaces it with the real screen (see routes/AppRoutes.tsx).
 * Screen ids refer to the Figma files in context/.
 */
export interface NavItem {
  label: string
  /** Path relative to the role area, '' is the area home. */
  path: string
  /** Page title shown in the header and the placeholder. */
  title: string
  description: string
  task: string
  screens: string[]
}

export interface NavSection {
  heading?: string
  items: NavItem[]
}

export interface RoleArea {
  role: Role
  basePath: string
  workspaceLabel: string
  sections: NavSection[]
}

export const MEMBER_AREA: RoleArea = {
  role: 'MEMBER',
  basePath: '/member',
  workspaceLabel: 'Member area',
  sections: [
    {
      items: [
        {
          label: 'Dashboard',
          path: '',
          title: 'Member Dashboard',
          description: 'Your membership, sport packages and upcoming training in one place.',
          task: 'FE-02',
          screens: ['F1-07'],
        },
        {
          label: 'My Profile',
          path: 'profile',
          title: 'My Profile',
          description: 'Keep your contact details and password up to date.',
          task: 'FE-02',
          screens: ['F1-08'],
        },
        {
          label: 'Sport Packages',
          path: 'packages',
          title: 'Sport Packages',
          description: 'Choose one sport, your training style and a package that suits you.',
          task: 'FE-03',
          screens: ['F1-09', 'F1-10'],
        },
        {
          label: 'My Membership',
          path: 'membership',
          title: 'My Membership and Sport Packages',
          description: 'Your membership card, its benefits and the packages you registered.',
          task: 'FE-04',
          screens: ['F1-11', 'F1-12'],
        },
        {
          label: 'My Schedule',
          path: 'schedule',
          title: 'My Schedule',
          description: 'Find classes, book sessions and manage your bookings.',
          task: 'FE-08',
          screens: ['F2-04', 'F2-05', 'F2-06'],
        },
        {
          label: 'My Payments',
          path: 'payments',
          title: 'My Payments and Receipts',
          description: 'Payment instructions, payment history and receipts.',
          task: 'FE-11',
          screens: ['F3-01', 'F3-02', 'F3-03'],
        },
      ],
    },
  ],
}

export const RECEPTIONIST_AREA: RoleArea = {
  role: 'RECEPTIONIST',
  basePath: '/reception',
  workspaceLabel: 'Receptionist workspace',
  sections: [
    {
      heading: 'Front desk',
      items: [
        {
          label: 'Member search',
          path: '',
          title: 'Member Search and Profile',
          description: 'Find a member by name, phone or email.',
          task: 'FE-05',
          screens: ['F1-13'],
        },
        {
          label: 'Register and renew',
          path: 'register',
          title: 'Register and Renew',
          description: 'Register a sport package or renew a membership card for a member.',
          task: 'FE-05',
          screens: ['F1-14'],
        },
        {
          label: 'Check-in',
          path: 'check-in',
          title: 'Check-in',
          description: 'Verify paid training access and the session reservation.',
          task: 'FE-05',
          screens: ['F1-15'],
        },
        {
          label: 'Book for member',
          path: 'bookings',
          title: 'Book for Member',
          description: 'Search a member and book a session on their behalf.',
          task: 'FE-10',
          screens: ['F2-11', 'F2-12'],
        },
      ],
    },
    {
      heading: 'Payments',
      items: [
        {
          label: 'Payment requests',
          path: 'payments',
          title: 'Payment Requests',
          description: 'Verify transfers, record payments, issue receipts and submit refunds.',
          task: 'FE-12',
          screens: ['F3-04', 'F3-05', 'F3-06', 'F3-07'],
        },
      ],
    },
  ],
}

export const MANAGER_AREA: RoleArea = {
  role: 'MANAGER',
  basePath: '/manager',
  workspaceLabel: 'Manager workspace',
  sections: [
    {
      heading: 'Users and memberships',
      items: [
        {
          label: 'Users',
          path: '',
          title: 'User Management',
          description: 'Manage member, coach and receptionist accounts.',
          task: 'FE-06',
          screens: ['F1-01'],
        },
        {
          label: 'Sport packages',
          path: 'packages',
          title: 'Sport Packages and Pricing',
          description: 'Edit sport packages, prices and session rules.',
          task: 'FE-06',
          screens: ['F1-02'],
        },
        {
          label: 'Membership cards',
          path: 'cards',
          title: 'Membership Cards and Benefits',
          description: 'Edit membership tiers, prices and benefits.',
          task: 'FE-06',
          screens: ['F1-03'],
        },
        {
          label: 'Activity log',
          path: 'activity',
          title: 'Activity Log',
          description: 'Review important actions performed in the center.',
          task: 'FE-06',
          screens: ['F1-04'],
        },
      ],
    },
    {
      heading: 'Classes',
      items: [
        {
          label: 'Classes',
          path: 'classes',
          title: 'Class Management',
          description: 'Create and edit classes for each sport.',
          task: 'FE-07',
          screens: ['F2-01', 'F2-02'],
        },
        {
          label: 'Schedule and coaches',
          path: 'schedule',
          title: 'Schedule and Coach Assignment',
          description: 'Plan sessions and assign coaches without conflicts.',
          task: 'FE-07',
          screens: ['F2-03'],
        },
      ],
    },
    {
      heading: 'Finance',
      items: [
        {
          label: 'Financial overview',
          path: 'finance',
          title: 'Financial Overview',
          description: 'Revenue, pending payments and refunds at a glance.',
          task: 'FE-13',
          screens: ['F3-08'],
        },
        {
          label: 'Transactions and refunds',
          path: 'transactions',
          title: 'Transactions and Refund Approval',
          description: 'Review transactions and approve or reject refund requests.',
          task: 'FE-13',
          screens: ['F3-09'],
        },
        {
          label: 'Revenue reports',
          path: 'reports',
          title: 'Revenue Reports',
          description: 'Revenue by period, sport and package, with export.',
          task: 'FE-13',
          screens: ['F3-10'],
        },
      ],
    },
  ],
}

export const COACH_AREA: RoleArea = {
  role: 'COACH',
  basePath: '/coach',
  workspaceLabel: 'Coach workspace',
  sections: [
    {
      items: [
        {
          label: 'Teaching schedule',
          path: '',
          title: 'My Teaching Schedule',
          description: 'View your assigned classes and prepare for each session.',
          task: 'FE-10',
          screens: ['F2-13'],
        },
        {
          label: 'Class roster',
          path: 'roster',
          title: 'Class Roster',
          description: 'Members booked into your next sessions.',
          task: 'FE-10',
          screens: ['F2-14'],
        },
      ],
    },
  ],
}

export const STAFF_AREAS: Record<Exclude<Role, 'MEMBER'>, RoleArea> = {
  RECEPTIONIST: RECEPTIONIST_AREA,
  MANAGER: MANAGER_AREA,
  COACH: COACH_AREA,
}

export const ALL_AREAS: RoleArea[] = [MEMBER_AREA, RECEPTIONIST_AREA, MANAGER_AREA, COACH_AREA]

export function areaItems(area: RoleArea): NavItem[] {
  return area.sections.flatMap((section) => section.items)
}

export function itemHref(area: RoleArea, item: NavItem): string {
  return item.path ? `${area.basePath}/${item.path}` : area.basePath
}

/**
 * Returns the menu item with this path in the area, or throws. Used for routes that depend on a
 * menu entry (for example /member/payments/:paymentId reuses the My Payments item), so removing
 * or renaming that entry fails loudly when the routes are built instead of dropping a route.
 */
export function requireNavItem(area: RoleArea, path: string): NavItem {
  const item = areaItems(area).find((candidate) => candidate.path === path)
  if (!item) {
    throw new Error(`Menu item "${path}" is missing from ${area.basePath} in layouts/navigation.ts`)
  }
  return item
}

/** Finds the nav item that matches the current location, for page titles and breadcrumbs. */
export function findActiveItem(area: RoleArea, pathname: string): NavItem | undefined {
  const items = areaItems(area)
  const exact = items.find((item) => itemHref(area, item) === pathname)
  if (exact) {
    return exact
  }
  return items
    .filter((item) => item.path && pathname.startsWith(`${itemHref(area, item)}/`))
    .sort((a, b) => b.path.length - a.path.length)[0]
}
