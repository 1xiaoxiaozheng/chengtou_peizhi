




sqlserver版本：
-- 重建表，target_table_id使用VARCHAR类型（SQL Server版本）- 分期还款计划表
CREATE TABLE Middle_Installment_Records (
  -- 自增主键：SQL Server 用 IDENTITY 替代 AUTO_INCREMENT
  id INT IDENTITY(1,1) PRIMARY KEY,
  -- 文本类型：SQL Server 用 NVARCHAR 适配多字符集，长度50保持一致
  target_table_id NVARCHAR(50) NOT NULL,
  -- 监听字段内容：NVARCHAR 适配中文/特殊字符，长度255不变
  monitored_field NVARCHAR(255),
  -- 创建时间：SQL Server 用 GETDATE() 替代 CURRENT_TIMESTAMP，默认值需用 DEFAULT
  create_time DATETIME DEFAULT GETDATE(),
  -- 处理状态：TINYINT 对应 SQL Server 的 TINYINT，默认值0
  process_status TINYINT DEFAULT 0,
  -- 处理时间：允许为空，类型不变
  process_time DATETIME NULL,
  -- 失败原因：加长字符集类型，长度1000不变
  fail_reason NVARCHAR(1000) NULL,
  -- 重试次数：INT 类型，默认值0
  retry_count INT DEFAULT 0
);

-- 可选：添加索引提升查询性能（根据你的查询场景）
CREATE INDEX idx_Middle_Installment_Records_target_id ON Middle_Installment_Records(target_table_id);
CREATE INDEX idx_Middle_Installment_Records_process_status ON Middle_Installment_Records(process_status);

