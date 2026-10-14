# FE-06 verification

Implementation branch: feat/f1-manager-screens.

## Run locally

From this repository's frontend folder, run npm ci, then npm run dev:preview. Open http://localhost:5173/login and choose Open manager preview. The preview does not require a backend or a real account. For an IP-bound server, use npm run dev:preview -- --host 127.0.0.1 and open http://127.0.0.1:5173/login.

Use npm run dev for live mode. The default API URL is http://localhost:8080/api/v1. Preview is off by default, available only in development and requires VITE_MANAGER_PREVIEW=true or the manager-preview Vite mode. Its sign-in controls and fixture adapter are excluded from production builds. Preview tokens are synthetic and use the existing auth provider and role guards; they never grant access to the real backend.

All preview data and simulated writes are in src/api/mock/managerPreview.ts. Changes are held in memory, reset on a full reload and never written to the backend. The preview covers manager users, sports, all 36 packages across six sports, three card tiers and activity records. Preview-only authentication uses reserved @preview.sportify.local accounts. Real account tokens cannot invoke unverified mutations even when the preview flag is enabled.

## Integration status

| Screen | Live contract | Current limitation |
| --- | --- | --- |
| User Management | GET/POST /manager/users; PATCH /manager/users/{id}/status; GET /sports | Verified against V1 ManagerUserController and DTOs. The active backend checkout does not contain BE-01, so live availability was not verified. |
| Packages | POST /packages creation verified against V1 DTO | GET /manager/packages and PUT /packages/{id} are proposed BE-03 contracts. Manager listing and updates are gated until released fields are verified. |
| Cards | Existing public CardTierResponse reused for the preview | GET /manager/card-tiers and PUT /membership-cards/tiers/{id} require BE-03 confirmation. They are gated for real accounts. |
| Activity | Actor lookup reuses verified GET /manager/users | GET /manager/activity-logs requires BE-04 confirmation of response fields, action enum and calendar/date inclusivity. Date strings are used only in preview; live requests are gated. |

No production fallback substitutes the public active-only package/tier list for a manager catalog. No backend file has been changed. No real account creation or catalog mutation was used during browser review.

## Design references and intentional adjustments

All nine images were opened before implementation from the plan's reference folder: ../V1/G3-SportCentreManagement-main/context/Flow 1 - User and membership management/Flow 1 - Manager.

- User Management and Add Staff overlay: preserve the shared blue/white shell, filters and account columns. Omit unsupported global role counts and arbitrary profile edits. Add the required temporary password and ordered coach sports instead of unsupported skills/certificate fields. Status confirmation replaces arbitrary Edit.
- Packages: one catalog implements all three screenshot segments, with sport, training type and status filters. Twenty-row pagination is explicitly local. Code, sport and training type remain immutable during edits.
- Cards: omit unsupported Create tier. Standard stays read-only, free, active, zero-discount and without an expiry. Preserve unknown and inactive tiers. Package discounts do not stack or apply to single visits.
- Activity: add required actor/date/action filters and pagination. Actor selection searches all account pages. Action codes use a text field because the BE-04 action enum is not yet released. Details use the existing row only.
- Manager timestamps use dd/MM/yyyy and center-local time. Money uses the shared VND formatter. Navigation, API refresh, auth storage and modal keyboard handling reuse the foundation.

## Manual checks

1. Access: log out and open each /manager route. Expect login. Use Check member access on the preview login screen. Expect 403 without manager data. Return to login and choose Open manager preview.
2. Loading: open /manager?previewState=slow, /manager/packages?previewState=slow, /manager/cards?previewState=slow and /manager/activity?previewState=slow. Expect a visible loading state and no stale rows under changed filters.
3. Empty: use the same routes with previewState=empty. Expect a distinct empty state. Search for a nonexistent account/package/action in normal preview and expect no matching results, then clear filters.
4. Error and retry: use previewState=error. Expect a service error rather than a successful empty list. Try again preserves filters. Remove the parameter and reload for normal data. In live mode, verify missing BE-03/BE-04 contracts show a dependency error.
5. User filters: combine role/status/search, inspect the changed rows, move to page 2, then change a filter and expect page 1. Rapidly change filters and expect only the newest response.
6. Staff form: submit blank fields and verify focus on the first invalid input. Validate email/phone/password. Choose Coach, select Basketball then Football and verify Basketball is primary. Change to Receptionist and verify sports are cleared. Duplicate an existing email/phone and expect inline server messages with entered fields retained. Press Enter twice and expect one creation. Cancel/Escape sends no mutation and clears the temporary password by unmounting the editor.
7. User status: verify your own Deactivate is disabled. Confirm another account's status change; cancel first and verify no change. Successful confirmation refreshes the current query. A failed refresh must report the refresh error without reopening the successful creation form.
8. Packages: filter every sport, both training types and inactive status. Create a package with a unique code, a positive numeric price, whole duration/session counts and 60 or 90 minutes. Edit price and status; code/sport/type stay read-only. Reject zero/negative prices, fractional counts and blank numeric fields. Cancel leaves data unchanged. Confirm deactivation, then find the item under inactive packages.
9. Cards: Standard has a default-tier badge and no editor. Edit Gold/VIP; check positive fee, whole-month validity and discount bounds including 0 and 100. Reject 101/-1 and blank numbers. Cancel keeps the old fee. Save an inactive tier and verify its label. Discount policy remains visible.
10. Activity: filter actor by searching the paginated picker, then combine action/search/from/to. Enter a reversed range and expect an inline error with no log request. Check that a local calendar day is not shifted. Open details and expect no additional endpoint, then Refresh. History has no edit/delete controls.
11. Responsive: inspect each page and editor at 375px and desktop. Filters/forms/cards stack, the navigation menu remains usable, and tables scroll inside their panels without making the whole page wider. Verify visible focus, modal Tab/Shift+Tab trapping and Escape dismissal.
12. Developer checks: run npm run test, npm run lint and npm run build. Inspect the browser console in preview. Production dist must not contain a managerPreview chunk or reserved preview account addresses.

## Automated coverage

The manager suite exercises role denial, live request authentication, pagination/query encoding, loading/error/retry/empty states, stale response protection, staff validation and ordered coach sports, duplicate submissions, server field errors, self-deactivation, confirmation cancellation, failed refresh after a successful write, proposed-contract gates, package numeric validation/payloads, Standard protection, extra inactive tiers, cancelled edits, local-day filtering and immutable activity details. The full inherited frontend suite is also run.
