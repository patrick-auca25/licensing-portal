# Design Document

## Architecture

Single Spring Boot application, three-tier layered architecture: controllers → services → JPA → PostgreSQL. No microservices — the domain is bounded, the team is one engineer, and the 48-hour window makes distributed complexity a liability.

The frontend is Thymeleaf (server-side rendered). All data is fetched via REST API calls from JavaScript. The HTML pages are publicly accessible — security is enforced entirely at the `/api/**` layer via JWT + `@PreAuthorize`.

**Request flow:**
```
JwtAuthFilter → @PreAuthorize → Controller → StateMachineService → @Transactional → AuditService → Response
```

---

## Data Model

**users** — email, BCrypt password hash, role (VARCHAR), active flag

**applications** — institution details, status (VARCHAR), `version` column for optimistic locking, `reviewer_id` and `approver_id` to track who touched the application

**documents** — file metadata (path, size, type, uploader), `version_number` and `current` boolean for document versioning. Files stored on local filesystem.

**audit_log** — actor, action, state_before, state_after, timestamp. No `updated_at` column. `REVOKE UPDATE, DELETE ON audit_log FROM PUBLIC` in schema enforces immutability at the database level.

---

## State Machine

```
DRAFT → SUBMITTED → UNDER_REVIEW → REVIEWED → APPROVED (terminal)
                         └→ ADDITIONAL_INFO_REQUESTED     → REJECTED (terminal)
                                   └→ SUBMITTED
```

All transitions are validated in `StateMachineService` before any write occurs. The transition matrix and required role per transition are expressed as static Maps — easy to read, easy to test.

Terminal states (APPROVED, REJECTED) are checked first in every transition method. If the application is in a terminal state, the method throws immediately regardless of what transition was requested.

---

## Roles

| Role | What they can do |
|---|---|
| APPLICANT | Submit applications, upload documents, respond to info requests. Own applications only. |
| REVIEWER | Claim applications, request info, submit review recommendation. Cannot make final decisions. |
| APPROVER | Approve or reject reviewed applications. Cannot review (four-eyes). |
| ADMIN | User management, read-only access to all data. No involvement in licensing decisions. |

Role enforcement is in `@PreAuthorize` on every controller method. The frontend hides buttons as a UX convenience — it provides no security.

---

## Four-Eyes Rule

When a REVIEWER claims an application, their user ID is stored as `reviewer_id`. On APPROVE or REJECT, `StateMachineService` checks:

```java
if (app.getReviewer().getId().equals(approver.getId())) {
    throw new AccessDeniedException("Four-eyes rule violation...");
}
```

This runs in the service layer. Bypassing the frontend and calling the API directly still hits this check.

---

## Concurrency

`@Version` on the `Application` entity. Hibernate includes `WHERE id=? AND version=?` in every UPDATE. If two users act simultaneously, the second write throws `ObjectOptimisticLockingFailureException` — caught by the global exception handler and returned as HTTP 409.

This is demonstrated in `StateMachineServiceTest.startReview_concurrentAccess_throwsOptimisticLockException()`.

---

## Audit Trail

Two enforcement layers:

1. `AuditService` only calls `save()` — no update or delete operations exist in the codebase
2. `REVOKE UPDATE, DELETE ON audit_log FROM PUBLIC` in `schema.sql` — the database refuses modifications regardless of application code

`AuditService` uses `Propagation.REQUIRED` so audit entries are written in the same transaction as the state change they record. This prevents audit entries from being written for operations that were subsequently rolled back.

---

## Document Versioning

On resubmission after ADDITIONAL_INFO_REQUESTED, previous documents are never deleted or overwritten. Each upload increments `version_number` and sets `current = true` on the new record and `current = false` on all previous records for that category. All versions remain queryable.

---



