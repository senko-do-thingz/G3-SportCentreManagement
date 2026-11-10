# Source Preservation Matrix

Consolidated branch `fix/flow-1-3-receptionist-concept-a-consolidated`, preservation commit `e0d5dade`, base origin/main `fd4e48b0`.
Old stack tip: `778ac5b5` (PR #280). `git diff 778ac5b5 e0d5dade` is empty: the consolidated tree equals the final stack tree.

Blob = git object id of the file at the old PR head. New blob = same file at the consolidated commit. A different new blob means a later RUI task also edited that file (shared stylesheet); the final version is kept.

| RUI | Old PR | Old base -> head | Path | Old blob | New blob | Coverage |
|---|---|---|---|---|---|---|
| RUI-00 Receptionist scoped UI foundation | #268 | `main` -> `0db8d16a` | `frontend/src/pages/reception/ReceptionConcept.tsx` | `2406c586` | `2406c586` | identical |
| RUI-00 Receptionist scoped UI foundation | #268 | `main` -> `0db8d16a` | `frontend/src/styles/receptionConcept.css` | `9a33bcdf` | `fa19b889` | superseded by later RUI edit, kept final |
| RUI-01 Action inbox | #272 | `fix/flow-1-receptionist-ui-foundation` -> `f433e943` | `frontend/src/pages/reception/ReceptionInboxPage.tsx` | `b613d94d` | `b613d94d` | identical |
| RUI-01 Action inbox | #272 | `fix/flow-1-receptionist-ui-foundation` -> `f433e943` | `frontend/src/styles/receptionConcept.css` | `8c42ae2d` | `fa19b889` | superseded by later RUI edit, kept final |
| RUI-02 Support requests | #271 | `fix/flow-3-receptionist-action-inbox-ui` -> `505ddee8` | `frontend/src/pages/reception/SupportRequestsPage.tsx` | `5322c35d` | `5322c35d` | identical |
| RUI-03 Member search and profile | #269 | `fix/flow-1-receptionist-support-ui` -> `f1cf97d4` | `frontend/src/pages/reception/MemberSearchPage.tsx` | `2e0a89fc` | `2e0a89fc` | identical |
| RUI-03 Member search and profile | #269 | `fix/flow-1-receptionist-support-ui` -> `f1cf97d4` | `frontend/src/styles/receptionConcept.css` | `6eef6d33` | `fa19b889` | superseded by later RUI edit, kept final |
| RUI-04 Registration and renewal | #270 | `fix/flow-1-receptionist-member-search-ui` -> `02735564` | `frontend/src/pages/reception/RegisterRenewPage.tsx` | `9482740f` | `9482740f` | identical |
| RUI-04 Registration and renewal | #270 | `fix/flow-1-receptionist-member-search-ui` -> `02735564` | `frontend/src/styles/receptionConcept.css` | `4d145b3e` | `fa19b889` | superseded by later RUI edit, kept final |
| RUI-05 Check-in | #273 | `fix/flow-1-receptionist-register-renew-ui` -> `c81944ca` | `frontend/src/pages/reception/CheckInPage.tsx` | `2b01e8a1` | `2b01e8a1` | identical |
| RUI-05 Check-in | #273 | `fix/flow-1-receptionist-register-renew-ui` -> `c81944ca` | `frontend/src/styles/receptionConcept.css` | `153527b9` | `fa19b889` | superseded by later RUI edit, kept final |
| RUI-06 Assisted booking | #274 | `fix/flow-1-receptionist-check-in-ui` -> `9643632a` | `frontend/src/pages/reception/ReceptionBookingsPage.tsx` | `26da3dc6` | `26da3dc6` | identical |
| RUI-06 Assisted booking | #274 | `fix/flow-1-receptionist-check-in-ui` -> `9643632a` | `frontend/src/styles/receptionConcept.css` | `bc7360d3` | `fa19b889` | superseded by later RUI edit, kept final |
| RUI-07 Payment requests | #276 | `fix/flow-2-receptionist-assisted-booking-ui` -> `c4bfe0b4` | `frontend/src/pages/reception/PaymentRequestsPage.tsx` | `4c0a6897` | `4c0a6897` | identical |
| RUI-07 Payment requests | #276 | `fix/flow-2-receptionist-assisted-booking-ui` -> `c4bfe0b4` | `frontend/src/styles/receptionConcept.css` | `61fa42be` | `fa19b889` | superseded by later RUI edit, kept final |
| RUI-08 Payment verification, QR display, receipt | #277 | `fix/flow-3-receptionist-payment-requests-ui` -> `25839237` | `frontend/src/pages/reception/ReceptionPaymentPage.tsx` | `aa55c876` | `aa55c876` | identical |
| RUI-08 Payment verification, QR display, receipt | #277 | `fix/flow-3-receptionist-payment-requests-ui` -> `25839237` | `frontend/src/pages/reception/ReceptionQrDisplayPage.tsx` | `e7e10052` | `e7e10052` | identical |
| RUI-08 Payment verification, QR display, receipt | #277 | `fix/flow-3-receptionist-payment-requests-ui` -> `25839237` | `frontend/src/pages/reception/ReceptionReceiptPage.tsx` | `a3d6a63b` | `a3d6a63b` | identical |
| RUI-08 Payment verification, QR display, receipt | #277 | `fix/flow-3-receptionist-payment-requests-ui` -> `25839237` | `frontend/src/styles/receptionConcept.css` | `a2f28d8f` | `fa19b889` | superseded by later RUI edit, kept final |
| RUI-09 Refund requests and detail | #278 | `fix/flow-3-receptionist-payment-details-ui` -> `6d9c6ef3` | `frontend/src/pages/reception/RefundDetailPage.tsx` | `7bac02b0` | `7bac02b0` | identical |
| RUI-09 Refund requests and detail | #278 | `fix/flow-3-receptionist-payment-details-ui` -> `6d9c6ef3` | `frontend/src/pages/reception/RefundRequestsPage.tsx` | `a7f6a1b7` | `a7f6a1b7` | identical |
| RUI-09 Refund requests and detail | #278 | `fix/flow-3-receptionist-payment-details-ui` -> `6d9c6ef3` | `frontend/src/styles/receptionConcept.css` | `e45ce457` | `fa19b889` | superseded by later RUI edit, kept final |
| RUI-10 Wallet top-up queue | #279 | `fix/flow-3-receptionist-refund-ui` -> `666cff49` | `frontend/src/pages/reception/WalletTopUpQueuePage.tsx` | `50854ad3` | `50854ad3` | identical |
| RUI-10 Wallet top-up queue | #279 | `fix/flow-3-receptionist-refund-ui` -> `666cff49` | `frontend/src/styles/receptionConcept.css` | `fa19b889` | `fa19b889` | identical |
| RUI-11 Wallet withdrawal queue | #280 | `fix/flow-3-receptionist-wallet-topups-ui` -> `778ac5b5` | `frontend/src/pages/reception/WalletWithdrawalQueuePage.tsx` | `da4cf469` | `da4cf469` | identical |

Backup: all 12 old branches remain on origin unchanged and are not deleted. Unique paths across all tasks: 16. Added: 2 (ReceptionConcept.tsx, receptionConcept.css). Deleted or renamed: 0.

Stylesheet check: for each of the 12 old heads, every non-empty line of its receptionConcept.css also appears in the consolidated file (0 missing lines for all 12).
