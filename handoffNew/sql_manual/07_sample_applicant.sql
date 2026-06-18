-- Manual configuration: one applicant + payment seed for external admission demo (Session A9/A10)
-- Canon: applicant intake and student-number issuance are external to Registrar
USE eacdb;

SET @ref = 'DEMO-APP-001';

DELETE FROM payments WHERE reference_number = @ref;
DELETE FROM applicants WHERE reference_number = @ref;

INSERT INTO applicants (reference_number, applicant_status, first_name, last_name, email, program1)
VALUES (@ref, 'QUALIFIED FOR ENROLLMENT', 'Demo', 'Applicant', 'demo.applicant@test.eac.ph', 'BSCPE');

INSERT INTO payments (transaction_id, reference_number, amount, payment_method, status, payment_date)
VALUES ('DEMO-PAY-001', @ref, 5000, 'Cash', 'COMPLETED', NOW());

SELECT reference_number, first_name, last_name, program1, applicant_status FROM applicants WHERE reference_number = @ref;
SELECT reference_number, amount, status FROM payments WHERE reference_number = @ref;

-- Y1 admit: continue in the external Admission / Cashier system
-- For Y2+ demo: create the student externally, then verify Registrar reads the flags correctly
