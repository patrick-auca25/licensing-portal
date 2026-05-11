# Bank Licensing & Compliance Portal — BNR

A web application for managing bank licence applications at the National Bank of Rwanda. Replaces the current manual process (email submissions, spreadsheet tracking) with a structured workflow, role-based access control, and a permanent audit trail.

---

## Stack

- **Java 21**, Spring Boot 3.2, Maven 3.9
- **PostgreSQL 15** — persistent storage, audit trail enforcement
- **Spring Security + JWT** — stateless authentication
- **Thymeleaf** — server-side rendered UI
- **Docker Compose** — local environment setup

---

## Prerequisites

```
Java 21+
Maven 3.9+
Docker + Docker Compose
```

Verify:
```bash
java -version
mvn -version
docker --version
```

---

## Running the Application

### Step 1 — Create and start the PostgreSQL container

```bash
docker run --name bnr_postgres \
  -e POSTGRES_DB=bnr_licensing \
  -e POSTGRES_USER=bnr_user \
  -e POSTGRES_PASSWORD=bnr_pass \
  -p 5432:5432 \
  -d postgres:15-alpine
```

Or using Docker Compose (creates and starts both database and app):

```bash
docker-compose up -d
```

### Step 2 — Start the application

```bash
mvn spring-boot:run
```

Application runs at: **http://localhost:8080**

On first start, the schema is created and seed data is loaded automatically. Subsequent restarts leave existing data untouched — no data loss on restart.

### Reset to clean state

```bash
docker stop bnr_postgres && docker rm bnr_postgres
# Then repeat Step 1 and Step 2
```

---

## Seed Accounts

Password for all accounts: `Password123!`

| Role | Email |
|---|---|
| APPLICANT | applicant@example.com |
| REVIEWER | reviewer@bnr.rw |
| APPROVER | approver@bnr.rw |
| ADMIN | admin@bnr.rw |



---

## Running Tests

```bash
mvn test
```

Tests cover the state machine — valid transitions, invalid transitions, terminal state enforcement, the four-eyes rule, role boundaries, concurrent access, and edge cases.



---

## API

Import `docs/BNR_Postman_Collection.json` into Postman. Collection variables are pre-set (`baseUrl`, tokens). Run the AUTH folder first — tokens save automatically to collection variables.

### Endpoints

```
POST   /api/auth/login

GET    /api/applications                        role-filtered
POST   /api/applications
GET    /api/applications/{id}
PUT    /api/applications/{id}                   DRAFT only

POST   /api/applications/{id}/submit
POST   /api/applications/{id}/start-review
POST   /api/applications/{id}/request-info
POST   /api/applications/{id}/resubmit
POST   /api/applications/{id}/complete-review
POST   /api/applications/{id}/approve
POST   /api/applications/{id}/reject

POST   /api/applications/{id}/documents
GET    /api/applications/{id}/documents         all versions
GET    /api/applications/{id}/documents/current

GET    /api/audit/applications/{id}
GET    /api/audit

POST   /api/users
GET    /api/users
PATCH  /api/users/{id}/deactivate
```

All error responses are structured — no stack traces:
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Four-eyes rule violation: the reviewer cannot also be the approver.",
  "timestamp": "2026-05-10T14:22:01"
}
```

---

## Application Workflow

```
DRAFT → SUBMITTED → UNDER_REVIEW ──→ REVIEWED → APPROVED (terminal)
                         └→ ADDITIONAL_INFO_REQUESTED      → REJECTED (terminal)
                                   └→ SUBMITTED (resubmit)
```

APPROVED and REJECTED have no outgoing transitions — enforced at the API level.

The reviewer of an application cannot make the final decision on it (four-eyes rule — enforced in backend service, not the UI).

---

## Docs

- `docs/DESIGN.md` — architecture, data model, decisions
- `docs/BNR_Postman_Collection.json` — API test collection
- `docs/BNR_Architecture_Document.docx` — architecture document
- `docs/BNR_SRS_Document.docx` — requirements specification
