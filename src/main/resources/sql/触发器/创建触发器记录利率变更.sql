-- 第一步：删除旧触发器（如果存在，避免冲突）
IF EXISTS (SELECT 1 FROM sys.triggers WHERE name = 'trg_formmain_0032_insert')
DROP TRIGGER trg_formmain_0032_insert;
GO

-- 第二步：创建适配formmain_0032的新触发器（名称对应表名，便于维护）
CREATE TRIGGER trg_formmain_0032_insert
ON formmain_0032
AFTER INSERT -- 插入后触发
AS
BEGIN
    SET NOCOUNT ON; -- 不返回计数信息，避免干扰插入操作

    -- 插入数据到interest_change_notify中间表（1:1映射formmain_0032的真实字段）
    INSERT INTO interest_change_notify (
        source_id,          -- 关联formmain_0032的主键ID
        bill_no,            -- 单据编号（formmain_0032.field0001）
        current_rate,       -- 利率值（formmain_0032.field0042）
        rate_effective_time,-- 利率生效时间（formmain_0032.field0041）
        process_status,     -- 处理状态：默认0（未处理）
        create_time         -- 记录创建时间（使用当前时间）
    )
    SELECT
        id,                                      -- source_id：formmain_0032的主键（必存，关联源表）
        ISNULL(i.field0001, '') AS bill_no,      -- bill_no：单据编号，空值替换为空字符串（满足NOT NULL约束）
        i.field0042 AS current_rate,             -- current_rate：利率值（NUMERIC(20,6)匹配）
        i.field0041 AS rate_effective_time,      -- rate_effective_time：利率生效时间（DATETIME匹配）
        0 AS process_status,                     -- 显式赋值未处理状态（与表默认值一致）
        GETDATE() AS create_time                 -- 创建时间（也可省略，使用表的DEFAULT GETDATE()）
    FROM INSERTED i
    -- 过滤核心非空字段，避免违反中间表NOT NULL约束
    WHERE
        i.field0042 IS NOT NULL  -- 利率值不能为空
        AND i.field0041 IS NOT NULL; -- 利率生效时间不能为空
END;
GO