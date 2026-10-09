# Sportify Center - Frontend

React web app for Sportify Center (Flow 1-3). Owners: Phat, Hung.

This folder is created by task FE-00 (frontend foundation): Vite + React + TypeScript + React Router.

## Run (after FE-00 is merged)

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The backend must be running (`cd backend && mvn spring-boot:run`) with CORS enabled (task BE-00).

## Rules

- Code and comments in English; use - instead of an em dash; no emoji.
- Commit `package-lock.json`; never commit `node_modules/` or `dist/`.
- API base URL comes from `VITE_API_BASE_URL` in `frontend/.env` (not committed).
