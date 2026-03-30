-- 生成自 form_meta_config.json：rowChange
IF EXISTS (SELECT 1 FROM sys.triggers WHERE name = 'trg_formson_0043_monitor')
DROP TRIGGER trg_formson_0043_monitor;
GO

CREATE TRIGGER trg_formson_0043_monitor
ON formson_0043
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    -- 更新：inserted + deleted
    IF EXISTS (SELECT 1 FROM inserted) AND EXISTS (SELECT 1 FROM deleted)
    BEGIN
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT(N'field0027=', CAST(i.field0027 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        JOIN deleted d ON i.id = d.id
        JOIN formmain_0039 m ON i.formmain_id = m.ID
        LEFT JOIN CTP_ENUM_ITEM ei1 ON m.field0013 = ei1.ID
        LEFT JOIN CTP_ENUM_ITEM ei2 ON m.field0014 = ei2.ID
        WHERE (i.field0027 <> d.field0027 OR (i.field0027 IS NOT NULL AND d.field0027 IS NULL) OR (i.field0027 IS NULL AND d.field0027 IS NOT NULL))
          AND i.field0027 IS NOT NULL
          AND ei1.SHOWVALUE = N'分期还本' AND (ei2.SHOWVALUE <> N'分期付息' OR ei2.SHOWVALUE IS NULL);


        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT(N'field0029=', CAST(i.field0029 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        JOIN deleted d ON i.id = d.id
        JOIN formmain_0039 m ON i.formmain_id = m.ID
        LEFT JOIN CTP_ENUM_ITEM ei1 ON m.field0013 = ei1.ID
        LEFT JOIN CTP_ENUM_ITEM ei2 ON m.field0014 = ei2.ID
        WHERE (i.field0029 <> d.field0029 OR (i.field0029 IS NOT NULL AND d.field0029 IS NULL) OR (i.field0029 IS NULL AND d.field0029 IS NOT NULL))
          AND i.field0029 IS NOT NULL
          AND ei1.SHOWVALUE = N'分期还本' AND (ei2.SHOWVALUE <> N'分期付息' OR ei2.SHOWVALUE IS NULL);

    END

    -- 新增：只有 inserted
    IF EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted)
    BEGIN
        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT(N'field0027=', CAST(i.field0027 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        JOIN formmain_0039 m ON i.formmain_id = m.ID
        LEFT JOIN CTP_ENUM_ITEM ei1 ON m.field0013 = ei1.ID
        LEFT JOIN CTP_ENUM_ITEM ei2 ON m.field0014 = ei2.ID
        WHERE i.field0027 IS NOT NULL
          AND ei1.SHOWVALUE = N'分期还本' AND (ei2.SHOWVALUE <> N'分期付息' OR ei2.SHOWVALUE IS NULL);


        INSERT INTO Middle_Installment_Records (target_table_id, monitored_field, create_time)
        SELECT
            i.id,
            CONCAT(N'field0029=', CAST(i.field0029 AS NVARCHAR(50))),
            GETDATE()
        FROM inserted i
        JOIN formmain_0039 m ON i.formmain_id = m.ID
        LEFT JOIN CTP_ENUM_ITEM ei1 ON m.field0013 = ei1.ID
        LEFT JOIN CTP_ENUM_ITEM ei2 ON m.field0014 = ei2.ID
        WHERE i.field0029 IS NOT NULL
          AND ei1.SHOWVALUE = N'分期还本' AND (ei2.SHOWVALUE <> N'分期付息' OR ei2.SHOWVALUE IS NULL);

    END
END;
GO

