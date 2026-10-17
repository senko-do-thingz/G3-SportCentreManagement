import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthProvider'
import { ToastProvider } from '../components/ui/ToastProvider'
import { AppRoutes } from '../routes/AppRoutes'

/** Renders the full app at the given URL, with the same providers as App.tsx. */
export function renderApp(initialPath = '/', initialState?: unknown) {
  const parsed = new URL(initialPath, 'http://localhost')
  const initialEntry = initialState === undefined ? initialPath : {
    pathname: parsed.pathname,
    search: parsed.search,
    hash: parsed.hash,
    state: initialState,
  }
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <ToastProvider>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  )
}
