-- 生成自 form_meta_config.json：Middle_Insert_Records（SQL Server）
CREATE TABLE Middle_Insert_Records (
  id INT IDENTITY(1,1) PRIMARY KEY,
  target_table_id NVARCHAR(50) NOT NULL,
  monitored_field NVARCHAR(255),
  create_time DATETIME DEFAULT GETDATE(),
  process_status TINYINT DEFAULT 0,
  process_time DATETIME NULL,
  fail_reason NVARCHAR(1000) NULL,
  retry_count INT DEFAULT 0
);

CREATE INDEX idx_Middle_Insert_Records_target_id ON Middle_Insert_Records(target_table_id);
CREATE INDEX idx_Middle_Insert_Records_process_status ON Middle_Insert_Records(process_status);

