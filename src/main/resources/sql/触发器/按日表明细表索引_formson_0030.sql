-- 按日表明细表 formson_0030 索引：便于 updateDailyData 范围查询走索引，减少锁竞争与死锁
-- 使用前请确认表名（含动态表名时由实施替换）
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_formson_0030_formmain_date' AND object_id = OBJECT_ID('formson_0030'))
BEGIN
    CREATE INDEX ix_formson_0030_formmain_date ON formson_0030 (formmain_id, field0026);
END
GO
