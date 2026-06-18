-- Retire empty curriculum blocker programs from active offerings.
-- Date: 2026-06-08
--
-- Business rule:
-- These programs had no official curriculum source available and only empty
-- placeholder curricula. They should not block active-term readiness while they
-- are not offered operationally. This is a soft retirement, not a hard delete.
--
-- Historical applicants, curriculum placeholders, fee rows, and audit data are
-- preserved. Reactivate by setting active_status = 1 if the program becomes
-- operational again with an official curriculum.

USE eacdb;

UPDATE programs
SET active_status = 0
WHERE program_code IN (
    'BSBA', 'BSCE', 'BSCS', 'BSECE', 'BSED', 'BSMATH',
    'ABCOMM', 'BEED', 'BSMT'
);
