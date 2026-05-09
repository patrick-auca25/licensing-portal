-- ============================================================
-- BNR Bank Licensing Portal — Seed Data
-- Passwords are all: Password123!
-- BCrypt hash of "Password123!" with strength 12
-- ============================================================

-- Clear existing seed data
DELETE FROM audit_log;
DELETE FROM documents;
DELETE FROM applications;
DELETE FROM users;

-- ── Users (one per role) ─────────────────────────────────────
INSERT INTO users (id, email, password_hash, full_name, role, is_active) VALUES
(
    'a0000001-0000-0000-0000-000000000001',
    'applicant@example.com',
    '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Alice Uwimana',
    'APPLICANT',
    TRUE
),
(
    'a0000002-0000-0000-0000-000000000002',
    'reviewer@bnr.rw',
    '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Bob Nkurunziza',
    'REVIEWER',
    TRUE
),
(
    'a0000003-0000-0000-0000-000000000003',
    'approver@bnr.rw',
    '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Claire Mukamana',
    'APPROVER',
    TRUE
),
(
    'a0000004-0000-0000-0000-000000000004',
    'admin@bnr.rw',
    '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'David Habimana',
    'ADMIN',
    TRUE
);

-- ── Application 1: SUBMITTED state ───────────────────────────
INSERT INTO applications (
    id, reference_number, applicant_id,
    institution_name, institution_type,
    business_plan, registered_capital,
    status, version, submitted_at
) VALUES (
    'b0000001-0000-0000-0000-000000000001',
    'BNR-2026-0001',
    'a0000001-0000-0000-0000-000000000001',
    'Kigali Commercial Bank Ltd',
    'Commercial Bank',
    'A full-service commercial bank targeting SMEs in Rwanda with mobile-first banking solutions.',
    5000000000,
    'SUBMITTED',
    0,
    NOW() - INTERVAL '2 days'
);

-- ── Application 2: UNDER_REVIEW state ────────────────────────
INSERT INTO applications (
    id, reference_number, applicant_id,
    institution_name, institution_type,
    business_plan, registered_capital,
    status, version, reviewer_id,
    submitted_at
) VALUES (
    'b0000002-0000-0000-0000-000000000002',
    'BNR-2026-0002',
    'a0000001-0000-0000-0000-000000000001',
    'Rwanda Microfinance Corp',
    'Microfinance Institution',
    'Providing micro-credit and savings services to rural communities across all provinces.',
    500000000,
    'UNDER_REVIEW',
    1,
    'a0000002-0000-0000-0000-000000000002',
    NOW() - INTERVAL '5 days'
);

-- ── Seed Audit Log entries ────────────────────────────────────
INSERT INTO audit_log (application_id, actor_id, action, state_before, state_after, metadata) VALUES
(
    'b0000001-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    'APPLICATION_SUBMITTED',
    'DRAFT',
    'SUBMITTED',
    '{"note": "Initial submission by applicant"}'
),
(
    'b0000002-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000001',
    'APPLICATION_SUBMITTED',
    'DRAFT',
    'SUBMITTED',
    '{"note": "Initial submission by applicant"}'
),
(
    'b0000002-0000-0000-0000-000000000002',
    'a0000002-0000-0000-0000-000000000002',
    'REVIEW_STARTED',
    'SUBMITTED',
    'UNDER_REVIEW',
    '{"note": "Reviewer Bob Nkurunziza claimed the application"}'
);
