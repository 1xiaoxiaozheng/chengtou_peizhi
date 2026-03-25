变更sqlserver
-- 创建触发器：监听 formson_0030 的下柜资金/还本字段变化
CREATE TRIGGER trg_formson_0030_monitor
ON formson_0030
AFTER INSERT, UPDATE  -- 同时监听新增和更新操作
AS
BEGIN
    SET NOCOUNT ON;  -- 避免返回额外的行数影响

    -- 处理更新操作（对比新旧值）
    IF EXISTS (SELECT 1 FROM inserted) AND EXISTS (SELECT 1 FROM deleted)
    BEGIN
        -- 监听 field0027（下柜资金）变化
        INSERT INTO Middle_Insert_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT('field0027=', CAST(i.field0027 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        JOIN deleted d ON i.id = d.id
        WHERE (i.field0027 <> d.field0027
               OR (i.field0027 IS NOT NULL AND d.field0027 IS NULL)
               OR (i.field0027 IS NULL AND d.field0027 IS NOT NULL))
          AND i.field0027 IS NOT NULL;  -- 仅记录非空的变化

        -- 监听 field0029（还本）变化
        INSERT INTO Middle_Insert_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT('field0029=', CAST(i.field0029 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        JOIN deleted d ON i.id = d.id
        WHERE (i.field0029 <> d.field0029
               OR (i.field0029 IS NOT NULL AND d.field0029 IS NULL)
               OR (i.field0029 IS NULL AND d.field0029 IS NOT NULL))
          AND i.field0029 IS NOT NULL;  -- 仅记录非空的变化
    END

    -- 处理新增操作（只有插入，没有旧值）
    IF EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted)
    BEGIN
        -- 新增时记录 field0027（下柜资金）
        INSERT INTO Middle_Insert_Records (target_table_id, monitored_field, create_time)
        SELECT
            id,
            CONCAT('field0027=', CAST(field0027 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted
        WHERE field0027 IS NOT NULL;

        -- 新增时记录 field0029（还本）
        INSERT INTO Middle_Insert_Records (target_table_id, monitored_field, create_time)
        SELECT
            id,
            CONCAT('field0029=', CAST(field0029 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted
        WHERE field0029 IS NOT NULL;
    END
END;