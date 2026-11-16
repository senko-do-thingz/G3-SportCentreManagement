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

## Removed

The previous short-lived pass (`SCIN1.` tokens, 120 second expiry, single-use redemption, `POST /api/v1/member/check-in-pass` and `POST /api/v1/reception/check-in-pass/redeem`) was removed. The V43 table `member_check_in_pass` is no longer written or read. It is left in place because Flyway migrations are append-only; dropping it needs a new migration reviewed by the database owner.
