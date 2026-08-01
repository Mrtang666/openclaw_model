ALTER TABLE medical_user_wechat_bindings
    DROP INDEX uk_medical_wechat_connection_user;

ALTER TABLE medical_user_wechat_bindings
    ADD UNIQUE KEY uk_medical_wechat_user_connection (user_id, connection_id, from_user_id);
