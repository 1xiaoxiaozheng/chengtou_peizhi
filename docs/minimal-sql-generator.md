# 最小 SQL 生成器使用说明

## 目标

基于 `src/main/resources/config/form_meta_config.json` 生成 SQL 脚本文件（只生成，不执行数据库）。

## 已有能力

- 生成器服务：`com.SpringbootTZ.FACT.Service.FormMetaSqlGenerator`
- 后端接口：`POST /api/admin/meta/generate`
- CLI 入口：`com.SpringbootTZ.FACT.Tools.MetaSqlGenerateCli`

## 方式一：不启动 Spring，直接命令行生成（推荐）

在项目根目录执行：

```bash
mvn -DskipTests "-Dexec.mainClass=com.SpringbootTZ.FACT.Tools.MetaSqlGenerateCli" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

可选：指定输出根目录（会自动创建 `meta_yyyyMMdd_HHmmss` 子目录）：

```bash
mvn -DskipTests "-Dexec.mainClass=com.SpringbootTZ.FACT.Tools.MetaSqlGenerateCli" "-Dexec.args=F:/tmp/sql-out" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

## 方式二：HTTP 接口生成

接口：

- `POST /api/admin/meta/generate`

请求体（可选）：

```json
{ "outDir": "F:/tmp/sql-out" }
```

不传 `outDir` 时，默认输出到：`{user.dir}/fact-generated-sql/meta_yyyyMMdd_HHmmss`

## 当前产物范围（最小版）

- 中间表 DDL（项目内已识别表）
- `rowChange` / `afterInsert` 事件触发器脚本

## 注意事项

- 这是“生成器优先”的最小实现：可评审、可入库、可走 Git，但不自动执行。
- 若 `form_meta_config.json` 结构变化，需同步调整生成规则。
