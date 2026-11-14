# Spori Gemini status evidence (2026-11-14)

Captured on `main` at `8f641bec`, backend run with `mvn spring-boot:run` from `backend/`, desktop 1440x900, account `manager@sportify.com`.

| File | What it shows |
| --- | --- |
| `spori-status-connected.png` | `/status/` answers "Gemini API: CONNECTED. Spori successfully received a live Gemini response." |
| `spori-gemini-reply.png` | A normal question answered by Gemini (reply tagged **Gemini**; API mode `AI_GENERATED`). |

## If `/status/` shows DISABLED

The running backend was started without the assistant AI enabled. The code is not broken. Add these to `backend/.env` (never commit it), then restart the backend from the `backend/` folder:

```
AI_ASSISTANT_ENABLED=true
AI_ASSISTANT_PROVIDER=gemini
GEMINI_API_KEY=<your key>
```

`NOT CONNECTED` means AI is on but Gemini refused or was unreachable: check the key, model, quota (free tier returns HTTP 429 quickly) and network.
