-- Add SKIPPED to the delivery_status channel enum so that opt-out entries
-- can be recorded without violating the column constraint.
ALTER TABLE delivery_status
    MODIFY COLUMN channel ENUM('WECHAT','IN_APP','SKIPPED') NOT NULL;
