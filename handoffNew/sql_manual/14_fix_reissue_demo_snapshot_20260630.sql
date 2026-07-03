USE eacdb;

-- Repairs the disposable reissue demo snapshot used during cross-app testing.
-- The live row drifted to a non-existent course/section pair:
--   course_code = 'REISSUE 101'
--   section_code = 'R-101'
-- This helper re-points it to a real BSIT term-1 offering so Enrollment auto-finalize
-- can materialize an actual enlistment for SPRINT-DEMO-2026-001 / REISSUE-DEMO-001.

UPDATE applicant_pre_reg_subject_lines l
JOIN applicant_pre_reg_snapshots s ON s.id = l.snapshot_id
SET l.course_id = 101,
    l.course_code = 'CC101',
    l.course_title = 'Introduction to Computing',
    l.section_id = 1001,
    l.section_code = 'CC101-A',
    l.schedule_text = 'MON 07:30-09:00',
    l.units = 3.00,
    l.sort_order = COALESCE(NULLIF(l.sort_order, 0), l.line_order, 1)
WHERE s.reference_number = 'REISSUE-DEMO-001';

SELECT s.reference_number,
       l.snapshot_id,
       l.line_order,
       l.sort_order,
       l.course_id,
       l.course_code,
       l.section_id,
       l.section_code
FROM applicant_pre_reg_subject_lines l
JOIN applicant_pre_reg_snapshots s ON s.id = l.snapshot_id
WHERE s.reference_number = 'REISSUE-DEMO-001'
ORDER BY l.line_order, l.id;
