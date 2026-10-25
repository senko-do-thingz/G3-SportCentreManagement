# DTO families and sample review records

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

## Candidate family metadata

New wire names, DTO layouts, nullability, lengths, metrics, enums and numeric bounds remain unsigned. Review-value types validate documentation consistency only. Existing CheckInRequest/Response declarations are bounded source facts, not a universal serializer guarantee. Pure reads use selector families and no mutation body or revision input; expected-revision families occur only on edit/correction/publication proposals.

### COMMON.CONTEXT

- Id: COMMON.CONTEXT
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Principal-derived actor and separately verified session/member/assignment context.
- Decision: D-12
- Status: PROPOSED
- Nullable: TBD_POLICY_D-12
- Blank and coercion: TBD_POLICY_D-12
- Length or numeric constraints: TBD_POLICY_D-12
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### COMMON.EXPECTED_REVISION

- Id: COMMON.EXPECTED_REVISION
- Wire name: TBD_AFTER_APPROVAL
- Review value type: string
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Opaque expected revision in review; header versus body and token format unresolved.
- Decision: D-10
- Status: PROPOSED
- Nullable: TBD_POLICY_D-10
- Blank and coercion: TBD_POLICY_D-10
- Length or numeric constraints: TBD_POLICY_D-10
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### COMMON.CORRECTION_REASON

- Id: COMMON.CORRECTION_REASON
- Wire name: TBD_AFTER_APPROVAL
- Review value type: string
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Reason for attributable change; mandatory trigger and Member redaction unresolved.
- Decision: D-12
- Status: PROPOSED
- Nullable: TBD_POLICY_D-12
- Blank and coercion: TBD_POLICY_D-12
- Length or numeric constraints: TBD_POLICY_D-12
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### COMMON.HISTORY_WINDOW

- Id: COMMON.HISTORY_WINDOW
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_OPTIONAL
- Description: Historical entitlement and bounded temporal selection, distinct from current enrollment.
- Decision: D-12
- Status: PROPOSED
- Nullable: TBD_POLICY_D-12
- Blank and coercion: TBD_POLICY_D-12
- Length or numeric constraints: TBD_POLICY_D-12
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### COMMON.PAGE_SELECTION

- Id: COMMON.PAGE_SELECTION
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_OPTIONAL
- Description: Page or cursor selection and stable ordering; no new default or maximum selected.
- Decision: D-10
- Status: PROPOSED
- Nullable: TBD_POLICY_D-10
- Blank and coercion: TBD_POLICY_D-10
- Length or numeric constraints: TBD_POLICY_D-10
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### PLAN.TARGET

- Id: PLAN.TARGET
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Personal target versus class/session assignment; templates cannot copy personal narratives.
- Decision: D-02
- Status: PROPOSED
- Nullable: TBD_POLICY_D-02
- Blank and coercion: TBD_POLICY_D-02
- Length or numeric constraints: TBD_POLICY_D-02
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### PLAN.ACTIVITIES

- Id: PLAN.ACTIVITIES
- Wire name: TBD_AFTER_APPROVAL
- Review value type: array
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Ordered permitted content; fields and editing stage allowlist unresolved.
- Decision: D-02
- Status: PROPOSED
- Nullable: TBD_POLICY_D-02
- Blank and coercion: TBD_POLICY_D-02
- Length or numeric constraints: TBD_POLICY_D-02
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### PLAN.AUDIENCE

- Id: PLAN.AUDIENCE
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Entitled audience, snapshot versus dynamic membership and publication visibility.
- Decision: D-02
- Status: PROPOSED
- Nullable: TBD_POLICY_D-02
- Blank and coercion: TBD_POLICY_D-02
- Length or numeric constraints: TBD_POLICY_D-02
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### RESULT.OBSERVATIONS

- Id: RESULT.OBSERVATIONS
- Wire name: TBD_AFTER_APPROVAL
- Review value type: array
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Approved observations, units and precision; absent, zero and incomplete differ.
- Decision: D-03
- Status: PROPOSED
- Nullable: TBD_POLICY_D-03
- Blank and coercion: TBD_POLICY_D-03
- Length or numeric constraints: TBD_POLICY_D-03
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### ATTEND.MEANING

- Id: ATTEND.MEANING
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Participation meaning, eligibility and uniqueness; schema literals are not adopted API enums.
- Decision: D-13
- Status: PROPOSED
- Nullable: TBD_POLICY_D-13
- Blank and coercion: TBD_POLICY_D-13
- Length or numeric constraints: TBD_POLICY_D-13
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### PROGRESS.ASSESSMENT

- Id: PROGRESS.ASSESSMENT
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Evaluation and permitted comments derived from authorized training evidence.
- Decision: D-03
- Status: PROPOSED
- Nullable: TBD_POLICY_D-03
- Blank and coercion: TBD_POLICY_D-03
- Length or numeric constraints: TBD_POLICY_D-03
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### PROGRESS.CLASSIFICATION

- Id: PROGRESS.CLASSIFICATION
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: CONDITIONAL_REQUIRED
- Description: Explicit permitted audience; no private-note entitlement is granted here.
- Decision: D-12
- Status: PROPOSED
- Nullable: TBD_POLICY_D-12
- Blank and coercion: TBD_POLICY_D-12
- Length or numeric constraints: TBD_POLICY_D-12
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### READ.PROJECTION

- Id: READ.PROJECTION
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: RESPONSE_ONLY
- Description: Allowlisted authorized fields; peer identities and restricted narratives omitted.
- Decision: D-12
- Status: PROPOSED
- Nullable: TBD_POLICY_D-12
- Blank and coercion: TBD_POLICY_D-12
- Length or numeric constraints: TBD_POLICY_D-12
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### COMMON.PROVENANCE

- Id: COMMON.PROVENANCE
- Wire name: TBD_AFTER_APPROVAL
- Review value type: object
- Wire type: TBD_AFTER_APPROVAL
- Presence: RESPONSE_ONLY
- Description: Server-attributed actor, subject, occurred/recorded time, revision and permitted origin.
- Decision: D-12
- Status: PROPOSED
- Nullable: TBD_POLICY_D-12
- Blank and coercion: TBD_POLICY_D-12
- Length or numeric constraints: TBD_POLICY_D-12
- Unknown fields: TBD_POLICY_D-10
- Sensitive: Member training data; allowlist by audience

### EXISTING.CHECKIN_REQUEST

- Id: EXISTING.CHECKIN_REQUEST
- Status: OBSERVED_EXISTING
- Source: SRC-CHECKIN-REQUEST
- Fields: [{"presence": "REQUIRED", "type": "string", "validation": "NotBlank", "wire_name": "identifier"}, {"presence": "OPTIONAL", "type": "Long", "validation": "No explicit positive constraint in this DTO", "wire_name": "bookingId"}, {"presence": "OPTIONAL", "type": "string", "validation": "No explicit length limit in this DTO", "wire_name": "note"}]
- Unknown fields: NOT_VERIFIED
- Null inclusion: NOT_VERIFIED

### EXISTING.CHECKIN_FILTERS

- Id: EXISTING.CHECKIN_FILTERS
- Status: OBSERVED_EXISTING
- Source: SRC-CHECKIN
- Fields: [{"presence": "OPTIONAL", "type": "Long", "wire_name": "memberId"}, {"presence": "OPTIONAL", "type": "ISO LocalDate", "wire_name": "date"}]
- Validation: At least memberId or date; when both given, retain Member filter
- Unknown fields: NOT_VERIFIED
- Null inclusion: NOT_VERIFIED

### EXISTING.CHECKIN_RESPONSE

- Id: EXISTING.CHECKIN_RESPONSE
- Status: OBSERVED_EXISTING
- Source: SRC-CHECKIN-RESPONSE
- Container: Single DTO on write; list of DTOs on staff history
- Fields: [{"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "Long", "wire_name": "id"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "Long", "wire_name": "memberId"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "String", "wire_name": "memberName"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "Long", "wire_name": "membershipId"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "Long", "wire_name": "bookingId"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "String", "wire_name": "bookingCode"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "Long", "wire_name": "packageRegistrationId"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "LocalDateTime", "wire_name": "checkedInAt"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "String", "wire_name": "result"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "String", "wire_name": "denialReason"}, {"presence": "DTO_DECLARATION_NOT_NON_NULL_GUARANTEE", "type": "String", "wire_name": "note"}]
- Null inclusion: NOT_VERIFIED
- Unknown fields: NOT_VERIFIED
- Limitation: Field declarations do not establish serializer null inclusion or offset formatting. Examples are synthetic source-shape discussions, not executed responses.

## Sanitized synthetic examples

### F4-ATTEND-empty

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-ATTEND-empty",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-ATTEND",
  "operation_id": "F4-ATTEND-01",
  "variant": "empty",
  "expected_review_validity": true,
  "empty_meaning": "No authorized data projected; not proof of absence/denial",
  "fields": []
}
```

### F4-ATTEND-invalid

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-ATTEND-invalid",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-ATTEND",
  "operation_id": "F4-ATTEND-01",
  "variant": "invalid",
  "expected_review_validity": false,
  "empty_meaning": null,
  "fields": [
    {
      "family_id": "COMMON.CONTEXT",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": 101
    },
    {
      "family_id": "ATTEND.MEANING",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional ATTEND.MEANING; policy not selected"
      }
    }
  ]
}
```

### F4-ATTEND-valid

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-ATTEND-valid",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-ATTEND",
  "operation_id": "F4-ATTEND-01",
  "variant": "valid",
  "expected_review_validity": true,
  "empty_meaning": null,
  "fields": [
    {
      "family_id": "COMMON.CONTEXT",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional COMMON.CONTEXT; policy not selected"
      }
    },
    {
      "family_id": "ATTEND.MEANING",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional ATTEND.MEANING; policy not selected"
      }
    }
  ]
}
```

### F4-PLAN-empty

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-PLAN-empty",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-PLAN",
  "operation_id": "F4-PLAN-01",
  "variant": "empty",
  "expected_review_validity": true,
  "empty_meaning": "No authorized data projected; not proof of absence/denial",
  "fields": []
}
```

### F4-PLAN-invalid

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-PLAN-invalid",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-PLAN",
  "operation_id": "F4-PLAN-01",
  "variant": "invalid",
  "expected_review_validity": false,
  "empty_meaning": null,
  "fields": [
    {
      "family_id": "PLAN.TARGET",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": 101
    },
    {
      "family_id": "PLAN.ACTIVITIES",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": [
        {
          "synthetic_content": "Conditional PLAN.ACTIVITIES; fields and constraints unapproved"
        }
      ]
    },
    {
      "family_id": "PLAN.AUDIENCE",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional PLAN.AUDIENCE; policy not selected"
      }
    }
  ]
}
```

### F4-PLAN-valid

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-PLAN-valid",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-PLAN",
  "operation_id": "F4-PLAN-01",
  "variant": "valid",
  "expected_review_validity": true,
  "empty_meaning": null,
  "fields": [
    {
      "family_id": "PLAN.TARGET",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional PLAN.TARGET; policy not selected"
      }
    },
    {
      "family_id": "PLAN.ACTIVITIES",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": [
        {
          "synthetic_content": "Conditional PLAN.ACTIVITIES; fields and constraints unapproved"
        }
      ]
    },
    {
      "family_id": "PLAN.AUDIENCE",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional PLAN.AUDIENCE; policy not selected"
      }
    }
  ]
}
```

### F4-PROGRESS-empty

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-PROGRESS-empty",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-PROGRESS",
  "operation_id": "F4-PROGRESS-01",
  "variant": "empty",
  "expected_review_validity": true,
  "empty_meaning": "No authorized data projected; not proof of absence/denial",
  "fields": []
}
```

### F4-PROGRESS-invalid

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-PROGRESS-invalid",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-PROGRESS",
  "operation_id": "F4-PROGRESS-01",
  "variant": "invalid",
  "expected_review_validity": false,
  "empty_meaning": null,
  "fields": [
    {
      "family_id": "COMMON.CONTEXT",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": 101
    },
    {
      "family_id": "PROGRESS.ASSESSMENT",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional PROGRESS.ASSESSMENT; policy not selected"
      }
    },
    {
      "family_id": "PROGRESS.CLASSIFICATION",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional PROGRESS.CLASSIFICATION; policy not selected"
      }
    }
  ]
}
```

### F4-PROGRESS-valid

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-PROGRESS-valid",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-PROGRESS",
  "operation_id": "F4-PROGRESS-01",
  "variant": "valid",
  "expected_review_validity": true,
  "empty_meaning": null,
  "fields": [
    {
      "family_id": "COMMON.CONTEXT",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional COMMON.CONTEXT; policy not selected"
      }
    },
    {
      "family_id": "PROGRESS.ASSESSMENT",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional PROGRESS.ASSESSMENT; policy not selected"
      }
    },
    {
      "family_id": "PROGRESS.CLASSIFICATION",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional PROGRESS.CLASSIFICATION; policy not selected"
      }
    }
  ]
}
```

### F4-RESULT-empty

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-RESULT-empty",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-RESULT",
  "operation_id": "F4-RESULT-01",
  "variant": "empty",
  "expected_review_validity": true,
  "empty_meaning": "No authorized data projected; not proof of absence/denial",
  "fields": []
}
```

### F4-RESULT-invalid

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-RESULT-invalid",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-RESULT",
  "operation_id": "F4-RESULT-01",
  "variant": "invalid",
  "expected_review_validity": false,
  "empty_meaning": null,
  "fields": [
    {
      "family_id": "COMMON.CONTEXT",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": 101
    },
    {
      "family_id": "RESULT.OBSERVATIONS",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": [
        {
          "synthetic_content": "Conditional RESULT.OBSERVATIONS; fields and constraints unapproved"
        }
      ]
    }
  ]
}
```

### F4-RESULT-valid

NON_BINDING_EXAMPLE

```json
{
  "example_id": "F4-RESULT-valid",
  "label": "NON_BINDING_EXAMPLE",
  "x_approval_status": "DRAFT_NOT_SIGNED",
  "schema_kind": "REVIEW_RECORD_NOT_WIRE_DTO",
  "group": "F4-RESULT",
  "operation_id": "F4-RESULT-01",
  "variant": "valid",
  "expected_review_validity": true,
  "empty_meaning": null,
  "fields": [
    {
      "family_id": "COMMON.CONTEXT",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": {
        "synthetic_context": "S-401/M-201",
        "meaning": "Conditional COMMON.CONTEXT; policy not selected"
      }
    },
    {
      "family_id": "RESULT.OBSERVATIONS",
      "wire_name": "TBD_AFTER_APPROVAL",
      "review_value": [
        {
          "synthetic_content": "Conditional RESULT.OBSERVATIONS; fields and constraints unapproved"
        }
      ]
    }
  ]
}
```

## Existing arrival DTO shape discussion

NON_BINDING_EXAMPLE

```json
{
  "label": "NON_BINDING_EXAMPLE",
  "status": "OBSERVED_DTO_SHAPE_EXAMPLE_NOT_EXECUTED",
  "source_refs": [
    "SRC-CHECKIN-REQUEST",
    "SRC-CHECKIN-RESPONSE",
    "SRC-ARRIVAL"
  ],
  "limitations": "Synthetic partial projection of declared DTO fields; omitted nullable fields and exact runtime serialization are not verified. No actual request was sent. Denial reason wording below is illustrative.",
  "examples": [
    {
      "id": "EXISTING-ARRIVAL-request",
      "kind": "REQUEST",
      "payload": {
        "identifier": "SYNTHETIC-MEMBER-201",
        "bookingId": 501,
        "note": "Synthetic front desk fixture"
      }
    },
    {
      "id": "EXISTING-ARRIVAL-denied",
      "kind": "RESPONSE_PROJECTION",
      "payload": {
        "id": 901,
        "memberId": 201,
        "memberName": "Synthetic Member",
        "bookingId": 501,
        "bookingCode": "SYNTHETIC-BOOKING-501",
        "checkedInAt": "2026-10-25T09:00:00",
        "result": "DENIED",
        "denialReason": "Synthetic duplicate visit reason; exact source wording not promised",
        "note": "Synthetic front desk fixture"
      }
    },
    {
      "id": "EXISTING-ARRIVAL-empty-history",
      "kind": "HISTORY",
      "payload": []
    }
  ]
}
```

The existing response example is a partial synthetic projection, not an executed response or a new wire schema. Null inclusion and exact serializer formatting remain NOT_VERIFIED. Internal model schema (`schemas/canonical-contract-model.schema.json` - local review reference, not published) validates documentation metadata, not runtime DTOs.
