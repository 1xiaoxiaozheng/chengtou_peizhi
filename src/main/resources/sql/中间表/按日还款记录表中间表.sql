-- 重建表，target_table_id使用VARCHAR类型
CREATE TABLE `Middle_Insert_Records` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `target_table_id` VARCHAR(50) NOT NULL,  -- 文本类型，长度50足够容纳大多数主键（可根据实际调整）
  `monitored_field` VARCHAR(255),
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `process_status` TINYINT DEFAULT 0,
  `process_time` DATETIME NULL,
  `fail_reason` VARCHAR(1000) NULL,
  `retry_count` INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;





sqlserver版本：
-- 重建表，target_table_id使用VARCHAR类型（SQL Server版本）
CREATE TABLE Middle_Insert_Records (
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
CREATE INDEX idx_Middle_Insert_Records_target_id ON Middle_Insert_Records(target_table_id);
CREATE INDEX idx_Middle_Insert_Records_process_status ON Middle_Insert_Records(process_status);


-- 给Middle_Insert_Records新增old_value字段，用于记录监控字段的原值
ALTER TABLE Middle_Insert_Records
ADD
    old_value NVARCHAR(50) NULL -- 原值字段：与monitored_field的数值部分长度一致，允许空（新增场景无原值）
    CONSTRAINT DF_Middle_Insert_Records_old_value DEFAULT NULL; -- 显式设置默认值为NULL（可选）