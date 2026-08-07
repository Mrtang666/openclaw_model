CREATE TABLE medical_health_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    recorded_by_user_id BIGINT NOT NULL,
    recorder_role VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    primary_value DECIMAL(10,2) NULL,
    secondary_value DECIMAL(10,2) NULL,
    unit VARCHAR(32) NULL,
    record_text VARCHAR(2000) NULL,
    source_type VARCHAR(32) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_health_record_idempotency (idempotency_key),
    KEY idx_medical_health_record_patient_time (patient_user_id, occurred_at),
    CONSTRAINT fk_medical_health_record_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_health_record_recorder
        FOREIGN KEY (recorded_by_user_id) REFERENCES medical_users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
