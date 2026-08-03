UPDATE medical_notifications
SET status = 'WAITING_LOGIN', locked_at = NULL
WHERE status IN ('PENDING', 'FAILED')
  AND (last_error LIKE '%微信连接当前不可用%' OR last_error LIKE '%没有可用微信连接%');

DELETE stale
FROM medical_notifications stale
JOIN medical_notifications sent
  ON sent.to_user_id = stale.to_user_id
 AND sent.notification_type = stale.notification_type
 AND SUBSTRING_INDEX(sent.idempotency_key, ':', 3) = SUBSTRING_INDEX(stale.idempotency_key, ':', 3)
WHERE stale.status IN ('PENDING', 'FAILED', 'WAITING_LOGIN')
  AND stale.idempotency_key LIKE 'task:%'
  AND sent.status = 'SENT';

DELETE stale
FROM medical_notifications stale
JOIN medical_notifications newer
  ON newer.to_user_id = stale.to_user_id
 AND newer.notification_type = stale.notification_type
 AND SUBSTRING_INDEX(newer.idempotency_key, ':', 3) = SUBSTRING_INDEX(stale.idempotency_key, ':', 3)
 AND newer.id > stale.id
WHERE stale.status IN ('PENDING', 'FAILED', 'WAITING_LOGIN')
  AND newer.status IN ('PENDING', 'FAILED', 'WAITING_LOGIN')
  AND stale.idempotency_key LIKE 'task:%'
  AND newer.idempotency_key LIKE 'task:%';
