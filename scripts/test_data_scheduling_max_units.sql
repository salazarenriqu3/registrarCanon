-- Use eacdb
USE eacdb;

-- 1. Create Test Students
INSERT IGNORE INTO students 
(student_number, first_name, last_name, program_code, year_level, semester, status, is_active, role) 
VALUES 
('TEST-IT-001', 'Test', 'IT Student', 'BSIT', 1, 1, 'ACTIVE', 1, 'STUDENT'),
('TEST-CRIM-001', 'Test', 'Crim Student', 'BSCrim', 1, 1, 'ACTIVE', 1, 'STUDENT');

-- Insert user mapping for them to be able to login if needed, using a dummy hash
INSERT IGNORE INTO sys_users (username, password, is_active, last_name, first_name, role)
VALUES 
('TEST-IT-001', '{noop}1234', 1, 'IT Student', 'Test', 'STUDENT'),
('TEST-CRIM-001', '{noop}1234', 1, 'Crim Student', 'Test', 'STUDENT');

UPDATE students s JOIN sys_users u ON s.student_number = u.username 
SET s.user_id = u.user_id 
WHERE s.student_number IN ('TEST-IT-001', 'TEST-CRIM-001');

-- 2. Assign Curriculum
INSERT IGNORE INTO student_curriculum_assignments
(student_number, curriculum_id, program_code, assignment_type, is_current)
VALUES
('TEST-IT-001', 144, 'BSIT', 'DEFAULT', 1),
('TEST-CRIM-001', 139, 'BSCrim', 'DEFAULT', 1);

-- 3. Create Class Sections
-- 204: AUS0 11 (BSIT)
INSERT INTO class_sections (course_id, term_id, section_code, max_capacity, section_status, semester_number) 
VALUES (204, 1, 'TEST-IT-1A', 40, 'Open', 1);
SET @bsit_sec1 = LAST_INSERT_ID();

-- 205: ARPH 11 (BSIT)
INSERT INTO class_sections (course_id, term_id, section_code, max_capacity, section_status, semester_number) 
VALUES (205, 1, 'TEST-IT-1A', 40, 'Open', 1);
SET @bsit_sec2 = LAST_INSERT_ID();

-- 760: GE 1 (BSCrim)
INSERT INTO class_sections (course_id, term_id, section_code, max_capacity, section_status, semester_number) 
VALUES (760, 1, 'TEST-CRIM-1A', 40, 'Open', 1);
SET @crim_sec1 = LAST_INSERT_ID();

-- 761: GE 2 (BSCrim)
INSERT INTO class_sections (course_id, term_id, section_code, max_capacity, section_status, semester_number) 
VALUES (761, 1, 'TEST-CRIM-1A', 40, 'Open', 1);
SET @crim_sec2 = LAST_INSERT_ID();

-- 4. Create Schedules
-- BSIT Section 1: Monday, 08:00 - 09:30, Room 7 (ENG-201), Faculty 1 (EMP-1001)
INSERT INTO class_schedules (section_id, room_id, faculty_id, day_of_week, start_time, end_time, schedule_type, status)
VALUES (@bsit_sec1, 7, 1, 1, '08:00:00', '09:30:00', 'Lecture', 'OPEN');

-- BSIT Section 2: Monday, 10:00 - 11:30, Room 7 (ENG-201), Faculty 2 (EMP-1002)
INSERT INTO class_schedules (section_id, room_id, faculty_id, day_of_week, start_time, end_time, schedule_type, status)
VALUES (@bsit_sec2, 7, 2, 1, '10:00:00', '11:30:00', 'Lecture', 'OPEN');

-- BSCrim Section 1: Tuesday, 08:00 - 09:30, Room 8 (ENG-202), Faculty 1 (EMP-1001)
INSERT INTO class_schedules (section_id, room_id, faculty_id, day_of_week, start_time, end_time, schedule_type, status)
VALUES (@crim_sec1, 8, 1, 2, '08:00:00', '09:30:00', 'Lecture', 'OPEN');

-- BSCrim Section 2: Tuesday, 10:00 - 11:30, Room 8 (ENG-202), Faculty 2 (EMP-1002)
INSERT INTO class_schedules (section_id, room_id, faculty_id, day_of_week, start_time, end_time, schedule_type, status)
VALUES (@crim_sec2, 8, 2, 2, '10:00:00', '11:30:00', 'Lecture', 'OPEN');
