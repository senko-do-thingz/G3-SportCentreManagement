# Existing API conventions

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

## Verified behavior and compatibility limits

| Area | Observed fact | Limit |
| --- | --- | --- |
| Routes | Existing /api/v1 and singular/plural arrival aliases | No new training URL approved |
| Success bodies | Flat DTO, PageResponse, Spring Page, legacy lists and204 vary | No universal envelope |
| Errors | Security entrypoint/denied handlers and global timestamp/status/message maps; optional details/code | No universal code; generic handler mapping alone does not prove applicability |
| Dates | Injected Clock Asia/Ho_Chi_Minh; LocalDate/LocalDateTime; error builder uses plain now | No newly approved UTC/offset or null policy |
| Paging | 20/100 and Coach62-day bound occur in other source APIs | Never imposed on arrival history or new training reads automatically |
| Arrival | POST201 for saved ALLOWED/DENIED; roles MANAGER/RECEPTIONIST | Not participation, idempotent replay or an extra debit |
| History | At least memberId or date, combined filters retained, unpaged list | No new envelope, ceiling,404 or stable tie ordering |
| Security | Fresh JWT user details; method roles; Coach service scope pattern | No inherited Manager training grants |
| CORS | Authorization/Content-Type allowed; no new concurrency header exposed | Header design remains pending |
| Schema | Training scaffold literals and uniqueness exist | Not approved API enums/metrics or deployed-schema proof |

Full current spans and symbols (`evidence/source-citations.json` - local review reference, not published). Existing test methods were inspected, not run.
