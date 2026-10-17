# BE-10 facility lookup

`GET /api/v1/facilities` is an authenticated, MANAGER-only directory for FE-07
Manager Schedule. Anonymous requests return 401. MEMBER, COACH and RECEPTIONIST
requests return 403, following the existing authentication/error envelopes.

The response is an unpaginated JSON array, sorted by facility name ascending,
then ID ascending using database ordering. There are no request parameters.
An empty table returns HTTP 200 with `[]`.

Each entry has exactly these fields:

| Field | Type | Source |
| --- | --- | --- |
| id | number | Persisted facility primary key |
| name | string | Persisted display name |
| active | boolean | facility.is_active (SQL Server BIT NOT NULL) |

Inactive facilities remain in the directory for historical calendar filtering.
FE-07 excludes `active: false` from editor choices. Existing BE-10 create/update
validation still requires an active assigned facility; the lookup changes no
scheduling, conflict or booking rules. Editing an inactive assignment requires
an explicit active replacement, as before. This endpoint does not silently
replace assignments or write records.

The caller was verified at `origin/feat/f2-manager-screens`, commit
`eda04a5d40b5ebcd3f3add07512f3fd9e4122140`, in
`frontend/src/api/facilitiesApi.ts`, `ManagerSchedulePage.tsx` and
`SessionEditorModal.tsx`. That frontend is not yet on the backend fix's main
base. It consumes the array directly without an envelope or normalization.
Its 404-only manual ID fallback remains compatible with older deployments.
403 and 500 remain errors. Optional sport/capacity fields are not required by
the caller and are omitted. No pagination, migrations or frontend production
changes are needed for this directory.

SQL Server integration coverage is registered in the required CI suite manifest;
local unit tests do not establish database or browser acceptance.
