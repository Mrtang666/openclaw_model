INSERT INTO medical_permission_definitions (
    permission_code,
    permission_name,
    description,
    created_at
) VALUES
    ('PATIENT_CONTEXT_READ', 'Read patient context', 'Allows reading patient conversation context and memory.', NOW(3)),
    ('PATIENT_DAILY_READ', 'Read patient daily status', 'Allows reading patient daily status, summaries, and check-in information.', NOW(3)),
    ('PATIENT_TASK_READ', 'Read patient tasks', 'Allows reading patient care plan tasks and task execution status.', NOW(3)),
    ('PATIENT_TASK_CREATE', 'Create patient tasks', 'Allows creating care plan tasks for a patient.', NOW(3)),
    ('PATIENT_TASK_ADJUST', 'Adjust patient tasks', 'Allows adjusting or pausing patient care plan tasks.', NOW(3)),
    ('PATIENT_EMAIL_DOCTOR', 'Email doctor about patient', 'Allows sending patient-related email messages to a doctor.', NOW(3)),
    ('PATIENT_EMAIL_PARENT', 'Email family about patient', 'Allows sending patient-related email messages to family members.', NOW(3));
