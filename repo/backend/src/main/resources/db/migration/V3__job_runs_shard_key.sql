-- Idempotent: add shard_key when upgrading from older schema snapshots that omitted it.
SET @db = DATABASE();
SET @exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'job_runs' AND COLUMN_NAME = 'shard_key'
);
SET @sql = IF(@exists = 0,
  'ALTER TABLE job_runs ADD COLUMN shard_key INT NOT NULL DEFAULT 0 AFTER dedup_key',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
