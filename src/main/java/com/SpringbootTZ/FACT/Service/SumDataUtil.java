package com.SpringbootTZ.FACT.Service;

import com.SpringbootTZ.FACT.Mapper.InterestNotifyMapper;
import com.SpringbootTZ.FACT.Mapper.SumDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 合计数据工具类
 * 用于计算明细表的合计值并更新到主表
 * 
 * 因表单求sum的时候，存在一些问题无法触发，所以代码每次计算的时候自动更新一下
 * 例如（分期还款计划表 formmain_0039）：
 * - field0034: 下柜资金合计 (NUMERIC(20,6))
 * - field0036: 计划还本合计 (NUMERIC(20,2))
 * - field0037: 模拟付息合计 (NUMERIC(20,10))
 * 
 * 公式：该formmain_id相同，字段值累加
 */
@Service
public class SumDataUtil {

    private static final Logger log = LoggerFactory.getLogger(SumDataUtil.class);

    private final SumDataMapper sumDataMapper;
    private final InterestNotifyMapper interestNotifyMapper;

    @Autowired
    public SumDataUtil(SumDataMapper sumDataMapper, InterestNotifyMapper interestNotifyMapper) {
        this.sumDataMapper = sumDataMapper;
        this.interestNotifyMapper = interestNotifyMapper;
    }

    /**
     * 计算明细表中指定字段的合计值
     * 
     * @param detailTableName 明细表名（如：formson_0030）
     * @param formmainId      主表ID
     * @param fieldName       字段名（如：field0027 下柜资金）
     * @return 合计值（字符串格式，保留足够的小数位）
     */
    public String calculateSum(String detailTableName, String formmainId, String fieldName) {
        try {
            if (detailTableName == null || detailTableName.trim().isEmpty()) {
                log.error("明细表名为空，无法计算合计值");
                return "0";
            }
            if (formmainId == null || formmainId.trim().isEmpty()) {
                log.error("主表ID为空，无法计算合计值");
                return "0";
            }
            if (fieldName == null || fieldName.trim().isEmpty()) {
                log.error("字段名为空，无法计算合计值");
                return "0";
            }

            String sumValue = sumDataMapper.calculateSum(detailTableName, formmainId, fieldName);

            if (sumValue == null || sumValue.trim().isEmpty()) {
                log.debug("合计值为空，返回0，detailTableName: {}, formmainId: {}, fieldName: {}",
                        detailTableName, formmainId, fieldName);
                return "0";
            }

            log.debug("计算合计值成功，detailTableName: {}, formmainId: {}, fieldName: {}, sumValue: {}",
                    detailTableName, formmainId, fieldName, sumValue);
            return sumValue;

        } catch (Exception e) {
            log.error("计算合计值失败，detailTableName: {}, formmainId: {}, fieldName: {}",
                    detailTableName, formmainId, fieldName, e);
            return "0";
        }
    }

    /**
     * 将数值字符串转换为标准格式（避免科学计数法）
     * 
     * @param value 数值字符串（可能包含科学计数法，如 "0E-10"）
     * @return 标准格式的数值字符串（如 "0.0000000000"）
     */
    private String normalizeNumericValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "0";
        }

        try {
            // 去除前后空格
            value = value.trim();

            // 尝试解析为 BigDecimal（支持科学计数法）
            BigDecimal decimal = new BigDecimal(value);

            // 转换为标准格式字符串，保留足够的小数位（最多20位）
            // 使用 toPlainString() 避免科学计数法
            String plainString = decimal.toPlainString();

            // 如果值为0，返回 "0" 而不是 "0.000..."
            if (decimal.compareTo(BigDecimal.ZERO) == 0) {
                return "0";
            }

            return plainString;

        } catch (NumberFormatException e) {
            log.warn("无法解析数值字符串: {}, 返回0", value);
            return "0";
        }
    }

    /**
     * 更新主表中指定字段的值
     * 
     * @param mainTableName 主表名（如：formmain_0029）
     * @param formmainId    主表ID
     * @param fieldName     字段名（如：field0034 下柜资金合计）
     * @param fieldValue    字段值（字符串格式，可能包含科学计数法）
     */
    public void updateFieldValue(String mainTableName, String formmainId, String fieldName, String fieldValue) {
        try {
            if (mainTableName == null || mainTableName.trim().isEmpty()) {
                log.error("主表名为空，无法更新字段值");
                return;
            }
            if (formmainId == null || formmainId.trim().isEmpty()) {
                log.error("主表ID为空，无法更新字段值");
                return;
            }
            if (fieldName == null || fieldName.trim().isEmpty()) {
                log.error("字段名为空，无法更新字段值");
                return;
            }
            if (fieldValue == null) {
                fieldValue = "0";
            }

            // 将科学计数法格式转换为标准数字格式
            String normalizedValue = normalizeNumericValue(fieldValue);

            sumDataMapper.updateFieldValue(mainTableName, formmainId, fieldName, normalizedValue);
            log.debug("更新字段值成功，mainTableName: {}, formmainId: {}, fieldName: {}, fieldValue: {} (原始值: {})",
                    mainTableName, formmainId, fieldName, normalizedValue, fieldValue);

        } catch (Exception e) {
            log.error("更新字段值失败，mainTableName: {}, formmainId: {}, fieldName: {}, fieldValue: {}",
                    mainTableName, formmainId, fieldName, fieldValue, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 计算并更新合计字段（便捷方法）
     * 计算明细表中指定字段的合计值，并更新到主表的对应合计字段
     * 
     * @param detailTableName  明细表名（如：formson_0030）
     * @param mainTableName    主表名（如：formmain_0029）
     * @param formmainId       主表ID
     * @param detailFieldName  明细表字段名（如：field0027 下柜资金）
     * @param summaryFieldName 主表合计字段名（如：field0034 下柜资金合计）
     */
    public void calculateAndUpdateSum(String detailTableName, String mainTableName, String formmainId,
            String detailFieldName, String summaryFieldName) {
        try {
            // 计算合计值
            String sumValue = calculateSum(detailTableName, formmainId, detailFieldName);

            // 更新到主表
            updateFieldValue(mainTableName, formmainId, summaryFieldName, sumValue);

            log.info("计算并更新合计字段完成，detailTableName: {}, mainTableName: {}, formmainId: {}, " +
                    "detailFieldName: {}, summaryFieldName: {}, sumValue: {}",
                    detailTableName, mainTableName, formmainId, detailFieldName, summaryFieldName, sumValue);

        } catch (Exception e) {
            log.error("计算并更新合计字段失败，detailTableName: {}, mainTableName: {}, formmainId: {}, " +
                    "detailFieldName: {}, summaryFieldName: {}",
                    detailTableName, mainTableName, formmainId, detailFieldName, summaryFieldName, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 更新按日还款计划表的所有合计字段
     * 计算明细表（formson_0030）的合计值并更新到主表（formmain_0029）
     * 
     * @param formmainId 主表ID
     */
    public void updateDailyRepaymentPlanSummary(String formmainId) {
        try {
            String detailTableName = "formson_0030";
            String mainTableName = "formmain_0029";

            // 更新下柜资金合计（field0027 -> field0034）
            calculateAndUpdateSum(detailTableName, mainTableName, formmainId, "field0027", "field0034");

            // 更新还本合计（field0029 -> field0036）
            calculateAndUpdateSum(detailTableName, mainTableName, formmainId, "field0029", "field0036");

            // 更新付息合计（field0030 -> field0037）
            String interestSum = calculateSum(detailTableName, formmainId, "field0030");
            updateFieldValue(mainTableName, formmainId, "field0037", interestSum);

            // 更新模拟付息合计（field0039），等于付息合计
            updateFieldValue(mainTableName, formmainId, "field0039", interestSum);

            log.info("更新按日还款计划表的所有合计字段完成，formmainId: {}", formmainId);

        } catch (Exception e) {
            log.error("更新按日还款计划表的所有合计字段失败，formmainId: {}", formmainId, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 更新分期还款计划表的所有合计字段
     * 计算明细表（formson_0043）的合计值并更新到主表（formmain_0039）
     *
     * @param formmainId 分期表主表ID（formmain_0039）
     */
    public void updateInstallmentRepaymentPlanSummary(String formmainId) {
        try {
            String detailTableName = "formson_0043";
            String mainTableName = "formmain_0039";

            // 更新下柜资金合计（field0027 -> field0034）
            calculateAndUpdateSum(detailTableName, mainTableName, formmainId, "field0027", "field0034");

            // 更新计划还本合计（field0029 -> field0036）
            calculateAndUpdateSum(detailTableName, mainTableName, formmainId, "field0029", "field0036");

            // 更新模拟付息合计（field0030 -> field0037）：分期表明细 SUM，不取自按日表
            String interestSum = calculateSum(detailTableName, formmainId, "field0030");
            updateFieldValue(mainTableName, formmainId, "field0037", interestSum);

            // 更新实际付息合计（field0033 -> field0039）
            String actualInterestSum = calculateSum(detailTableName, formmainId, "field0033");
            updateFieldValue(mainTableName, formmainId, "field0039", actualInterestSum);

            log.info("更新分期还款计划表的所有合计字段完成，formmainId: {}", formmainId);

        } catch (Exception e) {
            log.error("更新分期还款计划表的所有合计字段失败，formmainId: {}", formmainId, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 更新利率调整记录表的所有合计字段
     * 计算明细表（formson_0033）的合计值并更新到主表（formmain_0032）
     * 
     * 合计字段映射：
     * - field0027（下柜资金）-> field0034（下柜资金合计）
     * - field0028（贷款余额）-> field0035（贷款余额合计）：取「今天或之后第一个还款日」对应的贷款余额，若没有则为0
     * - field0029（还本）-> field0036（还本合计）
     * - field0030（付息）-> field0037（付息合计）
     * 
     * @param formmainId 主表ID
     */
    public void updateInterestTableSummary(String formmainId) {
        try {
            String detailTableName = "formson_0033";
            String mainTableName = "formmain_0032";

            // 更新下柜资金合计（field0027 -> field0034）
            calculateAndUpdateSum(detailTableName, mainTableName, formmainId, "field0027", "field0034");

            // 更新贷款余额合计（field0028 -> field0035）：取今天或之后第一个还款日的贷款余额，若没有则为0
            String currentDate = java.time.LocalDate.now().toString();
            String loanBalance = interestNotifyMapper.getLoanBalanceForFirstFutureDate(formmainId, currentDate);
            if (loanBalance == null || loanBalance.trim().isEmpty() || "null".equals(loanBalance)) {
                loanBalance = "0";
            }
            updateFieldValue(mainTableName, formmainId, "field0035", loanBalance);

            // 更新还本合计（field0029 -> field0036）
            calculateAndUpdateSum(detailTableName, mainTableName, formmainId, "field0029", "field0036");

            // 更新付息合计（field0030 -> field0037）
            String interestSum = calculateSum(detailTableName, formmainId, "field0030");
            updateFieldValue(mainTableName, formmainId, "field0037", interestSum);

            log.info("更新利率调整记录表的所有合计字段完成，formmainId: {}", formmainId);

        } catch (Exception e) {
            log.error("更新利率调整记录表的所有合计字段失败，formmainId: {}", formmainId, e);
            // 不抛出异常，避免影响主流程
        }
    }
}
