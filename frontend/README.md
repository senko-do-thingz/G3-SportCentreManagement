# Sportify Center - Frontend

React web app for Sportify Center (Flow 1-3). Owners: Phat, Hung.

Stack: Vite + React 19 + TypeScript + React Router 7. Tests: Vitest + Testing Library (jsdom).

## Run

```bash
cd frontend
npm install
cp .env.example .env        # optional, the default API URL is http://localhost:8080/api/v1
npm run dev
```

Open http://localhost:5173. The backend must be running (`cd backend && mvn spring-boot:run`). CORS for `http://localhost:5173` is enabled by default in the `dev` profile; other origins go in `CORS_ALLOWED_ORIGINS` in `backend/.env`. The dev server uses port 5173 with `strictPort`, so it fails instead of silently moving to a port the backend does not allow.

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | Type check and production build into `dist/` (must pass before a PR) |
| `npm run lint` | ESLint |
| `npm test` | Unit and flow tests once |
| `npm run test:watch` | Tests in watch mode |

Demo accounts: `manager@sportify.com` / `password` (seeded by V4). Other roles: create them with the manager API (BE-01) or SQL.

## Folder structure

```
src/
  api/          client.ts (fetch wrapper, token refresh), authApi.ts, errors.ts (ApiError), types.ts, page.ts
  auth/         AuthProvider, useAuth, RequireAuth, RequireRole, roles.ts (role labels and home paths)
  components/   Brand.tsx and ui/ (shared UI kit, import from components/ui)
  config/       env.ts (VITE_API_BASE_URL)
  layouts/      PublicLayout, MemberLayout (top nav), StaffLayout (side nav), navigation.ts (menus)
  pages/        public/ (login, 403, 404), dev/ComponentDemoPage (/ui-kit), PlaceholderPage
  routes/       AppRoutes.tsx
  styles/       tokens.css (design tokens), base.css, components.css, layouts.css
  utils/        format.ts (money, date, labels, initials)
  test/         test setup and helpers
```

## How to use the foundation

### Call the API

```ts
import { api } from '../api/client'
import { ApiError } from '../api/errors'
import type { PageResponse } from '../api/types'

const users = await api.get<PageResponse<ManagerUser>>('/manager/users', { query: { page: 0, size: 10, search } })
await api.post('/manager/users', body)
```

- The base URL and `/api/v1` prefix come from `VITE_API_BASE_URL`; pass only the path.
- The access token is attached automatically. On 401 the client calls `POST /auth/refresh` once and retries once. If the refresh fails, the tokens are cleared and the user is sent to the login page.
- Every failure is an `ApiError` with `status`, `message` (backend message) and `fieldErrors` (validation `details`, one entry per field). `status` is 0 when the server cannot be reached.
- `GET /members` returns a raw Spring page (`number` instead of `page`): wrap it with `normalizePage` from `api/page.ts`.

### Current user and roles

```ts
const { user, role, status, login, logout, reloadUser } = useAuth()
```

Roles: `MEMBER`, `RECEPTIONIST`, `MANAGER`, `COACH`. Home pages: `/member`, `/reception`, `/manager`, `/coach`.

### Add a screen

1. Build the page under `src/pages/<area>/`.
2. Register it in `SCREENS` in `src/routes/AppRoutes.tsx` with its full path, for example `'/member/packages': <SportPackagesPage />`.
3. The menu entry already exists in `src/layouts/navigation.ts`. Add one there only for a new menu item. Sub pages (for example `/member/packages/:id`) go as extra routes inside the area.

Routes are already protected by role: a member opening a manager URL sees the 403 page.

### Shared UI

All components are shown at http://localhost:5173/ui-kit with working examples.

`Button`, `Input`, `Select`, `Modal`, `Table` (with `Pagination`), `Badge` and `StatusBadge`, `Toast` (`useToast()`), `Spinner` and `PageLoader`, `EmptyState`, `ErrorState`, `Card`, `StatCard`, `PageHeader`.

Formatters in `utils/format.ts`: `formatMoney(1350000)` gives `1.350.000 VND`, `formatDate('2027-07-04')` gives `04 Jul 2027`, `formatDateTime`, `formatTime`, `humanizeCode('PENDING_PAYMENT')` gives `Pending payment`, `initials`.

Use the CSS variables in `styles/tokens.css` (for example `var(--color-primary)`) instead of raw colors.

## Rules

- Code and comments in English; use - instead of an em dash; no emoji.
- Commit `package-lock.json`; never commit `node_modules/`, `dist/` or `frontend/.env`.
- `npm run lint`, `npm test` and `npm run build` must pass before opening a PR.
- Tokens are kept in localStorage, so never render untrusted HTML (no `dangerouslySetInnerHTML` with user data).
