import { Outlet } from 'react-router-dom'

/** Layout for pages that do not need a signed in user, such as Log in and Sign up. */
export function PublicLayout() {
  return (
    <main className="public-layout" id="main-content">
      <Outlet />
    </main>
  )
}
