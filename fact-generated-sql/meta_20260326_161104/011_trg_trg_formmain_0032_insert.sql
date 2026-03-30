-- 生成自 form_meta_config.json：afterInsert
IF EXISTS (SELECT 1 FROM sys.triggers WHERE name = 'trg_formmain_0032_insert')
DROP TRIGGER trg_formmain_0032_insert;
GO

CREATE TRIGGER trg_formmain_0032_insert
ON formmain_0032
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO interest_change_notify (
        source_id,
        bill_no,
        current_rate,
        rate_effective_time,
        process_status
    )
    SELECT
        i.id,
        i.field0001,
        i.field0042,
        i.field0041,
        0
    FROM INSERTED i
    WHERE
        i.field0042 IS NOT NULL
        AND i.field0041 IS NOT NULL
END;
GO

