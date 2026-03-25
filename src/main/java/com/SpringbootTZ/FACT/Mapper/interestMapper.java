package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface interestMapper {
        /**
         * 新数据字典：利率表 formmain_0032
         * field0001: 单据编号/流水号
         * field0041: 生效时间 (DATETIME)
         * field0042: 利率 (NUMERIC，格式如：.470000，小数格式)
         * 
         * 根据单据编号/流水号查询最新的利率和生效时间（利率变更记录表里面最后的那条记录）
         * 
         * @param serialNumber 单据编号/流水号
         * @return 包含利率和生效时间的Map，key为"利率"和"生效时间"
         */
        @Select("SELECT TOP 1 field0042 as 利率, field0041 as 生效时间 FROM formmain_0032 WHERE field0001 = #{serialNumber} ORDER BY field0041 DESC")
        Map<String, Object> getInterestBySerialNumber(@Param("serialNumber") String serialNumber);

        /**
         * 根据单据编号/流水号查询最新的利率（只返回利率值）
         * 
         * @param serialNumber 单据编号/流水号
         * @return 利率值（小数格式，如：.470000）
         */
        @Select("SELECT TOP 1 field0042 FROM formmain_0032 WHERE field0001 = #{serialNumber} ORDER BY field0041 DESC")
        String getInterest(@Param("serialNumber") String serialNumber);

        /**
         * 根据单据编号/流水号查询所有利率变更记录
         * 
         * @param serialNumber 单据编号/流水号
         * @return 利率变更记录列表，包含 interest_rate (field0042) 和 change_date (field0041)
         */
        @Select("SELECT field0042 as interest_rate, field0041 as change_date " +
                        "FROM formmain_0032 " +
                        "WHERE field0001 = #{serialNumber} " +
                        "ORDER BY field0041 ASC")
        List<Map<String, Object>> getInterestChangesBySerialNumber(@Param("serialNumber") String serialNumber);

        /**
         * 根据单据编号/流水号和日期范围查询利率变更记录
         * 
         * @param serialNumber 单据编号/流水号
         * @param startDate    开始日期
         * @param endDate      结束日期
         * @return 利率变更记录列表
         */
        @Select("SELECT field0042 as interest_rate, field0041 as change_date " +
                        "FROM formmain_0032 " +
                        "WHERE field0001 = #{serialNumber} " +
                        "AND field0041 >= #{startDate} " +
                        "AND field0041 <= #{endDate} " +
                        "ORDER BY field0041 ASC")
        List<Map<String, Object>> getInterestChangesBySerialNumberAndDateRange(
                        @Param("serialNumber") String serialNumber,
                        @Param("startDate") String startDate,
                        @Param("endDate") String endDate);
}
