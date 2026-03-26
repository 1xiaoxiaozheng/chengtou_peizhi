package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.builder.annotation.ProviderMethodResolver;
import org.apache.ibatis.jdbc.SQL;

import java.util.List;
import java.util.Map;

/**
 * 按日还款计划表 — 中间表 Notify，表名/字段名全部从 oa_dict_config 注入的 cfg 读取。
 * 替换主工程中同接口文件即可（需配合 OaDictConfigMapper + DailyNotifyService 传 cfg）。
 */
@Mapper
public interface DailyNotifyMapper {

    class SqlProvider implements ProviderMethodResolver {

        public static String getStatusNot(Map<String, String> cfg) {
            return new SQL()
                    .SELECT("*")
                    .FROM(cfg.get("middle.insert.table"))
                    .WHERE("process_status = 0")
                    .ORDER_BY("create_time ASC")
                    .toString();
        }

        public static String getPendingSerialNumbers(Map<String, String> cfg) {
            String serial = cfg.get("oa.plan.serial.field");
            String detail = cfg.get("oa.plan.detail.table");
            String main = cfg.get("oa.plan.main.table");
            return new SQL()
                    .SELECT_DISTINCT("m." + serial)
                    .FROM(cfg.get("middle.insert.table") + " mir")
                    .INNER_JOIN(detail + " f ON mir.target_table_id = f.id")
                    .INNER_JOIN(main + " m ON f.formmain_id = m.id")
                    .WHERE("mir.process_status = 0")
                    .WHERE("m." + serial + " IS NOT NULL")
                    .WHERE("m." + serial + " <> ''")
                    .toString();
        }

        public static String getPendingRecordsWithMainTableId(Map<String, String> cfg) {
            String detail = cfg.get("oa.plan.detail.table");
            return new SQL()
                    .SELECT("mir.id, mir.target_table_id, mir.monitored_field, mir.old_value, f.formmain_id")
                    .FROM(cfg.get("middle.insert.table") + " mir")
                    .INNER_JOIN(detail + " f ON mir.target_table_id = f.id")
                    .WHERE("mir.process_status = 0")
                    .ORDER_BY("mir.create_time ASC")
                    .toString();
        }
    }

    @SelectProvider(type = SqlProvider.class)
    List<Map<String, Object>> getStatusNot(Map<String, String> cfg);

    @SelectProvider(type = SqlProvider.class)
    List<String> getPendingSerialNumbers(Map<String, String> cfg);

    @SelectProvider(type = SqlProvider.class)
    List<Map<String, Object>> getPendingRecordsWithMainTableId(Map<String, String> cfg);

    // 含点的 key 必须用 OGNL：cfg['middle.insert.table']，不可用 cfg.middle.insert.table
    @Update("<script>UPDATE ${cfg['middle.insert.table']} SET process_status = 1, process_time = GETDATE() WHERE id = #{id}</script>")
    void updateProcessStatus(@Param("id") Integer id, @Param("cfg") Map<String, String> cfg);

    @Update("<script>UPDATE ${cfg['middle.insert.table']} SET process_status = 2, process_time = GETDATE(), fail_reason = #{failReason} WHERE id = #{id}</script>")
    void updateProcessStatusFailed(@Param("id") Integer id, @Param("failReason") String failReason, @Param("cfg") Map<String, String> cfg);

    @Update("<script>UPDATE ${cfg['middle.insert.table']} SET retry_count = retry_count + 1 WHERE id = #{id}</script>")
    void incrementRetryCount(@Param("id") Integer id, @Param("cfg") Map<String, String> cfg);

    @Update("<script>"
            + "UPDATE ${cfg['middle.insert.table']} SET process_status = 1, process_time = GETDATE() WHERE id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach>"
            + "</script>")
    void batchUpdateProcessStatus(@Param("ids") List<Integer> ids, @Param("cfg") Map<String, String> cfg);

    @Update("<script>"
            + "UPDATE ${cfg['middle.insert.table']} SET process_status = 2, process_time = GETDATE(), fail_reason = #{failReason} WHERE id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach>"
            + "</script>")
    void batchUpdateProcessStatusFailed(@Param("ids") List<Integer> ids, @Param("failReason") String failReason, @Param("cfg") Map<String, String> cfg);

    @Update("<script>"
            + "UPDATE ${cfg['middle.insert.table']} SET retry_count = retry_count + 1 WHERE id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach>"
            + "</script>")
    void batchIncrementRetryCount(@Param("ids") List<Integer> ids, @Param("cfg") Map<String, String> cfg);
}
