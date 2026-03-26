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
-- 主表字段配置
(N'oa.plan.field.is.add.detail', N'field0061', N'是否添加明细表日期'),
(N'oa.plan.field.start.date', N'field0059', N'开始日期'),
(N'oa.plan.field.end.date', N'field0060', N'结束日期'),
(N'oa.plan.field.bank', N'field0046', N'贷款银行'),
(N'oa.plan.field.rate', N'field0042', N'最新利率'),
(N'oa.plan.field.total.disburse', N'field0034', N'下柜资金合计'),
(N'oa.plan.field.total.balance', N'field0035', N'贷款余额合计'),
(N'oa.plan.field.total.principal', N'field0036', N'还本合计'),
(N'oa.plan.field.total.interest', N'field0037', N'付息合计'),

-- 明细表字段配置
(N'oa.plan.field.seq1', N'field0025', N'序号1'),
(N'oa.plan.field.date', N'field0026', N'时间/日期'),
(N'oa.plan.field.disburse', N'field0027', N'下柜资金'),
(N'oa.plan.field.balance', N'field0028', N'贷款余额'),
(N'oa.plan.field.principal', N'field0029', N'还本'),
(N'oa.plan.field.interest', N'field0030', N'付息'),
(N'oa.plan.field.remark', N'field0031', N'备注-还款计划'),
(N'oa.plan.field.simulate.interest', N'field0033', N'模拟付息'),

-- 基础表/固定表配置
(N'oa.plan.basic.table', N'formmain_0146', N'贷款基础表'),
(N'oa.plan.field.repayment.mode', N'field0014', N'付息模式'),
(N'oa.plan.field.bank.serial', N'field0023', N'银行流水号字段'),
(N'oa.plan.field.bank.name', N'field0025', N'银行名称字段');