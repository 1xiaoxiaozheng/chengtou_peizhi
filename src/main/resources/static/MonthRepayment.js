(function (factory) {
    let nameSpace = 'field_-4066558273311850158';
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

    // 构建贷款查询参数
    App.prototype.buildLoanQueryParam = function () {
        // 获取主表数据
        const needDetail = getMainTableData("field0024");
        const loanSerialNo = getMainTableData("field0023");
        const loanStartTime = getMainTableData("field0001");
        const loanEndTime = getMainTableData("field0002");
        const annualInterestRateRaw = getMainTableData("field0004");
        const principalRepaymentMode = getMainTableData("field0014");
        const interestPaymentMode = getMainTableData("field0015");
        const loanBank = getMainTableData("field0025");

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