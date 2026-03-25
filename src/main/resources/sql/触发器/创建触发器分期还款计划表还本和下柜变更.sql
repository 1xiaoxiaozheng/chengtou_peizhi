-- 创建触发器：监听 formson_0043 的下柜资金/还本字段变化（带业务条件过滤）
CREATE TRIGGER trg_formson_0043_monitor
ON formson_0043
AFTER INSERT, UPDATE  -- 监听新增和更新操作
AS
BEGIN
    SET NOCOUNT ON;  -- 避免返回额外行数影响业务执行
    SET XACT_ABORT ON; -- 异常时回滚事务，增强稳定性

    -- 核心业务条件：field0013=分期还本 且 field0014≠分期付息
    -- 先通过CTP_ENUM_ITEM关联翻译field0013/field0014的SHOWVALUE，过滤符合条件的记录
    WITH ValidData AS (
        SELECT 
            ins.*,
            ei13.SHOWVALUE AS show13,
            ei14.SHOWVALUE AS show14
        FROM inserted ins
        LEFT JOIN CTP_ENUM_ITEM ei13 ON ins.field0013 = ei13.ID
        LEFT JOIN CTP_ENUM_ITEM ei14 ON ins.field0014 = ei14.ID
        -- 过滤条件：field0013是分期还本 + field0014不是分期付息（含null，只要不是分期付息就满足）
        WHERE ei13.SHOWVALUE = N'分期还本'
          AND (ei14.SHOWVALUE <> N'分期付息' OR ei14.SHOWVALUE IS NULL)
    )

    -- 处理更新操作（对比新旧值，仅处理符合业务条件的记录）
    IF EXISTS (SELECT 1 FROM inserted) AND EXISTS (SELECT 1 FROM deleted)
    BEGIN
        -- 监听 field0027（下柜资金）变化
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            vd.id,
            CONCAT(N'field0027=', CAST(vd.field0027 AS NVARCHAR(50))),
            GETDATE()
        FROM ValidData vd
        JOIN deleted d ON vd.id = d.id
        -- 判定field0027真变化（含null和非null互转）
        WHERE (vd.field0027 <> d.field0027
               OR (vd.field0027 IS NOT NULL AND d.field0027 IS NULL)
               OR (vd.field0027 IS NULL AND d.field0027 IS NOT NULL))
          AND vd.field0027 IS NOT NULL;  -- 仅记录非空变化

        -- 监听 field0029（还本）变化
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            vd.id,
            CONCAT(N'field0029=', CAST(vd.field0029 AS NVARCHAR(50))),
            GETDATE()
        FROM ValidData vd
        JOIN deleted d ON vd.id = d.id
        -- 判定field0029真变化（含null和非null互转）
        WHERE (vd.field0029 <> d.field0029
               OR (vd.field0029 IS NOT NULL AND d.field0029 IS NULL)
               OR (vd.field0029 IS NULL AND d.field0029 IS NOT NULL))
          AND vd.field0029 IS NOT NULL;  -- 仅记录非空变化
    END

    -- 处理新增操作（只有插入无旧值，仅处理符合业务条件的记录）
    IF EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted)
    BEGIN
        -- 新增时记录 field0027（下柜资金）
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            id,
            CONCAT(N'field0027=', CAST(field0027 AS NVARCHAR(50))),
            GETDATE()
        FROM ValidData
        WHERE field0027 IS NOT NULL;

        -- 新增时记录 field0029（还本）
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            id,
            CONCAT(N'field0029=', CAST(field0029 AS NVARCHAR(50))),
            GETDATE()
        FROM ValidData
        WHERE field0029 IS NOT NULL;
    END
END;
GO





修改语句


-- 修改触发器：监听 formson_0043 的下柜资金/还本字段变化（正确关联formmain_0039主表取field0013/field0014）
ALTER TRIGGER trg_formson_0043_monitor
ON formson_0043
AFTER INSERT, UPDATE  -- 同时监听新增和更新操作
AS
BEGIN
    SET NOCOUNT ON;  -- 避免返回额外的行数影响
    SET XACT_ABORT ON; -- 异常回滚，防止脏数据，增强稳定性

    -- 处理更新操作（对比新旧值，仅处理符合业务条件的记录）
    IF EXISTS (SELECT 1 FROM inserted) AND EXISTS (SELECT 1 FROM deleted)
    BEGIN
        -- 监听 field0027（下柜资金）变化：field0013=分期还本 且 field0014≠分期付息
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT(N'field0027=', CAST(i.field0027 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        JOIN deleted d ON i.id = d.id
        -- 核心：子表formson_0043通过formmain_id关联主表formmain_0039（取field0013/field0014）
        JOIN formmain_0039 m ON i.formmain_id = m.ID
        -- 通过主表的field0013/field0014关联枚举表取显示值
        LEFT JOIN CTP_ENUM_ITEM ei13 ON m.field0013 = ei13.ID
        LEFT JOIN CTP_ENUM_ITEM ei14 ON m.field0014 = ei14.ID
        -- 核心业务规则：field0013是分期还本 + field0014不是分期付息（含null）
        WHERE ei13.SHOWVALUE = N'分期还本'
          AND (ei14.SHOWVALUE <> N'分期付息' OR ei14.SHOWVALUE IS NULL)
          -- 判定field0027真变化（含null和非null互转）
          AND (i.field0027 <> d.field0027
               OR (i.field0027 IS NOT NULL AND d.field0027 IS NULL)
               OR (i.field0027 IS NULL AND d.field0027 IS NOT NULL))
          AND i.field0027 IS NOT NULL;  -- 仅记录非空的变化

        -- 监听 field0029（还本）变化：field0013=分期还本 且 field0014≠分期付息
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT(N'field0029=', CAST(i.field0029 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        JOIN deleted d ON i.id = d.id
        -- 关联主表formmain_0039
        JOIN formmain_0039 m ON i.formmain_id = m.ID
        -- 关联枚举表
        LEFT JOIN CTP_ENUM_ITEM ei13 ON m.field0013 = ei13.ID
        LEFT JOIN CTP_ENUM_ITEM ei14 ON m.field0014 = ei14.ID
        -- 核心业务规则
        WHERE ei13.SHOWVALUE = N'分期还本'
          AND (ei14.SHOWVALUE <> N'分期付息' OR ei14.SHOWVALUE IS NULL)
          -- 判定field0029真变化
          AND (i.field0029 <> d.field0029
               OR (i.field0029 IS NOT NULL AND d.field0029 IS NULL)
               OR (i.field0029 IS NULL AND d.field0029 IS NOT NULL))
          AND i.field0029 IS NOT NULL;  -- 仅记录非空的变化
    END

    -- 处理新增操作（只有插入，没有旧值，仅处理符合业务条件的记录）
    IF EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted)
    BEGIN
        -- 新增时记录 field0027（下柜资金）：field0013=分期还本 且 field0014≠分期付息
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT(N'field0027=', CAST(i.field0027 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        -- 关联主表formmain_0039
        JOIN formmain_0039 m ON i.formmain_id = m.ID
        -- 关联枚举表
        LEFT JOIN CTP_ENUM_ITEM ei13 ON m.field0013 = ei13.ID
        LEFT JOIN CTP_ENUM_ITEM ei14 ON m.field0014 = ei14.ID
        -- 核心业务规则
        WHERE ei13.SHOWVALUE = N'分期还本'
          AND (ei14.SHOWVALUE <> N'分期付息' OR ei14.SHOWVALUE IS NULL)
          AND i.field0027 IS NOT NULL;

        -- 新增时记录 field0029（还本）：field0013=分期还本 且 field0014≠分期付息
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT(N'field0029=', CAST(i.field0029 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        -- 关联主表formmain_0039
        JOIN formmain_0039 m ON i.formmain_id = m.ID
        -- 关联枚举表
        LEFT JOIN CTP_ENUM_ITEM ei13 ON m.field0013 = ei13.ID
        LEFT JOIN CTP_ENUM_ITEM ei14 ON m.field0014 = ei14.ID
        -- 核心业务规则
        WHERE ei13.SHOWVALUE = N'分期还本'
          AND (ei14.SHOWVALUE <> N'分期付息' OR ei14.SHOWVALUE IS NULL)
          AND i.field0029 IS NOT NULL;
    END
END;
GO