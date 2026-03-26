-- 按日还款中间表 + OA 表名/字段名 可配置：在业务库执行一次即可。
-- 说明：本文件仅存放在 reference 目录供复制，不会自动执行。

CREATE TABLE oa_dict_config (
  id int IDENTITY(1,1) NOT NULL PRIMARY KEY,
  config_key varchar(100) NOT NULL,
  config_value varchar(255) NOT NULL,
  config_name varchar(100) NULL,
  CONSTRAINT uk_oa_dict_config_key UNIQUE (config_key)
);

INSERT INTO oa_dict_config (config_key, config_value, config_name) VALUES
(N'middle.insert.table', N'Middle_Insert_Records', N'中间表名'),
(N'oa.plan.detail.table', N'formson_0030', N'按日还款计划表明细表'),
(N'oa.plan.main.table', N'formmain_0029', N'按日还款计划表主表'),
(N'oa.plan.serial.field', N'field0001', N'主表单据编号字段');


