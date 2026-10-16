# FE-06 verification

Implementation branch: feat/f1-manager-screens.

Working project: E:/SWP391_SS2/Project/V1/G3-SportCentreManagement-main. Integrated origin/main at 91c1f29 on 2026-10-17 in merge commit 26a2895. The feature snapshot had an unrelated root history, so integration preserves both histories through a merge. Local files from before the update are recoverable at E:/SWP391_SS2/Project/V1/G3-SportCentreManagement-recovery-20261015-234415. Tracked backend files match origin/main exactly; no custom backend edits were made.

## Run locally

From this repository's frontend folder, run npm ci, then npm run dev:preview. Open http://localhost:5173/login and choose Open manager preview. The preview does not require a backend or a real account. For an IP-bound server, use npm run dev:preview -- --host 127.0.0.1 and open http://127.0.0.1:5173/login.

Use npm run dev for live mode. The default API URL is http://localhost:8080/api/v1. Preview is off by default, available only in development and requires VITE_MANAGER_PREVIEW=true or the manager-preview Vite mode. Its sign-in controls and fixture adapter are excluded from production builds. Preview tokens are synthetic and use the existing auth provider and role guards; they never grant access to the real backend.

All preview data and simulated writes are in src/api/mock/managerPreview.ts. Changes are held in memory, reset on a full reload and never written to the backend. The preview covers manager users, sports, all 36 packages across six sports, three card tiers and activity records. Preview-only authentication uses reserved @preview.sportify.local accounts. Real manager tokens use the released user, package, card and BE-04 activity endpoints even when the preview flag is enabled; preview tokens continue to use the isolated fixture adapter.

## Integration status

| Screen | Live contract | Current limitation |
| --- | --- | --- |
| User Management | GET/POST /manager/users; PATCH /manager/users/{id}/status; GET /sports | Verified against ManagerUserController and DTOs on main. Real-service smoke testing still requires a running backend and manager session. |
| Packages | GET /manager/packages?sportId=&format=&active=; POST /packages; PUT /packages/{id} | Released BE-03 contract. Create uses priceAmount; update uses price and active. Responses use priceAmount and isActive. No invented pagination or public active-only fallback. |
| Cards | GET /manager/card-tiers; PUT /membership-cards/tiers/{id} | Released BE-03 contract. Update uses price, durationMonths, integer discountPercentage, description and active. Responses retain isActive and tierRank. |
| Activity | GET /manager/activity-logs with actorId, action, from, to, search, page and size; actor lookup reuses verified GET /manager/users | Matches BE-04 ActivityLogResponse, including the system fallback name and shortDetail. The action dropdown contains only the backend's 36 supported action codes. Calendar end dates are inclusive; the API accepts local YYYY-MM-DD dates. |

No production fallback substitutes the public active-only package/tier list for a manager catalog. Tracked backend files match origin/main exactly; no custom backend edits were made. Browser review uses only the synthetic preview. Real-service mutation testing was not performed.

BE-03 validation: package name at most 100 characters, sessions 1-1000, duration 1-3650 days, 60/90 minutes, and positive prices with at most 12 integer digits and 2 decimals. Paid card validity is 1-120 months, discount is a whole number 0-100, and description is at most 500 characters. Standard stays read-only. Active card fees must increase by tier rank; backend business-rule errors remain visible in the editor. Price/active server field errors map to the corresponding package/card inputs.

## Design references and intentional adjustments

All nine images were opened before implementation from context/Flow 1 - User and membership management/Flow 1 - Manager in the working project.

- User Management and Add Staff overlay: preserve the shared blue/white shell, filters and account columns. Omit unsupported global role counts and arbitrary profile edits. Add the required temporary password and ordered coach sports instead of unsupported skills/certificate fields. Status confirmation replaces arbitrary Edit.
- Packages: one catalog implements all three screenshot segments, with sport, training type and status filters. Twenty-row pagination is explicitly local. Code, sport and training type remain immutable during edits.
- Cards: omit unsupported Create tier. Standard stays read-only, free, active, zero-discount and without an expiry. Preserve unknown and inactive tiers. Package discounts do not stack or apply to single visits.
- Activity: add required actor/date/action filters and pagination. Actor selection searches all account pages. Action options are restricted to the BE-04 supported codes. Reference and details use entity type/id and the safe backend shortDetail; no extra endpoint is called.
- Manager timestamps use dd/MM/yyyy and center-local time. Money uses the shared VND formatter. Navigation, API refresh, auth storage and modal keyboard handling reuse the foundation.

## Manual checks

1. Access: log out and open each /manager route. Expect login. Use Check member access on the preview login screen. Expect 403 without manager data. Return to login and choose Open manager preview.
2. Loading: open /manager?previewState=slow, /manager/packages?previewState=slow, /manager/cards?previewState=slow and /manager/activity?previewState=slow. Expect a visible loading state and no stale rows under changed filters.
3. Empty: use the same routes with previewState=empty. Expect a distinct empty state. Search for a nonexistent account/package/action in normal preview and expect no matching results, then clear filters.
4. Error and retry: use previewState=error. Expect a service error rather than a successful empty list. Try again preserves filters. Remove the parameter and reload for normal data. In live mode, users/packages/cards call the backend and report real errors; activity still shows the BE-04 dependency error.
5. User filters: combine role/status/search, confirm status offers only Active and Inactive, inspect the changed rows, move to page 2, then change a filter and expect page 1. Rapidly change filters and expect only the newest response.
6. Staff form: submit blank fields and verify focus on the first invalid input. Validate email/phone/password. Choose Coach, select Basketball then Football and verify Basketball is primary. Change to Receptionist and verify sports are cleared. Duplicate an existing email/phone and expect inline server messages with entered fields retained. Press Enter twice and expect one creation. Cancel/Escape sends no mutation and clears the temporary password by unmounting the editor.
7. User status: verify your own Deactivate is disabled. Confirm another account's status change; cancel first and verify no change. Successful confirmation refreshes the current query. A failed refresh must report the refresh error without reopening the successful creation form.
8. Packages: filter every sport, both training types and inactive status. Create a package with a unique code, a positive numeric price, whole duration/session counts and 60 or 90 minutes. Edit price and status; code/sport/type stay read-only. Reject zero/negative prices, fractional counts and blank numeric fields. Cancel leaves data unchanged. Confirm deactivation, then find the item under inactive packages.
9. Cards: Standard has a default-tier badge and no editor. Edit Gold/VIP; check positive fee, validity 1-120 months and integer discounts including 0 and 100. Reject 101/-1/fractional discounts, blank numbers and descriptions longer than 500 characters. Cancel keeps the old fee. Try a VIP fee below Gold and expect the rank-price error with input preserved. Save an inactive tier and verify its label. Discount policy remains visible.
10. Activity: filter actor by searching the paginated picker, then combine action/search/from/to. Confirm the Action dropdown contains only supported codes. Enter a reversed range and expect an inline error with no log request. Check that a local calendar day is not shifted. Open details and expect the backend shortDetail with no additional endpoint, then Refresh. Verify null actor history displays "System / unavailable actor". History has no edit/delete controls.
11. Responsive: inspect each page and editor at 375px and desktop. Filters/forms/cards stack, the navigation menu remains usable, and tables scroll inside their panels without making the whole page wider. Verify visible focus, modal Tab/Shift+Tab trapping and Escape dismissal.
12. Developer checks: run npm run test, npm run lint and npm run build. Inspect the browser console in preview. Production dist must not contain a managerPreview chunk or reserved preview account addresses.

## Automated coverage

Latest integration verification: 591 tests passed in 29 files; lint and production build passed. Production output contains neither a managerPreview chunk nor reserved preview account addresses. Vite reports a non-blocking warning for the 509.87 kB shared application chunk. Browser checks confirmed package/card saves, updated values after refresh, a usable mobile menu and package editor, no page-wide horizontal overflow at 375px, and no captured application console errors. The preview remains running at http://127.0.0.1:5174/manager/packages.

The audit update preserved all 35 main audit rows, restored the prior FE-06 row and appended the integration row. DOCX structural validation passed; packaged visual rendering could not run because LibreOffice soffice.exe is unavailable.

The manager suite exercises role denial, live request authentication, pagination/query encoding, loading/error/retry/empty states, stale response protection, staff validation and ordered coach sports, duplicate submissions, server field errors, self-deactivation, confirmation cancellation, failed refresh after a successful write, BE-04 activity authentication and response mapping, supported action filters, exact BE-03 wire payloads, DTO numeric limits, Standard protection, tier ranks, preview response mapping, malformed catalog errors, cancelled edits, local-day filtering and immutable activity details. The inherited frontend suite covers reception and member payment screens as well as the shared session behavior.
