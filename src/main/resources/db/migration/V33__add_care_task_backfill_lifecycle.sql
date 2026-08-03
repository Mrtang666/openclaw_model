ALTER TABLE medical_care_task_instances
    ADD COLUMN completion_mode VARCHAR(32) NULL AFTER overdue_notified_at,
    ADD COLUMN reported_at DATETIME(3) NULL AFTER completion_mode,
    ADD COLUMN late_checkin_deadline_at DATETIME(3) NULL AFTER reported_at,
    ADD COLUMN missed_confirmed_at DATETIME(3) NULL AFTER late_checkin_deadline_at,
    ADD COLUMN missed_reason VARCHAR(128) NULL AFTER missed_confirmed_at,
    ADD KEY idx_medical_task_instance_backfill_deadline
        (status, late_checkin_deadline_at, follow_up_enqueued_at),
    ADD KEY idx_medical_task_instance_missed_notification
        (status, overdue_notified_at, due_at);

UPDATE medical_care_task_instances i
JOIN medical_care_task_templates t ON t.id=i.task_template_id
SET i.late_checkin_deadline_at = TIMESTAMPADD(
    MINUTE, GREATEST(t.grace_period_minutes, t.escalation_after_minutes), i.due_at)
WHERE i.late_checkin_deadline_at IS NULL;

INSERT IGNORE INTO medical_permission_definitions
    (permission_code, permission_name, description, created_at)
VALUES
    ('PATIENT_TASK_BACKFILL', '照护任务补卡',
     '允许患者或已授权家属在补卡窗口内代为确认任务完成', CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO medical_relation_permissions
    (relation_id, permission_code, expires_at, created_at)
SELECT r.id, 'PATIENT_TASK_BACKFILL', p.expires_at, CURRENT_TIMESTAMP(3)
FROM medical_patient_relations r
JOIN medical_relation_permissions p ON p.relation_id=r.id
    AND p.permission_code='PATIENT_TASK_UPDATE'
WHERE r.status='ACTIVE';
