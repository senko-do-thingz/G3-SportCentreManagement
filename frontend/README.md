# Sportify Center - Frontend

React web app for Sportify Center (Flow 1-3). Owners: Phat, Hung.

Stack: Vite + React 19 + TypeScript + React Router 7. Tests: Vitest + Testing Library (jsdom).

## Requirements

- Node.js `^22.13.0`, `^24` or `>=26` (field `engines` in `package.json`). Node 20, 23 and 25 are not supported because Vitest 5 and ESLint 10 do not run on them. The recommended version is in `.nvmrc` (24). Check with `node -v`.
- With nvm for Windows: `nvm install 24` then `nvm use 24`.

## Run

```bash
cd frontend
npm install
cp .env.example .env        # optional, the default API URL is http://localhost:8080/api/v1
npm run dev
```

Open http://localhost:5173. The backend must be running (`cd backend`, then `mvn spring-boot:run`). CORS for `http://localhost:5173` is enabled by default in the `dev` profile; other origins go in `CORS_ALLOWED_ORIGINS` in `backend/.env`.

### Port 5173 is fixed

`vite.config.ts` sets `server.port = 5173` with `strictPort: true`, so `npm run dev` stops with "Port 5173 is already in use" instead of moving to another port that the backend CORS config would reject. When that happens, close the other dev server, or find and stop the process in PowerShell:

```powershell
Get-NetTCPConnection -LocalPort 5173 | Select-Object OwningProcess
Stop-Process -Id <OwningProcess>
```

If you really need another port, run `npm run dev -- --port 5174` and add `http://localhost:5174` to `CORS_ALLOWED_ORIGINS` in `backend/.env`, then restart the backend.

### Environment variables (`frontend/.env`, git-ignored)

| Variable | Default | Meaning |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1` | Base URL of the API, including `/api/v1` |
| `VITE_SHOW_SAMPLE_ACCOUNT` | off | Shows the sample account box on the Log in screen only when it is exactly `true`, and only in `npm run dev` |
| `VITE_SAMPLE_ACCOUNT_EMAIL` | empty | Email shown in that box |
| `VITE_SAMPLE_ACCOUNT_PASSWORD` | empty | Password shown in that box |

The box exists only in the development server (`npm run dev`): `sampleAccount()` also requires `import.meta.env.DEV`, so a production build (`npm run build`) never shows it and the values are not in the bundle. In development it stays hidden when the flag is not `true` or either value is empty. Use a throwaway demo member account for the box, never staff credentials, and never commit real values. Vite reads `.env` only when the dev server starts, so restart `npm run dev` after a change. Example in PowerShell:

```powershell
cd frontend
Copy-Item .env.example .env
notepad .env   # set VITE_SHOW_SAMPLE_ACCOUNT=true and the two values, then save
npm run dev
```

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
  config/       env.ts (VITE_API_BASE_URL, sampleAccount())
  layouts/      PublicLayout, MemberLayout (top nav), StaffLayout (side nav), navigation.ts (menus)
  pages/        public/ (log in, sign up, 403, 404), dev/ComponentDemoPage (/ui-kit), PlaceholderPage
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
- The access token is attached automatically. On 401 the client calls `POST /auth/refresh` once and retries once. If the backend rejects the refresh token (400, 401, 403 or 404), the tokens are cleared and the user is sent to the login page. A network error, 5xx or 429 keeps the session. A 403 on a normal request is a missing permission and never triggers a refresh.
- Several tabs share the tokens. The refresh runs inside a Web Locks API lock (`navigator.locks`), so only one tab refreshes at a time and the others reuse the new token. Without Web Locks, a rejected refresh checks storage again and reuses a token stored by another tab.
- Every failure is an `ApiError` with `status`, `message` (backend message) and `fieldErrors` (validation `details`, one entry per field). `status` is 0 when the server cannot be reached.
- `GET /members` returns a raw Spring page (`number` instead of `page`): wrap it with `normalizePage` from `api/page.ts`.

### Current user and roles

```ts
const { user, role, status, login, register, logout, reloadUser } = useAuth()

await login({ email, password }, { remember: true }) // remember: false keeps the session for this tab only
await register({ fullName, email, password, phone })  // creates a MEMBER and signs in (remembered)
```

"Remember me" (FE-01). Rule: one account per browser.

- Remember me ticked (and every sign up): the tokens go to localStorage. All tabs share them and they survive a browser restart.
- Remember me not ticked: the tokens go to the sessionStorage of the tab that logged in. They end when that tab is closed. Chrome and Edge with "Continue where you left off" restore sessionStorage when the browser reopens its tabs, so close the tab, not only the browser, to end such a session.
- A new login (with or without Remember me) and a logout always clear both areas of that tab, including the shared localStorage. Another tab that was signed in with a remembered session therefore loses it: it becomes anonymous and its login page shows "You were signed out because another account signed in or out in this browser." A logout in the same tab never shows that notice.
- A tab that holds its own session without Remember me also drops it when it sees another tab log in or out (`tokenStorage.endTabSession()`, called from the storage listener in `AuthProvider`).
- When another tab logs in as a different user, this tab follows the new account, but never silently: it opens the home page of the new user's role (`/member`, `/reception`, `/manager` or `/coach`, never the 403 page of the old URL) and every layout shows "You are now signed in as <full name> because another account signed in in this browser." The notice can be dismissed and is cleared by the next login, sign up, logout or switch (`useAuth().accountSwitched`, `dismissAccountSwitch()`). The role area is mounted again for the new user, so no page or form of the previous user stays on screen. The same user logging in again in another tab changes nothing visible, and a tab that was anonymous shows nothing.
- A request that was still running when the session changed fails with `StaleSessionError` (from `api/errors.ts`) instead of returning the previous account's answer, and it is never retried with the new account's token.
- Limitation: a login without Remember me writes nothing to localStorage when it is already empty, so a second tab that also logged in without Remember me gets no event and keeps its own account.
- `tokenStorage.startSession(tokens)` without options keeps the FE-00 behaviour (remembered). `useAuth().signedOutElsewhere` is true after such a sign out.

`status` is one of:

| Status | Meaning | What the guards do |
|---|---|---|
| `loading` | A stored session is being restored | Full page spinner |
| `authenticated` | Profile loaded | Render the page |
| `anonymous` | No session, or it ended | Redirect to `/login` |
| `error` | A session exists but the profile could not be loaded (server unreachable, 5xx, 429) | Full page Retry screen; tokens are kept and Retry calls `reloadUser()` |

Login and logout in another tab are followed automatically. A token refresh in another tab does not reload the profile.

Roles: `MEMBER`, `RECEPTIONIST`, `MANAGER`, `COACH`. Home pages: `/member`, `/reception`, `/manager`, `/coach`.

### Add a screen

1. Build the page under `src/pages/<area>/`.
2. Register it in `SCREENS` in `src/routes/AppRoutes.tsx` with its full path, for example `'/member/packages': <SportPackagesPage />`.
3. The menu entry already exists in `src/layouts/navigation.ts`. Add one there only for a new menu item. Sub pages (for example `/member/packages/:id`) go as extra routes inside the area.

Routes are already protected by role: a member opening a manager URL sees the 403 page.

### Shared UI

All components are shown at http://localhost:5173/ui-kit with working examples. This page exists only in development (`npm run dev`) and in tests: the route is registered when `import.meta.env.DEV` is true, so a production build (`npm run build`) does not contain the page or its route (it shows 404).

`Button`, `Input` (with an optional `trailing` element), `PasswordInput` (Show or Hide), `Checkbox`, `Select`, `Modal`, `Table` (with `Pagination`), `Badge` and `StatusBadge`, `Toast` (`useToast()`), `Spinner` and `PageLoader`, `EmptyState`, `ErrorState`, `Card`, `StatCard`, `PageHeader`.

Password rules for forms are in `auth/passwordRules.ts` (`checkPassword`, `isStrongPassword`), identical to the backend `@StrongPassword`. Reuse them for any screen that sets a password.

Formatters in `utils/format.ts`: `formatMoney(1350000)` gives `1.350.000 VND`, `formatDate('2027-07-04')` gives `04 Jul 2027`, `formatDateTime`, `formatTime`, `humanizeCode('PENDING_PAYMENT')` gives `Pending payment`, `initials`.

Use the CSS variables in `styles/tokens.css` (for example `var(--color-primary)`) instead of raw colors.

## Rules

- Code and comments in English; use - instead of an em dash; no emoji.
- Commit `package-lock.json`; never commit `node_modules/`, `dist/` or `frontend/.env`.
- `npm run lint`, `npm test` and `npm run build` must pass before opening a PR.
- `frontend/.env` is ignored by the root `.gitignore` (`.env` and `.env.*`, except `.env.example`). Check with `git check-ignore -v frontend/.env`.
- Tokens are kept in localStorage, so never render untrusted HTML (no `dangerouslySetInnerHTML` with user data).
