-- 创建利率变更通知中间表
CREATE TABLE interest_change_notify (
    id BIGINT IDENTITY(1,1) PRIMARY KEY, -- 自增ID
    source_id BIGINT NOT NULL, -- 关联 formmain_0148 表的主键ID
    current_rate VARCHAR(100) NOT NULL, -- 利率值（field0004）
    change_time DATETIME NOT NULL, -- 变更时间（field0005）
    process_status TINYINT DEFAULT 0, -- 处理状态：0-未处理，1-已处理，2-处理失败
    create_time DATETIME DEFAULT GETDATE() -- 记录创建时间（用于排查问题）
);

更改为sqlserver

需要加上单据号字段
-- 创建利率变更通知中间表（适配 formmain_0032，新增单据号字段）
CREATE TABLE interest_change_notify (
    id BIGINT IDENTITY(1,1) PRIMARY KEY, -- 自增主键
    source_id BIGINT NOT NULL, -- 关联 formmain_0032 表的主键ID
    bill_no NVARCHAR(255) NOT NULL, -- 新增：单据编号（field0001）
    current_rate NUMERIC(20,6) NOT NULL, -- 利率值（field0042，适配DECIMAL类型）
    rate_effective_time DATETIME NOT NULL, -- 利率生效时间（field0041）
    process_status TINYINT DEFAULT 0, -- 处理状态：0-未处理，1-已处理，2-处理失败
    create_time DATETIME DEFAULT GETDATE() -- 记录创建时间
);

-- 可选：添加索引提升查询性能
CREATE INDEX idx_interest_notify_source_id ON interest_change_notify(source_id);
CREATE INDEX idx_interest_notify_bill_no ON interest_change_notify(bill_no);
CREATE INDEX idx_interest_notify_process_status ON interest_change_notify(process_status);