-- ============================================================
-- BNR Bank Licensing Portal — Seed Data
-- All passwords: Password123!
-- Hash generated and verified with BCrypt rounds=10
-- ============================================================

-- Clear in reverse FK order
DELETE FROM audit_log;
DELETE FROM documents;
DELETE FROM applications;
DELETE FROM users;

-- ── One user per role ────────────────────────────────────────
INSERT INTO users (id, email, password_hash, full_name, role, active) VALUES
(
    'a0000001-0000-0000-0000-000000000001',
    'applicant@example.com',
    '$2a$10$Bouu9RckXhWXP4PPkvhp/uddQeKXmUv9kJt1VrWjbP8JJ3OBDk9vO',
    'Alice Uwimana',
    'APPLICANT',
    TRUE
),
(
    'a0000002-0000-0000-0000-000000000002',
    'reviewer@bnr.rw',
    '$2a$10$Bouu9RckXhWXP4PPkvhp/uddQeKXmUv9kJt1VrWjbP8JJ3OBDk9vO',
    'Bob Nkurunziza',
    'REVIEWER',
    TRUE
),
(
    'a0000003-0000-0000-0000-000000000003',
    'approver@bnr.rw',
    '$2a$10$Bouu9RckXhWXP4PPkvhp/uddQeKXmUv9kJt1VrWjbP8JJ3OBDk9vO',
    'Claire Mukamana',
    'APPROVER',
    TRUE
),
(
    'a0000004-0000-0000-0000-000000000004',
    'admin@bnr.rw',
    '$2a$10$Bouu9RckXhWXP4PPkvhp/uddQeKXmUv9kJt1VrWjbP8JJ3OBDk9vO',
    'David Habimana',
    'ADMIN',
    TRUE
);

-- ── Application 1: SUBMITTED ─────────────────────────────────
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
    'A full-service commercial bank targeting SMEs in Rwanda with mobile-first banking.',
    5000000000,
    'SUBMITTED',
    0,
    NOW() - INTERVAL '2 days'
);

-- ── Application 2: UNDER_REVIEW ──────────────────────────────
INSERT INTO applications (
    id, reference_number, applicant_id,
    institution_name, institution_type,
    business_plan, registered_capital,
    status, version, reviewer_id, submitted_at
) VALUES (
    'b0000002-0000-0000-0000-000000000002',
    'BNR-2026-0002',
    'a0000001-0000-0000-0000-000000000001',
    'Rwanda Microfinance Corp',
    'Microfinance Institution',
    'Providing micro-credit and savings to rural communities across all provinces.',
    500000000,
    'UNDER_REVIEW',
    1,
    'a0000002-0000-0000-0000-000000000002',
    NOW() - INTERVAL '5 days'
);

-- ── Seed Audit Log ────────────────────────────────────────────
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
