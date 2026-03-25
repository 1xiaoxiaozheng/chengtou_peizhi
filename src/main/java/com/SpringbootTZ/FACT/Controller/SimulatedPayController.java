package com.SpringbootTZ.FACT.Controller;

import com.SpringbootTZ.FACT.Service.SimulatedPaymentService;
import com.SpringbootTZ.FACT.Service.installmentPayment.InterestPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于模拟付息自定义控件的交互
 */
@RestController
@RequestMapping("/api/simulated-pay")
public class SimulatedPayController {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPayController.class);

    private final InterestPaymentService interestPaymentService;
    private final SimulatedPaymentService simulatedPaymentService;

    @Autowired
    public SimulatedPayController(InterestPaymentService interestPaymentService,
                                  SimulatedPaymentService simulatedPaymentService) {
        this.interestPaymentService = interestPaymentService;
        this.simulatedPaymentService = simulatedPaymentService;
    }

    /**
     * 接口一：接收单号，返回日期列表给前端
     * 
     * @param serialNumber 单据编号（流水号）
     * @return 包含日期列表的响应对象
     */
    @GetMapping("/dates")
    public Map<String, Object> getPaymentDates(@RequestParam("serialNumber") String serialNumber) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "单号不能为空");
                response.put("data", null);
                return response;
            }

            log.info("收到获取日期列表请求，serialNumber: {}", serialNumber);

            // 调用服务生成日期列表
            List<String> dateList = interestPaymentService.generatePaymentDatesBySerialNumber(serialNumber);

            response.put("success", true);
            response.put("message", "获取成功");
            response.put("data", dateList);

            log.info("返回日期列表，serialNumber: {}, 日期数量: {}", serialNumber, dateList.size());

        } catch (Exception e) {
            log.error("获取日期列表失败，serialNumber: {}", serialNumber, e);
            response.put("success", false);
            response.put("message", "获取日期列表失败: " + e.getMessage());
            response.put("data", null);
        }

        return response;
    }

    /**
     * 接口二：模拟付息计算
     * 接收下柜、还本配置和时间表，返回每个时间点的贷款余额和付息
     * 
     * @param requestBody 请求体，包含：
     *                    - projectSerialNumber: 项目编号
     *                    - drawdownConfig: 下柜配置列表
     *                    - repayment: 还本配置列表
     *                    - timeTableList: 时间表列表
     * @return 计算结果列表
     */
    @PostMapping("/calculate")
    public Map<String, Object> calculateSimulatedInterest(@RequestBody Map<String, Object> requestBody) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 获取参数
            String projectSerialNumber = (String) requestBody.get("projectSerialNumber");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> drawdownConfig = (List<Map<String, Object>>) requestBody.get("drawdownConfig");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> repayment = (List<Map<String, Object>>) requestBody.get("repayment");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> timeTableList = (List<Map<String, Object>>) requestBody.get("timeTableList");

            // 参数验证
            if (projectSerialNumber == null || projectSerialNumber.trim().isEmpty()) {
                response.put("success", false);
                response.put("msg", "项目编号不能为空");
                response.put("data", null);
                return response;
            }

            if (drawdownConfig == null) {
                drawdownConfig = new java.util.ArrayList<>();
            }
            if (repayment == null) {
                repayment = new java.util.ArrayList<>();
            }
            if (timeTableList == null || timeTableList.isEmpty()) {
                response.put("success", false);
                response.put("msg", "时间表不能为空");
                response.put("data", null);
                return response;
            }

            log.info("收到模拟付息计算请求，项目编号: {}, 下柜数量: {}, 还本数量: {}, 时间点数量: {}",
                    projectSerialNumber, drawdownConfig.size(), repayment.size(), timeTableList.size());

            // 调用服务计算
            List<Map<String, Object>> resultList = simulatedPaymentService.calculateSimulatedInterest(
                    projectSerialNumber,
                    drawdownConfig,
                    repayment,
                    timeTableList);

            response.put("success", true);
            response.put("msg", "计息计算成功");
            response.put("data", resultList);

            log.info("模拟付息计算完成，项目编号: {}, 返回结果数量: {}", projectSerialNumber, resultList.size());

        } catch (Exception e) {
            log.error("模拟付息计算失败", e);
            response.put("success", false);
            response.put("msg", "计息计算失败: " + e.getMessage());
            response.put("data", null);
        }

        return response;
    }
}
