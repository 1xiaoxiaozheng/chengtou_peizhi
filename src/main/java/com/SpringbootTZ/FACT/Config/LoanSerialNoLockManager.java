package com.SpringbootTZ.FACT.Config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 贷款流水号锁管理器
 * 用于防止并发操作同一贷款流水号，确保数据一致性
 * 
 * 使用场景：
 * 1. 用户打开表单触发计算
 * 2. 定时任务处理利率变更
 * 3. 多个用户同时操作同一流水号
 */
@Component
public class LoanSerialNoLockManager {

    // 使用ConcurrentHashMap保证线程安全
    private final Map<String, Object> operationLocks = new ConcurrentHashMap<>();

    /**
     * 获取或创建指定流水号的操作锁
     * 
     * @param loanSerialNo 贷款流水号
     * @return 锁对象
     */
    public Object getLock(String loanSerialNo) {
        return operationLocks.computeIfAbsent(loanSerialNo, k -> new Object());
    }

    /**
     * 移除指定流水号的锁（可选，用于清理不再使用的锁）
     * 
     * @param loanSerialNo 贷款流水号
     */
    public void removeLock(String loanSerialNo) {
        operationLocks.remove(loanSerialNo);
    }
}
