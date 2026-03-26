/*==========================================================
  通用配置表（SQL Server）
  用于“数据字典 + SQL配置驱动查询”
  依赖：前端页面 /api/dict/{dictType}、/api/query/{sqlKey}
==========================================================*/

/* 1) sys_dict：数据字典表 */
IF OBJECT_ID('dbo.sys_dict', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.sys_dict (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        dict_type NVARCHAR(100) NOT NULL,
        dict_key NVARCHAR(100) NOT NULL,
        dict_value NVARCHAR(255) NOT NULL,
        sort INT NOT NULL CONSTRAINT DF_sys_dict_sort DEFAULT(0),
        status TINYINT NOT NULL CONSTRAINT DF_sys_dict_status DEFAULT(1),
        remark NVARCHAR(255) NULL,
        create_time DATETIME NOT NULL CONSTRAINT DF_sys_dict_create_time DEFAULT(GETDATE()),
        update_time DATETIME NOT NULL CONSTRAINT DF_sys_dict_update_time DEFAULT(GETDATE())
    );

    CREATE UNIQUE INDEX UX_sys_dict_type_key ON dbo.sys_dict(dict_type, dict_key);
    CREATE INDEX IX_sys_dict_dict_type ON dbo.sys_dict(dict_type);
END

/* 2) sys_sql_config：SQL 配置表（存 JSON 字符串） */
IF OBJECT_ID('dbo.sys_sql_config', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.sys_sql_config (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        sql_key NVARCHAR(100) NOT NULL UNIQUE,
        base_table NVARCHAR(100) NOT NULL,
        selectable_fields NVARCHAR(MAX) NULL,  -- JSON数组：["id","field0001",...]
        condition_fields NVARCHAR(MAX) NULL,   -- JSON数组：[{"field":"field0001","op":"eq"},...]
        default_sort NVARCHAR(100) NULL,       -- 建议来自 selectable_fields
        remark NVARCHAR(255) NULL,
        create_time DATETIME NOT NULL CONSTRAINT DF_sys_sql_config_create_time DEFAULT(GETDATE()),
        update_time DATETIME NOT NULL CONSTRAINT DF_sys_sql_config_update_time DEFAULT(GETDATE())
    );

    CREATE INDEX IX_sys_sql_config_base_table ON dbo.sys_sql_config(base_table);
    CREATE INDEX IX_sys_sql_config_sql_key ON dbo.sys_sql_config(sql_key);
END

/*==========================================================
  3) 示例数据（可选）
  - dict_type = user_status
  - sql_key = daily_plan_by_serial
  说明：该 sql_key 的 base_table / 字段必须与实际库字段一致
==========================================================*/

/* 示例：数据字典 */
IF NOT EXISTS (SELECT 1 FROM dbo.sys_dict WHERE dict_type = 'user_status' AND dict_key = '1')
BEGIN
    INSERT INTO dbo.sys_dict(dict_type, dict_key, dict_value, sort, status, remark)
    VALUES ('user_status', '1', '正常', 0, 1, '示例数据');
END

IF NOT EXISTS (SELECT 1 FROM dbo.sys_dict WHERE dict_type = 'user_status' AND dict_key = '0')
BEGIN
    INSERT INTO dbo.sys_dict(dict_type, dict_key, dict_value, sort, status, remark)
    VALUES ('user_status', '0', '禁用', 1, 1, '示例数据');
END

/* 示例：SQL 配置 */
IF NOT EXISTS (SELECT 1 FROM dbo.sys_sql_config WHERE sql_key = 'daily_plan_by_serial')
BEGIN
    /*
      base_table：formmain_0029（项目里 DailyRepaymentPlanMapper 已经使用过）
      selectable_fields：查询字段
      condition_fields：只允许条件字段通过配置定义
      默认排序：field0059（同样在项目 Mapper 里出现过）
      eq：params 里传入 {"field0001":"xxx"} 即可
    */
    INSERT INTO dbo.sys_sql_config(sql_key, base_table, selectable_fields, condition_fields, default_sort, remark)
    VALUES (
        'daily_plan_by_serial',
        'formmain_0029',
        '["id","field0001","field0046","field0059","field0060"]',
        '[{"field":"field0001","op":"eq"}]',
        'field0059',
        '按单据编号查询按日还款计划（示例）'
    );
END

