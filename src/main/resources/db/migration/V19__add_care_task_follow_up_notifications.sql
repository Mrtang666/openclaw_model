ALTER TABLE medical_care_task_templates
    ADD COLUMN follow_up_after_minutes INT NOT NULL DEFAULT 30 AFTER end_date;

ALTER TABLE medical_care_task_instances
    ADD COLUMN follow_up_enqueued_at DATETIME(3) NULL AFTER reminder_enqueued_at,
    ADD KEY idx_medical_task_instance_follow_up (status, follow_up_enqueued_at, due_at);
