-- Registrar curriculum lifecycle status patch/check
-- Date: 2026-06-25
-- Purpose:
--   CURRENT  = the one actively offered curriculum per program used by default enrollment/fees/scholarship reads.
--   LEGACY   = old/historical curriculum that remains assignable to returning students.
--   DRAFT    = editable working copy; not used for scheduling/default assignment.
--   ARCHIVED = retained record; not assignable.

ALTER TABLE curriculum_templates
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

UPDATE curriculum_templates
SET lifecycle_status = CASE
    WHEN UPPER(COALESCE(approval_status,'')) IN ('ARCHIVED','RETIRED') THEN 'ARCHIVED'
    WHEN COALESCE(is_active,0) = 1 THEN 'CURRENT'
    WHEN UPPER(COALESCE(approval_status,'')) IN ('DRAFT','PLACEHOLDER') THEN 'DRAFT'
    ELSE 'LEGACY'
END
WHERE lifecycle_status IS NULL
   OR lifecycle_status = ''
   OR UPPER(lifecycle_status) NOT IN ('DRAFT','CURRENT','LEGACY','ARCHIVED');

-- Demote duplicate CURRENT labels per program. This keeps the newest current curriculum
-- with course rows, then moves other current rows to LEGACY.
UPDATE curriculum_templates ct
JOIN (
    SELECT ranked.curriculum_id
    FROM (
        SELECT ct2.curriculum_id,
               ROW_NUMBER() OVER (
                   PARTITION BY ct2.program_id
                   ORDER BY CASE WHEN COUNT(cc.curriculum_course_id) > 0 THEN 0 ELSE 1 END,
                            ct2.version_number DESC,
                            ct2.curriculum_id DESC
               ) AS current_rank
        FROM curriculum_templates ct2
        LEFT JOIN curriculum_courses cc ON cc.curriculum_id = ct2.curriculum_id
        WHERE UPPER(COALESCE(ct2.lifecycle_status,'')) = 'CURRENT'
        GROUP BY ct2.curriculum_id, ct2.program_id, ct2.version_number
    ) ranked
    WHERE ranked.current_rank > 1
) duplicate_current ON duplicate_current.curriculum_id = ct.curriculum_id
SET ct.lifecycle_status = 'LEGACY',
    ct.is_active = 0,
    ct.approval_status = 'Approved';

-- Verification 1: should return zero rows.
SELECT p.program_code, COUNT(*) AS current_count
FROM curriculum_templates ct
JOIN programs p ON p.program_id = ct.program_id
WHERE UPPER(COALESCE(ct.lifecycle_status,'')) = 'CURRENT'
GROUP BY p.program_code
HAVING COUNT(*) > 1;

-- Verification 2: review all curriculum versions and labels.
SELECT p.program_code,
       ct.curriculum_id,
       ct.curriculum_name,
       ct.academic_year,
       ct.version_number,
       ct.lifecycle_status,
       ct.approval_status,
       ct.is_active,
       COUNT(cc.curriculum_course_id) AS course_rows
FROM curriculum_templates ct
JOIN programs p ON p.program_id = ct.program_id
LEFT JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id
GROUP BY p.program_code,
         ct.curriculum_id,
         ct.curriculum_name,
         ct.academic_year,
         ct.version_number,
         ct.lifecycle_status,
         ct.approval_status,
         ct.is_active
ORDER BY p.program_code,
         CASE UPPER(COALESCE(ct.lifecycle_status,''))
             WHEN 'CURRENT' THEN 0
             WHEN 'LEGACY' THEN 1
             WHEN 'DRAFT' THEN 2
             ELSE 3
         END,
         ct.curriculum_id DESC;
