# MW-04 - Late and unmatched bank transfers

Status: PROPOSED for CODEOWNER review (PR #255). Manual process; no bank API is involved.

A wallet is credited only when a Receptionist matches one bank statement line to one top-up request: the exact
amount, the transfer note `SPW{id}` and the receiving account stored on the request. Showing a QR code never credits
anything. A Member has at most one in-flight wallet operation at a time (`wallet_pending_operation`).

## Cases

| Bank statement shows | Request status | What reception does | Wallet effect |
|---|---|---|---|
| Exact amount, note `SPW{id}` | `PENDING` | Confirm with the bank reference | Credited once (`TOPUP-{id}`) |
| Exact amount, note `SPW{id}`, arrived after the Member cancelled | `CANCELLED` (real money only) | **Review late payment**: confirm with the bank reference and a required note describing the late transfer | Credited once; the request becomes `CONFIRMED`, reviewer, time, reference and note are kept |
| Exact amount, note `SPW{id}` | `CONFIRMED` | Nothing. The bank reference is unique, so a second confirmation is refused | None (no second credit) |
| Different amount, missing or unknown note, or a request reception already `REJECTED` | any | Do not confirm. Record the statement line and return the money to the sender's account by bank transfer, outside the wallet. The Member may create a new request | None |
| Money for a request that has no stored receiving account | n/a | Cannot happen: such a request cannot be created (`TOPUP_NOT_PAYABLE`) and the database refuses a real-money row without a receiving account (`ck_wallet_topup_beneficiary`) | None |

## Why a cancelled request can still be confirmed

A Member can cancel a pending request (for example to change the amount), but they may already have sent the money.
Keeping the cancelled request confirmable means the money is never unclaimable: it is still tied to one request, one
transfer note and one ledger key, so it can be credited at most once. Demo (`SIMULATED`) requests are never confirmable
after cancellation because no money moved.

## Lost confirmation responses

If the Receptionist's confirmation response is lost or reports a conflict, the queue page re-reads the request from the
server. When it is already `CONFIRMED` with the same amount and bank reference, the page shows that confirmed outcome
and does not send a second confirmation. The server would refuse one anyway (409 `TOPUP_NOT_PENDING`, unique ledger key
and unique bank reference).

## Open points for CODEOWNER

- Who performs the bank return for unmatched money (Receptionist or Manager) and how it is recorded. MW-04 does not add
  a refund-to-sender record; it only guarantees that unmatched money is never credited to a wallet.
- Whether a cancelled real-money request should expire after a set period, after which late money is returned
  instead of credited.
