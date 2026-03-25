(function (factory) {
    let nameSpace = 'field_-8527130036398111250';
    // 解决当前页面有多个相同自定义控件产生冲突的问题
    if (!window[nameSpace]) {
        let Builder = factory();
        window[nameSpace] = {
            instance: {}
        };
        window[nameSpace].init = function (options) {
            // 存放当前对象的实例，privateId不同，所以不会重复
            window[nameSpace].instance[options.privateId] = new Builder(options);
        };
    }
})(function () {
    function App(options) {
        // 保存配置项
        this.options = {
            apiUrl: 'http://localhost:8089/api/daily-repayment-plan/calculate',
            ...options
        };
        // 初始化参数对象
        this.loanQueryParam = this.buildLoanQueryParam();

        // 在App实例初始化时自动发送请求
        this.init().catch(err => {
            console.error('初始化请求失败:', err.message);
        });

    }

    // 新增 init 方法
    App.prototype.init = async function () {
        return this.sendRequest();
    };

    // 构建贷款查询参数
    App.prototype.buildLoanQueryParam = function () {
        // 获取主表数据
        //是否添加明细表
        let needDetail = getMainTableData("field0024").showValue;
        if(needDetail == ""){
            needDetail = true;
        }else{
            needDetail = false;
        }
        //贷款流水号
        const loanSerialNo = getMainTableData("field0023").showValue;
        //贷款起始日期
        const loanStartTime = getMainTableData("field0001").showValue;
        //贷款结束日期
        const loanEndTime = getMainTableData("field0002").showValue;
        //年化利率
        const annualInterestRateRaw = getMainTableData("field0004").showValue;
        console.log(annualInterestRateRaw.showValue);
        //还款方式————三种：分期还本、一次性还本、到期还本
        const principalRepaymentMode = getMainTableData("field0014").showValue;
        //利息支付方式————四种：按年付息、按季付息、按月付息、分期付息(分期付息没有规律，用户自己去导入数据，开发无需处理)
        const interestPaymentMode = getMainTableData("field0015").showValue;
        //贷款银行
        const loanBank = getMainTableData("field0025").showValue;

        // 处理年化利率：去除百分号并转换为小数
        const annualInterestRate = this.handleInterestRate(annualInterestRateRaw);

        // 组装参数对象（按接口要求格式）
        return {
            needDetail: this.convertToBoolean(needDetail), // 确保是布尔类型
            loanSerialNo: loanSerialNo || '', // 默认为空字符串
            loanStartTime: loanStartTime || '',
            loanEndTime: loanEndTime || '',
            annualInterestRate: annualInterestRate, // 处理后的小数
            principalRepaymentMode: principalRepaymentMode || '',
            interestPaymentMode: interestPaymentMode || '',
            loanBank: loanBank || '',
            extParams: {} // 初始化扩展参数为空对象
        };
    };

    // 处理年化利率：去除%并转为小数
    App.prototype.handleInterestRate = function (rawValue) {
        if (!rawValue && rawValue !== 0) {
            console.warn('年化利率未提供，返回null');
            return null;
        }

        // 统一转为字符串处理
        let rateStr = String(rawValue).trim();

        // 去除百分号（如果有）
        if (rateStr.includes('%')) {
            rateStr = rateStr.replace('%', '').trim();
        }

        // 转换为数字
        const rateNumber = parseFloat(rateStr);

        // 验证数字有效性
        if (isNaN(rateNumber)) {
            console.error(`无效的年化利率值: ${rawValue}`);
            return null;
        }

        // 转换为小数（例如6.5 → 0.065）
        return rateNumber / 100;
    };

    // 转换为布尔值（处理可能的字符串或其他类型）
    App.prototype.convertToBoolean = function (value) {
        if (typeof value === 'boolean') {
            return value;
        }
        if (typeof value === 'string') {
            return value.toLowerCase() === 'true';
        }
        return !!value; // 其他类型转为布尔值
    };

    // 发送请求到后端API
    App.prototype.sendRequest = async function () {
        try {
            // 先验证参数有效性
            if (!this.validateParams()) {
                console.error('参数验证失败，无法发送请求');
                return null;
            }

            // 构建请求体
            const requestBody = {
                loanQueryParam: this.loanQueryParam
            };

            // 发送请求
            const response = await fetch(this.options.apiUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    // 可以添加其他需要的请求头
                    // 'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify(requestBody),
                // 处理跨域凭证，如果需要的话
                // credentials: 'include'
            });

            // 检查响应状态
            if (!response.ok) {
                throw new Error(`HTTP请求失败，状态码: ${response.status}`);
            }

            // 解析响应数据
            const result = await response.json();
            console.log('请求成功，返回数据:', result);
            
            // 如果计算成功，根据处理方式决定后续操作
            if (result.success) {
                if (result.data && result.data.status === 'processing') {
                    // 大数据量异步处理，显示提示并轮询状态
                    console.log('大数据量计算中，预计需要:', result.data.estimatedTime);
                    this.showProcessingMessage(result.data);
                    await this.pollCalculationStatus(result.data.loanSerialNo);
                } else {
                    // 小数据量同步处理，直接获取合计数据
                    await this.requestSummaryData();
                }
            }
            
            return result;

        } catch (error) {
            console.error('发送请求时发生错误:', error.message);
            // 可以在这里添加错误上报逻辑
            return null;
        }
    };

    // 参数验证
    App.prototype.validateParams = function () {
        const { annualInterestRate, loanStartTime, loanEndTime } = this.loanQueryParam;

        // 验证关键参数
        if (annualInterestRate === null) {
            console.error('年化利率无效');
            return false;
        }

        if (!loanStartTime) {
            console.error('贷款起始日期不能为空');
            return false;
        }

        if (!loanEndTime) {
            console.error('贷款结束日期不能为空');
            return false;
        }

        // 验证日期有效性
        if (new Date(loanStartTime).toString() === 'Invalid Date') {
            console.error('贷款起始日期格式无效');
            return false;
        }

        if (new Date(loanEndTime).toString() === 'Invalid Date') {
            console.error('贷款结束日期格式无效');
            return false;
        }

        return true;
    };

    // 刷新参数并重新发送请求
    App.prototype.refreshAndSend = async function () {
        this.loanQueryParam = this.buildLoanQueryParam();
        return this.sendRequest();
    };

    // 显示大数据量处理提示
    App.prototype.showProcessingMessage = function (data) {
        console.log(`正在处理大数据量计算...`);
        console.log(`记录数: ${data.recordCount}`);
        console.log(`预计时间: ${data.estimatedTime}`);
        console.log(`处理方式: ${data.calculationMethod}`);
        
        // 这里可以添加UI提示，比如显示进度条或提示框
        // 由于是表单控件，暂时只在控制台输出
    };

    // 轮询计算状态
    App.prototype.pollCalculationStatus = async function (loanSerialNo) {
        const maxAttempts = 60; // 最多轮询60次（5分钟）
        const interval = 5000; // 每5秒轮询一次
        
        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                console.log(`第${attempt}次查询计算状态...`);
                
                const response = await fetch('http://localhost:8089/api/daily-repayment-plan/status', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ loanSerialNo: loanSerialNo })
                });

                if (!response.ok) {
                    throw new Error(`状态查询失败，状态码: ${response.status}`);
                }

                const result = await response.json();
                console.log('状态查询结果:', result);

                if (result.success && result.data) {
                    if (result.data.status === 'completed') {
                        console.log('计算完成！');
                        // 计算完成后获取合计数据
                        await this.requestSummaryData();
                        return;
                    } else if (result.data.status === 'failed') {
                        console.error('计算失败:', result.data.message);
                        return;
                    }
                    // 如果状态是processing，继续轮询
                }

                // 等待下次轮询
                await new Promise(resolve => setTimeout(resolve, interval));
                
            } catch (error) {
                console.error(`第${attempt}次状态查询失败:`, error.message);
                if (attempt === maxAttempts) {
                    console.error('达到最大轮询次数，停止查询');
                    return;
                }
                // 等待后重试
                await new Promise(resolve => setTimeout(resolve, interval));
            }
        }
        
        console.warn('轮询超时，计算可能仍在进行中');
    };


    // 请求合计数据
    App.prototype.requestSummaryData = async function () {
        try {
            const loanSerialNo = this.loanQueryParam.loanSerialNo;
            
            if (!loanSerialNo) {
                console.error('贷款流水号为空，无法请求合计数据');
                return;
            }

            const requestBody = {
                loanSerialNo: loanSerialNo
            };

            const response = await fetch('http://localhost:8089/api/daily-repayment-plan/summary', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                throw new Error(`合计接口请求失败，状态码: ${response.status}`);
            }

            const result = await response.json();
            console.log('合计数据请求成功:', result);

            if (result.success && result.data) {
                this.setSummaryDataToForm(result.data);
            } else {
                console.error('合计数据请求失败:', result.message);
            }

        } catch (error) {
            console.error('请求合计数据时发生错误:', error.message);
        }
    };

    // 将合计数据设置到表单字段
    App.prototype.setSummaryDataToForm = function (summaryData) {
        try {
            // 设置下柜资金合计 (field0017)
            this.setFieldData('field0017', summaryData.totalDisbursedAmount);
            
            // 设置贷款余额合计 (field0018)
            this.setFieldData('field0018', summaryData.totalLoanBalance);
            
            // 设置还本合计 (field0019)
            this.setFieldData('field0019', summaryData.totalPrincipalPaid);
            
            // 设置付息合计 (field0020)
            this.setFieldData('field0020', summaryData.totalInterestPaid);

            console.log('合计数据已成功设置到表单字段');
            
        } catch (error) {
            console.error('设置合计数据到表单时发生错误:', error.message);
        }
    };

    // 设置单个字段数据的通用方法
    App.prototype.setFieldData = function (fieldId, value) {
        try {
            // 格式化数值显示
            const displayValue = this.formatNumber(value);
            
            // 确保value是正确的数值格式（保留2位小数）
            const numericValue = this.formatNumericValue(value);
            
            console.log(`设置字段 ${fieldId}: 原始值=${value}, 数值=${numericValue}, 显示值=${displayValue}`);
            
            const data = {
                fieldId: fieldId,
                fieldData: {
                    value: numericValue, // 数据值，存入数据库中的value值（数值格式）
                    display: displayValue, // 字段渲染在页面上的显示值（格式化显示）
                    placeHolder: '请输入数值', // input或其它控件输入提示语
                    auth: 'browse', // 字段权限，只能修改为browse(浏览)或hide(隐藏)
                    atts: [] // 图片、文档、附件等附件类型字段的附件信息
                }
            };
            
            // 设置单个主表字段值
            csdk.core.setFieldData(data);
            
            console.log(`字段 ${fieldId} 设置完成`);
            
        } catch (error) {
            console.error(`设置字段${fieldId}数据失败:`, error.message);
        }
    };
    
    // 格式化数值为数据库存储格式（保留2位小数，不添加千分位分隔符）
    App.prototype.formatNumericValue = function (value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '0.00';
        }
        
        const num = parseFloat(value);
        return num.toFixed(2); // 保留2位小数，不添加千分位分隔符
    };

    // 格式化数值显示（保留2位小数，添加千分位分隔符）
    App.prototype.formatNumber = function (value) {
        if (value === null || value === undefined || isNaN(value)) {
            return '0.00';
        }
        
        const num = parseFloat(value);
        return num.toLocaleString('zh-CN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    };

    // 获取主表数据
    function getMainTableData(fieldId) {
        try {
            const opts = { fieldId: fieldId };
            const data = csdk.core.getFieldData(opts);

            if (data === undefined || data === null) {
                console.warn(`字段${fieldId}未返回有效数据`);
                return null;
            }
            return data;
        } catch (error) {
            console.error(`获取字段${fieldId}数据失败:`, error.message);
            return null;
        }
    }

    return App;
});