CREATE TABLE medical_memory_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    recorded_by_user_id BIGINT NOT NULL,
    original_text MEDIUMTEXT NOT NULL,
    normalized_text MEDIUMTEXT NULL,
    occurred_at DATETIME(3) NULL,
    people_json JSON NULL,
    place_text VARCHAR(500) NULL,
    source_type VARCHAR(32) NOT NULL,
    source_message_id VARCHAR(255) NULL,
    visibility VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    confirmed_by_user_id BIGINT NULL,
    confirmed_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_memory_idempotency (idempotency_key),
    KEY idx_medical_memory_patient_status_time (patient_user_id, status, occurred_at),
    KEY idx_medical_memory_patient_created (patient_user_id, created_at),
    CONSTRAINT fk_medical_memory_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_memory_recorder
        FOREIGN KEY (recorded_by_user_id) REFERENCES medical_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_memory_confirmer
        FOREIGN KEY (confirmed_by_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_daily_checkins (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    submitted_by_user_id BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    sleep_status VARCHAR(32) NULL,
    meal_status VARCHAR(32) NULL,
    hydration_status VARCHAR(32) NULL,
    mood_status VARCHAR(32) NULL,
    activity_status VARCHAR(32) NULL,
    medication_confirmed BOOLEAN NULL,
    incident_type VARCHAR(64) NULL,
    original_text MEDIUMTEXT NULL,
    source_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    submitted_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_checkin_patient_date (patient_user_id, checkin_date),
    UNIQUE KEY uk_medical_checkin_idempotency (idempotency_key),
    KEY idx_medical_checkin_patient_submitted (patient_user_id, submitted_at),
    CONSTRAINT fk_medical_checkin_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_checkin_submitter
        FOREIGN KEY (submitted_by_user_id) REFERENCES medical_users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_care_observations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    observed_by_user_id BIGINT NOT NULL,
    observer_role VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    observation_text MEDIUMTEXT NOT NULL,
    severity VARCHAR(16) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_observation_idempotency (idempotency_key),
    KEY idx_medical_observation_patient_time (patient_user_id, occurred_at),
    CONSTRAINT fk_medical_observation_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_observation_observer
        FOREIGN KEY (observed_by_user_id) REFERENCES medical_users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_patient_status_snapshots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    generated_for_user_id BIGINT NULL,
    summary_text MEDIUMTEXT NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    source_range_start DATETIME(3) NOT NULL,
    source_range_end DATETIME(3) NOT NULL,
    source_reference_json JSON NOT NULL,
    generator_version VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_snapshot_patient_created (patient_user_id, created_at),
    CONSTRAINT fk_medical_snapshot_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_snapshot_audience
        FOREIGN KEY (generated_for_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
