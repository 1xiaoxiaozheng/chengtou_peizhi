-- 生成自 form_meta_config.json：interest_change_notify（SQL Server）
CREATE TABLE interest_change_notify (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    source_id BIGINT NOT NULL,
    bill_no NVARCHAR(255) NOT NULL,
    current_rate NUMERIC(20,6) NOT NULL,
    rate_effective_time DATETIME NOT NULL,
    process_status TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT GETDATE()
);

CREATE INDEX idx_interest_notify_source_id ON interest_change_notify(source_id);
CREATE INDEX idx_interest_notify_bill_no ON interest_change_notify(bill_no);
CREATE INDEX idx_interest_notify_process_status ON interest_change_notify(process_status);

