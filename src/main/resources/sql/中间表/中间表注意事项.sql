CREATE TABLE [dbo].[Middle_Insert_Records] (
    [id] INT IDENTITY(1,1) PRIMARY KEY,  -- 中间表自增主键（唯一标识每条待处理记录）
    [target_table_id] INT NOT NULL,      -- 目标表的记录 ID（关联原始数据）
    [monitored_field] VARCHAR(255),      -- 被监控的字段值（可选，便于排查问题）
    [create_time] DATETIME DEFAULT GETDATE(),  -- 记录创建时间（触发时间）
    [process_status] TINYINT DEFAULT 0,  -- 处理状态：0=未处理，1=已处理，2=处理失败
    [process_time] DATETIME NULL,        -- 处理完成时间（成功/失败时间）
    [fail_reason] VARCHAR(1000) NULL,    -- 失败原因（如接口返回错误、超时等）
    [retry_count] INT DEFAULT 0          -- 重试次数（避免无限重试）
);