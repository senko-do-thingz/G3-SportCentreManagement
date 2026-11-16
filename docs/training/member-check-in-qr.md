# Member check-in QR

Each Member has one fixed check-in QR. The QR content is the Member code (for example `MEM-0016`). It never changes, never expires and is not stored in the database; it is rendered on demand.

## Member side

`GET /api/v1/member/check-in-qr` (role MEMBER) returns `{ memberCode, qrContent }` for the signed-in Member. `qrContent` is always equal to the Member code. The page `/member/check-in-qr` (linked from My profile and My bookings) renders the QR in the browser and offers a "Download QR" PNG.

## Reception side

The QR is used for check-in only. It is not a credential: it cannot sign in, pay or edit a profile. A Receptionist or Manager scans the QR into the "Member code or QR scan" field on the Reception check-in page, which calls the single check-in endpoint `POST /api/v1/check-ins` with `{ identifier, bookingId?, note? }`.

`CheckInServiceImpl.recordCheckIn` looks up the Member by code and applies these rules:

| Case | Result | Check-in record |
| --- | --- | --- |
| Unknown Member code | 404 "Member not found" | none |
| Account `user_account.status` is not `ACTIVE` | 400 "Account is inactive, check-in denied" (`CHECK_IN_ACCOUNT_INACTIVE`) | none |
| Active Member | existing booking and package rules decide `ALLOWED` or `DENIED` | one row in `check_in` |

Arrival does not create session attendance or debit package credits.

## Reception camera scan

Reception can also scan the QR with the computer webcam instead of a hardware reader:

1. On the Reception check-in page (`/reception/check-in`), press "Scan QR" on the "Record arrival" card. The browser asks for camera permission the first time.
2. Hold the Member QR in front of the camera. When a QR is read, the Member code is trimmed and sent to the same `POST /api/v1/check-ins` endpoint. No other endpoint is used and the backend rules above are unchanged.
3. The result appears under the camera preview in the same style as a manual check-in:
   - Recorded: "Check-in successful - member may enter the centre", with the Member name, Member code and booking code.
   - Rejected: the reason returned by the backend, for example "Member not found", "Account is inactive, check-in denied", "No confirmed booking for today", an expired package or "Already checked in for this session".
4. Scan the next Member without reloading. While a check-in request is in flight, new detections are ignored, and the same code is ignored for 4 seconds after its result, so one QR held in front of the camera creates only one request.
5. Press "Close scanner" when done. The camera is released when the scanner is closed, when Reception leaves the page and when the browser hides the page.

The "Member code or QR scan" field remains available as a manual fallback.

Camera notes:

- Browsers only allow camera access on HTTPS or on `localhost`. On a plain HTTP address (for example `http://192.168.1.10:5173`) the scanner shows "This browser cannot open the camera here" and Reception must type the code or use a hardware reader.
- Clear messages are shown when camera permission is denied, when no camera is found, when the camera is already used by another application and when the browser does not support camera access.
- QR decoding runs in the browser with `@zxing/browser`; camera frames are never uploaded.

## Removed

The previous short-lived pass (`SCIN1.` tokens, 120 second expiry, single-use redemption, `POST /api/v1/member/check-in-pass` and `POST /api/v1/reception/check-in-pass/redeem`) was removed. The V43 table `member_check_in_pass` is no longer written or read. It is left in place because Flyway migrations are append-only; dropping it needs a new migration reviewed by the database owner.
