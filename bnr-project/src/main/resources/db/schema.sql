-- ============================================================
-- BNR Bank Licensing Portal — Database Schema
-- PostgreSQL 15+
-- Note: No dollar-quoting ($$) used — Spring SQL runner
-- does not support it. Use plain SQL throughout.
-- ============================================================

-- Drop in reverse dependency order (for clean restarts)
DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS documents CASCADE;
DROP TABLE IF EXISTS applications CASCADE;
DROP TABLE IF EXISTS users CASCADE;

DROP TYPE IF EXISTS user_role CASCADE;
DROP TYPE IF EXISTS application_status CASCADE;

-- ── Enums ───────────────────────────────────────────────────
CREATE TYPE user_role AS ENUM (
    'APPLICANT',
    'REVIEWER',
    'APPROVER',
    'ADMIN'
);

CREATE TYPE application_status AS ENUM (
    'DRAFT',
    'SUBMITTED',
    'UNDER_REVIEW',
    'ADDITIONAL_INFO_REQUESTED',
    'REVIEWED',
    'APPROVED',
    'REJECTED'
);

-- ── Users ────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            user_role    NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role  ON users(role);

-- ── Applications ─────────────────────────────────────────────
CREATE TABLE applications (
    id                          UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number            VARCHAR(50)        NOT NULL UNIQUE,
    applicant_id                UUID               NOT NULL REFERENCES users(id),
    institution_name            VARCHAR(255)       NOT NULL,
    institution_type            VARCHAR(100)       NOT NULL,
    business_plan               TEXT,
    registered_capital          BIGINT,
    status                      application_status NOT NULL DEFAULT 'DRAFT',
    version                     BIGINT             NOT NULL DEFAULT 0,
    reviewer_id                 UUID               REFERENCES users(id),
    approver_id                 UUID               REFERENCES users(id),
    rejection_reason            TEXT,
    reviewer_notes              TEXT,
    additional_info_request     TEXT,
    submitted_at                TIMESTAMP,
    decided_at                  TIMESTAMP,
    created_at                  TIMESTAMP          NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP          NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_applications_applicant  ON applications(applicant_id);
CREATE INDEX idx_applications_status     ON applications(status);
CREATE INDEX idx_applications_reviewer   ON applications(reviewer_id);

-- ── Documents ─────────────────────────────────────────────────
CREATE TABLE documents (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID         NOT NULL REFERENCES applications(id),
    file_name           VARCHAR(255) NOT NULL,
    file_path           VARCHAR(500) NOT NULL,
    file_size           BIGINT       NOT NULL,
    file_type           VARCHAR(100) NOT NULL,
    uploader_id         UUID         NOT NULL REFERENCES users(id),
    version_number      INTEGER      NOT NULL DEFAULT 1,
    current             BOOLEAN      NOT NULL DEFAULT TRUE,
    document_category   VARCHAR(100),
    uploaded_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_application ON documents(application_id);
CREATE INDEX idx_documents_current     ON documents(application_id, current);

-- ── Audit Log (APPEND-ONLY) ───────────────────────────────────
-- No updated_at column — deliberately absent (append-only design).
-- REVOKE UPDATE/DELETE enforced below.
CREATE TABLE audit_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID         REFERENCES applications(id),
    actor_id        UUID         NOT NULL REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    state_before    VARCHAR(50),
    state_after     VARCHAR(50),
    metadata        TEXT,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_application ON audit_log(application_id);
CREATE INDEX idx_audit_actor       ON audit_log(actor_id);
CREATE INDEX idx_audit_created     ON audit_log(created_at DESC);

-- ── Append-only enforcement ───────────────────────────────────
-- Revoke UPDATE and DELETE on audit_log from all users.
-- Even bnr_user (the app DB user) cannot modify audit records.
-- This is enforced at the database level — not just in application code.
REVOKE UPDATE, DELETE ON audit_log FROM PUBLIC;
