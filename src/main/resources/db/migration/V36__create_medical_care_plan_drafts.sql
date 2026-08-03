CREATE TABLE medical_care_plan_drafts (
    id VARCHAR(64) PRIMARY KEY,
    created_by_user_id BIGINT NOT NULL,
    patient_user_id BIGINT NOT NULL,
    patient_name VARCHAR(255) NOT NULL,
    patient_code VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    doctor_input TEXT NOT NULL,
    refined_plan TEXT NOT NULL,
    edited_plan TEXT NOT NULL,
    confirmed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_medical_care_draft_creator_updated (created_by_user_id, updated_at),
    KEY idx_medical_care_draft_patient_updated (patient_user_id, updated_at),
    CONSTRAINT fk_medical_care_draft_creator
        FOREIGN KEY (created_by_user_id) REFERENCES medical_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_care_draft_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
