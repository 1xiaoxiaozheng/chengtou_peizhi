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
 * 通用代理接口
 * 用于前端通过一个接口转发到多个实际接口，解决网络代理问题
 */
@RestController
@RequestMapping("/api/proxy")
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private final InterestPaymentService interestPaymentService;
    private final SimulatedPaymentService simulatedPaymentService;

    @Autowired
    public ProxyController(InterestPaymentService interestPaymentService,
                           SimulatedPaymentService simulatedPaymentService) {
        this.interestPaymentService = interestPaymentService;
        this.simulatedPaymentService = simulatedPaymentService;
    }

    /**
     * 通用代理接口
     * 根据前端传入的接口标识，调用对应的实际接口并返回结果
     * 
     * @param requestBody 包含以下字段：
     *                    - api: 接口标识（如：getPaymentDates）
     *                    - params: 接口参数（Map类型，根据不同的接口传入不同的参数）
     * @return 实际接口的返回结果
     */
    @PostMapping("/forward")
    public Map<String, Object> forward(@RequestBody Map<String, Object> requestBody) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 获取接口标识
            String api = (String) requestBody.get("api");
            if (api == null || api.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "接口标识(api)不能为空");
                response.put("data", null);
                return response;
            }

            // 获取参数
            Map<String, Object> params = new HashMap<>();
            Object paramsObj = requestBody.get("params");
            if (paramsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> tempParams = (Map<String, Object>) paramsObj;
                params = tempParams;
            }

            log.info("收到代理请求，api: {}, params: {}", api, params);

            // 根据接口标识调用对应的实际接口
            Object result = null;
            switch (api) {
                case "getPaymentDates":
                    // 调用获取日期列表接口
                    result = handleGetPaymentDates(params);
                    break;
                case "calculateSimulatedInterest":
                    // 调用模拟付息计算接口（需要完整的requestBody）
                    result = handleCalculateSimulatedInterest(requestBody);
                    break;
                // 可以在这里添加更多接口
                // case "otherApi":
                // result = handleOtherApi(params);
                // break;
                default:
                    response.put("success", false);
                    response.put("message", "未知的接口标识: " + api);
                    response.put("data", null);
                    return response;
            }

            // 如果返回结果已经是Map格式，直接返回
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }

            // 否则包装成标准格式
            response.put("success", true);
            response.put("message", "请求成功");
            response.put("data", result);

            log.info("代理请求处理成功，api: {}", api);

        } catch (Exception e) {
            log.error("代理请求处理失败", e);
            response.put("success", false);
            response.put("message", "请求处理失败: " + e.getMessage());
            response.put("data", null);
        }

        return response;
    }

    /**
     * 处理获取日期列表接口
     * 
     * @param params 参数Map，包含serialNumber
     * @return 日期列表响应
     */
    private Map<String, Object> handleGetPaymentDates(Map<String, Object> params) {
        Map<String, Object> response = new HashMap<>();

        try {
            String serialNumber = (String) params.get("serialNumber");
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "单号不能为空");
                response.put("data", null);
                return response;
            }

            log.info("处理获取日期列表请求，serialNumber: {}", serialNumber);

            // 调用服务生成日期列表
            log.info("开始调用 generatePaymentDatesBySerialNumber 方法");
            List<String> dateList = interestPaymentService.generatePaymentDatesBySerialNumber(serialNumber);
            log.info("generatePaymentDatesBySerialNumber 方法调用完成，返回日期数量: {}",
                    dateList != null ? dateList.size() : 0);

            response.put("success", true);
            response.put("message", "获取成功");
            response.put("data", dateList);

            log.info("返回日期列表，serialNumber: {}, 日期数量: {}", serialNumber,
                    dateList != null ? dateList.size() : 0);

        } catch (Exception e) {
            log.error("获取日期列表失败，serialNumber: {}", params.get("serialNumber"), e);
            e.printStackTrace(); // 打印完整堆栈
            response.put("success", false);
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.trim().isEmpty()) {
                errorMsg = e.getClass().getSimpleName();
            }
            response.put("message", "获取日期列表失败: " + errorMsg);
            response.put("data", null);
        }

        log.info("handleGetPaymentDates 方法执行完成，准备返回响应");
        return response;
    }

    /**
     * 处理模拟付息计算接口
     * 
     * @param requestBody 完整的请求体
     * @return 计算结果响应
     */
    private Map<String, Object> handleCalculateSimulatedInterest(Map<String, Object> requestBody) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 获取参数（从完整的requestBody中获取，因为参数结构复杂）
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

            log.info("处理模拟付息计算请求，项目编号: {}, 下柜数量: {}, 还本数量: {}, 时间点数量: {}",
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
