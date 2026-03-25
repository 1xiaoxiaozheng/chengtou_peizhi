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
            ...options
        };
        // 保存控件容器元素（用于显示加载圈圈）
        this.containerElement = options.containerElement || null;
        
        // 初始化按钮重试计数器
        this._buttonInitRetryCount = 0;
        
        // 注意：不在初始化时获取项目编号，而是在使用时动态获取
        // 这样可以支持用户新建表单后选择项目，或更换项目的情况
        
        // 初始化按钮（延迟执行，确保DOM已加载）
        setTimeout(() => {
            this.initCalculateButton();
        }, 500);
    }


    // 构建贷款查询参数（动态获取，支持用户新建后选择项目或更换项目）
    App.prototype.buildLoanQueryParam = function () {
        // 获取主表表单编号（field0022）
        const formSerialNumber = getMainTableData("field0022");
        
        // 优先使用 value（实际值），如果没有则使用 showValue（显示值）
        let serialNumber = '';
        if (formSerialNumber) {
            if (formSerialNumber.value !== undefined && formSerialNumber.value !== null && formSerialNumber.value !== '') {
                serialNumber = String(formSerialNumber.value).trim();
            } else if (formSerialNumber.showValue !== undefined && formSerialNumber.showValue !== null && formSerialNumber.showValue !== '') {
                serialNumber = String(formSerialNumber.showValue).trim();
            }
        }

        // 组装参数对象
        return {
            serialNumber: serialNumber || '' // 表单编号
        };
    };


    // 显示加载圈圈
    App.prototype.showLoading = function (message) {
        // 如果没有传入消息，使用默认消息
        const loadingMessage = message || '正在加载...';
        try {
            // 先尝试移除已存在的加载圈圈和遮罩层
            this.hideLoading();
            
            // 创建灰色遮罩层（覆盖整个页面，阻止用户操作）
            const overlayId = 'date-loading-overlay-' + (this.options.privateId || Date.now());
            const overlay = document.createElement('div');
            overlay.id = overlayId;
            overlay.className = 'date-loading-overlay';
            overlay.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.3);
                z-index: 99998;
                pointer-events: auto;
                cursor: not-allowed;
            `;
            document.body.appendChild(overlay);
            this.loadingOverlay = overlay;
            
            // 获取控件容器元素
            let container = this.containerElement;
            if (!container) {
                // 尝试通过 options 获取
                if (this.options && this.options.containerElement) {
                    container = this.options.containerElement;
                } else {
                    // 尝试通过字段ID查找容器
                    const fieldId = this.options && this.options.fieldId ? this.options.fieldId : 'field_-8527130036398111250';
                    // 尝试多种方式查找容器
                    container = document.querySelector(`[data-field-id="${fieldId}"]`) || 
                               document.querySelector(`#${fieldId}`) ||
                               document.querySelector(`[id*="${fieldId}"]`) ||
                               document.querySelector(`[fieldid="${fieldId}"]`) ||
                               document.querySelector('.custom-field-container') ||
                               document.querySelector('.field-container');
                }
            }
            
            // 如果还是找不到容器，尝试通过 privateId 查找
            if (!container && this.options && this.options.privateId) {
                const privateId = this.options.privateId;
                container = document.querySelector(`[privateid="${privateId}"]`) ||
                           document.querySelector(`[data-private-id="${privateId}"]`);
            }
            
            // 如果还是找不到，使用 body 作为容器（浮动显示）
            if (!container) {
                container = document.body;
            }
            
            // 创建加载圈圈元素
            const loadingElement = document.createElement('div');
            loadingElement.className = 'date-loading-spinner';
            loadingElement.id = 'date-loading-spinner-' + (this.options.privateId || Date.now());
            
            // 根据容器类型设置不同的样式
            if (container === document.body) {
                // 如果是 body，使用固定定位，居中显示
                loadingElement.style.cssText = `
                    position: fixed;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    z-index: 99999;
                    background: rgba(255, 255, 255, 0.95);
                    padding: 15px 25px;
                    border-radius: 6px;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
                    pointer-events: auto;
                `;
            } else {
                // 如果是控件容器，使用绝对定位
                loadingElement.style.cssText = `
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    z-index: 9999;
                    background: rgba(255, 255, 255, 0.95);
                    padding: 15px 25px;
                    border-radius: 6px;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
                    pointer-events: auto;
                `;
                // 确保容器有相对定位
                const containerStyle = window.getComputedStyle(container);
                if (containerStyle.position === 'static') {
                    container.style.position = 'relative';
                }
            }
            
            loadingElement.innerHTML = `
                <div style="
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: #1890ff;
                    font-size: 14px;
                    white-space: nowrap;
                ">
                    <div style="
                        width: 20px;
                        height: 20px;
                        border: 3px solid #f3f3f3;
                        border-top: 3px solid #1890ff;
                        border-radius: 50%;
                        animation: date-loading-spin 1s linear infinite;
                        margin-right: 10px;
                    "></div>
                    <span>${loadingMessage}</span>
                </div>
            `;
            
            // 添加旋转动画样式（如果还没有）
            if (!document.getElementById('date-loading-style')) {
                const style = document.createElement('style');
                style.id = 'date-loading-style';
                style.textContent = `
                    @keyframes date-loading-spin {
                        0% { transform: rotate(0deg); }
                        100% { transform: rotate(360deg); }
                    }
                `;
                document.head.appendChild(style);
            }
            
            container.appendChild(loadingElement);
            this.loadingElement = loadingElement;
            console.log('显示加载圈圈和遮罩层');
            
        } catch (error) {
            console.error('显示加载圈圈失败:', error.message);
        }
    };

    // 隐藏加载圈圈
    App.prototype.hideLoading = function () {
        try {
            // 先尝试移除保存的遮罩层引用
            if (this.loadingOverlay) {
                try {
                    if (this.loadingOverlay.parentNode) {
                        this.loadingOverlay.parentNode.removeChild(this.loadingOverlay);
                    }
                } catch (e) {
                    // 忽略移除错误
                }
                this.loadingOverlay = null;
            }
            
            // 强制查找并移除所有遮罩层（确保全部移除）
            const overlays = document.querySelectorAll('.date-loading-overlay');
            overlays.forEach(element => {
                try {
                    if (element && element.parentNode) {
                        element.parentNode.removeChild(element);
                    }
                } catch (e) {
                    // 忽略单个元素的移除错误，继续移除其他元素
                }
            });
            
            // 先尝试移除保存的加载元素引用
            if (this.loadingElement) {
                try {
                    if (this.loadingElement.parentNode) {
                        this.loadingElement.parentNode.removeChild(this.loadingElement);
                    }
                } catch (e) {
                    // 忽略移除错误
                }
                this.loadingElement = null;
            }
            
            // 强制查找并移除所有加载圈圈（确保全部移除）
            const loadingElements = document.querySelectorAll('.date-loading-spinner');
            loadingElements.forEach(element => {
                try {
                    if (element && element.parentNode) {
                        element.parentNode.removeChild(element);
                    }
                } catch (e) {
                    // 忽略单个元素的移除错误，继续移除其他元素
                }
            });
            
            if (loadingElements.length > 0 || overlays.length > 0) {
                console.log(`隐藏加载圈圈和遮罩层（移除了 ${loadingElements.length} 个加载圈圈，${overlays.length} 个遮罩层）`);
            }
        } catch (error) {
            console.error('隐藏加载圈圈失败:', error.message);
            // 即使出错也尝试强制移除
            try {
                const allLoading = document.querySelectorAll('.date-loading-spinner');
                allLoading.forEach(el => {
                    try {
                        if (el && el.parentNode) {
                            el.parentNode.removeChild(el);
                        }
                    } catch (e) {
                        // 忽略
                    }
                });
                
                const allOverlays = document.querySelectorAll('.date-loading-overlay');
                allOverlays.forEach(el => {
                    try {
                        if (el && el.parentNode) {
                            el.parentNode.removeChild(el);
                        }
                    } catch (e) {
                        // 忽略
                    }
                });
            } catch (e) {
                // 忽略
            }
        }
    };

    // 显示错误提示（替代 alert，更友好的UI）
    App.prototype.showErrorDialog = function (title, errors) {
        try {
            // 先移除已存在的错误提示
            this.hideErrorDialog();
            
            // 创建遮罩层
            const overlayId = 'date-error-overlay-' + (this.options.privateId || Date.now());
            const overlay = document.createElement('div');
            overlay.id = overlayId;
            overlay.className = 'date-error-overlay';
            overlay.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.5);
                z-index: 100000;
                display: flex;
                align-items: center;
                justify-content: center;
            `;
            
            // 创建错误提示框
            const errorDialog = document.createElement('div');
            errorDialog.className = 'date-error-dialog';
            errorDialog.style.cssText = `
                background: #fff;
                border-radius: 8px;
                box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
                max-width: 600px;
                width: 90%;
                max-height: 80vh;
                overflow: hidden;
                display: flex;
                flex-direction: column;
            `;
            
            // 标题栏
            const header = document.createElement('div');
            header.style.cssText = `
                padding: 16px 20px;
                background: #03A9F4;
                color: #fff;
                font-size: 16px;
                font-weight: 600;
                display: flex;
                align-items: center;
                justify-content: space-between;
            `;
            
            const titleSpan = document.createElement('span');
            titleSpan.textContent = title || '数据校验失败';
            titleSpan.style.cssText = 'display: flex; align-items: center;';
            
            // 错误图标
            const errorIcon = document.createElement('span');
            errorIcon.innerHTML = '⚠️';
            errorIcon.style.cssText = 'margin-right: 8px; font-size: 18px;';
            titleSpan.insertBefore(errorIcon, titleSpan.firstChild);
            
            // 关闭按钮
            const closeBtn = document.createElement('span');
            closeBtn.innerHTML = '✕';
            closeBtn.style.cssText = `
                cursor: pointer;
                font-size: 20px;
                line-height: 1;
                padding: 4px 8px;
                border-radius: 4px;
                transition: background 0.2s;
            `;
            closeBtn.onmouseover = function() { this.style.background = 'rgba(255, 255, 255, 0.2)'; };
            closeBtn.onmouseout = function() { this.style.background = 'transparent'; };
            closeBtn.onclick = () => this.hideErrorDialog();
            
            header.appendChild(titleSpan);
            header.appendChild(closeBtn);

            // 内容区域
            const content = document.createElement('div');
            content.style.cssText = `
                padding: 20px;
                overflow-y: auto;
                flex: 1;
            `;
            
            const message = document.createElement('div');
            message.style.cssText = `
                color: #333;
                font-size: 14px;
                line-height: 1.6;
                margin-bottom: 16px;
            `;
            message.textContent = '请检查以下问题并修正后重试：';
            
            // 错误列表
            const errorList = document.createElement('ul');
            errorList.style.cssText = `
                list-style: none;
                padding: 0;
                margin: 0;
            `;
            
            errors.forEach((error, index) => {
                const li = document.createElement('li');
                li.style.cssText = `
                    padding: 12px 16px;
                    margin-bottom: 8px;
                    background: #fff2f0;
                    border-left: 4px solid #ff4d4f;
                    border-radius: 4px;
                    color: #333;
                    font-size: 14px;
                    line-height: 1.5;
                `;
                li.textContent = `${index + 1}. ${error}`;
                errorList.appendChild(li);
            });
            
            content.appendChild(message);
            content.appendChild(errorList);
            
            // 底部按钮
            const footer = document.createElement('div');
            footer.style.cssText = `
                padding: 16px 20px;
                border-top: 1px solid #f0f0f0;
                display: flex;
                justify-content: flex-end;
            `;
            
            const confirmBtn = document.createElement('button');
            confirmBtn.textContent = '我知道了';
            confirmBtn.style.cssText = `
                padding: 8px 24px;
                background: #03A9F4;
                color: #fff;
                border: none;
                border-radius: 4px;
                font-size: 14px;
                cursor: pointer;
                transition: background 0.2s;
            `;
            confirmBtn.onmouseover = function() { this.style.background = '#ff7875'; };
            confirmBtn.onmouseout = function() { this.style.background = '#03A9F4'; };
            confirmBtn.onclick = () => this.hideErrorDialog();
            
            footer.appendChild(confirmBtn);
            
            // 组装
            errorDialog.appendChild(header);
            errorDialog.appendChild(content);
            errorDialog.appendChild(footer);
            overlay.appendChild(errorDialog);
            
            // 点击遮罩层也可以关闭
            overlay.onclick = (e) => {
                if (e.target === overlay) {
                    this.hideErrorDialog();
                }
            };
            
            document.body.appendChild(overlay);
            this.errorDialog = overlay;
            
            console.log('显示错误提示对话框');
        } catch (error) {
            console.error('显示错误提示失败:', error);
            // 如果创建失败，回退到 alert
            alert(title + '\n\n' + errors.join('\n'));
        }
    };

    // 隐藏错误提示
    App.prototype.hideErrorDialog = function () {
        try {
            if (this.errorDialog) {
                if (this.errorDialog.parentNode) {
                    this.errorDialog.parentNode.removeChild(this.errorDialog);
                }
                this.errorDialog = null;
            }
            
            // 强制查找并移除所有错误提示
            const errorDialogs = document.querySelectorAll('.date-error-overlay');
            errorDialogs.forEach(element => {
                try {
                    if (element && element.parentNode) {
                        element.parentNode.removeChild(element);
                    }
                } catch (e) {
                    // 忽略
                }
            });
        } catch (error) {
            console.error('隐藏错误提示失败:', error);
        }
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

    // 获取明细表所有行数据（使用 getSubmitData，更高效）
    App.prototype.getDetailTableData = function () {
        try {
            const submitData = csdk.core.getSubmitData();
            console.log(submitData);
            if (!submitData) {
                console.warn('无法获取提交数据');
                return [];
            }
            
            // 从 submitData 中获取明细表数据
            // formson_0044 是明细表名称
            const detailTableName = 'formson_0044';
            const records = submitData[detailTableName];
            
            if (!records || !Array.isArray(records) || records.length === 0) {
                return [];
            }
            
            console.log(`📋 从 getSubmitData 获取到明细表数据: ${records.length} 条记录`);
            
            return records;
        } catch (error) {
            console.error('获取明细表数据异常:', error);
            return [];
        }
    };

    // 构建模拟付息接口请求参数
    App.prototype.buildSimulatedInterestRequest = function () {
        try {
            // 动态获取项目编号（表单编号），每次调用时都重新获取，支持用户新建后选择项目或更换项目
            const loanQueryParam = this.buildLoanQueryParam();
            const projectSerialNumber = loanQueryParam.serialNumber;
            if (!projectSerialNumber || projectSerialNumber.trim() === '') {
                console.warn('项目编号为空，无法构建请求参数。请先选择项目（表单编号字段 field0022）');
                return null;
            }
            
            console.log('当前项目编号:', projectSerialNumber);
            
            // 获取明细表数据
            const records = this.getDetailTableData();
            if (!records || records.length === 0) {
                console.warn('明细表数据为空，无法构建请求参数');
                return null;
            }
            
            console.log('明细表原始数据:', records);
            
            // 初始化目标结构
            const result = {
                api: 'calculateSimulatedInterest',
                projectSerialNumber: projectSerialNumber,
                drawdownConfig: [],
                repayment: [],
                timeTableList: []
            };
            
            // 遍历每条记录（使用 getSubmitData 的数据结构更简洁）
            records.forEach((record, index) => {
                const rowId = record.id || `row_${index}`; // 获取行ID（getSubmitData 中使用 id）
                
                // 直接获取字段值（getSubmitData 中字段值已经是字符串，不需要解析 lists）
                const dateValue = record.field0016 || '';
                const drawdownAmountValue = record.field0017 || '';
                const repaymentAmountValue = record.field0019 || '';
                
                // 格式化日期
                const drawdownDate = this.formatDate(dateValue);
                
                // 转换为数字
                const drawdownAmount = drawdownAmountValue ? Number(drawdownAmountValue) || 0 : 0;
                const repaymentAmount = repaymentAmountValue ? Number(repaymentAmountValue) || 0 : 0;
                
                // 如果有日期，添加到时间表
                if (drawdownDate) {
                    result.timeTableList.push({
                        rowId: rowId,
                        time: drawdownDate
                    });
                }
                
                // 如果有下柜金额，添加到下柜配置
                if (drawdownAmount > 0 && drawdownDate) {
                    result.drawdownConfig.push({
                        rowId: rowId,
                        drawdownDate: drawdownDate,
                        drawdownAmount: drawdownAmount
                    });
                }
                
                // 如果有还本金额，添加到还本配置
                if (repaymentAmount > 0 && drawdownDate) {
                    result.repayment.push({
                        rowId: rowId,
                        repaymentDate: drawdownDate,
                        repaymentAmount: repaymentAmount
                    });
                }
            });
            
            // 按时间排序
            result.timeTableList.sort((a, b) => {
                return new Date(a.time) - new Date(b.time);
            });
            
            console.log('构建的模拟付息请求参数:', JSON.stringify(result, null, 2));
            
            return result;
        } catch (error) {
            console.error('构建模拟付息请求参数失败:', error);
            return null;
        }
    };

    // 格式化日期为 yyyy-MM-dd 格式
    App.prototype.formatDate = function (dateValue) {
        if (!dateValue) {
            return '';
        }
        
        try {
            // 如果是字符串，尝试解析
            let date;
            if (typeof dateValue === 'string') {
                // 处理各种可能的日期格式
                date = new Date(dateValue.replace(/-/g, '/'));
            } else if (dateValue instanceof Date) {
                date = dateValue;
            } else {
                return '';
            }
            
            if (isNaN(date.getTime())) {
                return '';
            }
            
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            
            return `${year}-${month}-${day}`;
        } catch (error) {
            console.error('格式化日期失败:', error, dateValue);
            return '';
        }
    };
    
    // 获取主表字段值
    App.prototype.getMainTableFieldValue = function (fieldId) {
        try {
            const opts = { fieldId: fieldId };
            const data = csdk.core.getFieldData(opts);
            
            if (data && data.value !== undefined && data.value !== null) {
                return String(data.value).trim();
            }
            
            return '';
        } catch (error) {
            console.error(`获取主表字段 ${fieldId} 失败:`, error);
            return '';
        }
    };

    // 初始化计算按钮
    App.prototype.initCalculateButton = function () {
        try {
            // 查找按钮容器
            const buttonContainer = document.querySelector('.toolbarButton-content');
            if (!buttonContainer) {
                // 增加重试计数
                this._buttonInitRetryCount = (this._buttonInitRetryCount || 0) + 1;
                
                // 如果重试次数超过5次，停止重试（用户可能只是查看，不需要按钮）
                if (this._buttonInitRetryCount > 5) {
                    console.log('未找到按钮容器 .toolbarButton-content，已重试5次，停止查找（用户可能只是查看模拟付息表）');
                    return;
                }
                
                console.warn(`未找到按钮容器 .toolbarButton-content，正在重试 (${this._buttonInitRetryCount}/5)`);
                // 延迟重试
                setTimeout(() => {
                    this.initCalculateButton();
                }, 1000);
                return;
            }
            
            // 找到按钮容器后，重置重试计数
            this._buttonInitRetryCount = 0;
            
            // 检查是否已经添加过按钮
            if (buttonContainer.querySelector('.calculate-interest-btn')) {
                console.log('计算利息按钮已存在');
                return;
            }
            
            // 创建按钮容器div（与现有按钮结构一致）
            const buttonWrapper = document.createElement('div');
            buttonWrapper.setAttribute('data-v-daff269a', '');
            buttonWrapper.className = 'formson-list__button cap-btn calculate-interest-btn';
            
            // 创建按钮内部div
            const buttonInner = document.createElement('div');
            buttonInner.title = '计算利息';
            buttonInner.className = 'button-basics cap-btn-inner';
            buttonInner.style.cssText = 'border-color: rgb(0, 119, 255); background-color: rgb(0, 119, 255); border-radius: 2px; border-width: 1px;';
            
            // 创建图标span
            const iconSpan = document.createElement('span');
            const icon = document.createElement('i');
            icon.className = 'CAP cap-icon-jisuanqi';
            icon.style.cssText = 'color: rgb(255, 255, 255);';
            iconSpan.appendChild(icon);
            
            // 创建文本span
            const textSpan = document.createElement('span');
            textSpan.textContent = '计算利息 ';
            textSpan.style.cssText = 'color: rgb(255, 255, 255);';
            
            // 组装按钮
            buttonInner.appendChild(iconSpan);
            buttonInner.appendChild(textSpan);
            buttonWrapper.appendChild(buttonInner);
            
            // 添加点击事件
            buttonInner.addEventListener('click', () => {
                console.log('计算利息按钮被点击了');
                // 调用计算模拟付息的方法
                this.calculateAndUpdateSimulatedInterest().catch(err => {
                    console.error('计算模拟付息失败:', err);
                    this.hideLoading();
                    this._isCalculating = false;
                });
            });
            
            // 添加到按钮容器
            buttonContainer.appendChild(buttonWrapper);
            
            console.log('已添加计算利息按钮到按钮组');
        } catch (error) {
            console.error('初始化计算按钮失败:', error);
        }
    };

    // 更新主表贷款余额合计（field0012）
    App.prototype.updateMainTableLoanBalance = function () {
        try {
            console.log('开始更新主表贷款余额合计...');
            
            // 获取明细表数据
            const records = this.getDetailTableData();
            if (!records || records.length === 0) {
                console.warn('明细表数据为空，无法更新主表贷款余额合计');
                return;
            }
            
            // 找到所有有日期的记录，按日期排序，选择最后一个（最新的）日期
            const recordsWithDate = records
                .map(record => {
                    const recordDate = this.formatDate(record.field0016);
                    return {
                        record: record,
                        date: recordDate,
                        dateObj: recordDate ? new Date(recordDate.replace(/-/g, '/')) : null
                    };
                })
                .filter(item => item.date && item.dateObj && !isNaN(item.dateObj.getTime()))
                .sort((a, b) => {
                    // 按日期从早到晚排序
                    return a.dateObj.getTime() - b.dateObj.getTime();
                });
            
            if (recordsWithDate.length === 0) {
                console.warn('未找到有效日期的明细记录，无法更新主表贷款余额合计');
                return;
            }
            
            // 选择最后一个（最新的）日期的记录
            const lastRecord = recordsWithDate[recordsWithDate.length - 1];
            const targetRecord = lastRecord.record;
            const targetDate = lastRecord.date;
            
            // 获取贷款余额（field0018）
            let loanBalance = 0;
            if (targetRecord.field0018) {
                // 可能是对象格式 {value, display} 或直接值
                if (typeof targetRecord.field0018 === 'object' && targetRecord.field0018 !== null) {
                    loanBalance = targetRecord.field0018.value || targetRecord.field0018 || 0;
                } else {
                    loanBalance = targetRecord.field0018 || 0;
                }
            }
            
            // 转换为数字
            loanBalance = parseFloat(loanBalance) || 0;
            
            console.log(`找到目标记录，日期: ${targetDate}, 贷款余额: ${loanBalance}`);
            
            // 格式化数值
            const formatNumber = (value) => {
                if (value === null || value === undefined || isNaN(value)) {
                    return '0.00';
                }
                return parseFloat(value).toFixed(2);
            };
            
            const formatDisplay = (value) => {
                if (value === null || value === undefined || isNaN(value)) {
                    return '0.00';
                }
                const num = parseFloat(value);
                return num.toLocaleString('zh-CN', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                });
            };
            
            // 设置主表字段值
            const data = {
                fieldId: 'field0012',
                fieldData: {
                    value: formatNumber(loanBalance),
                    display: formatDisplay(loanBalance)
                }
            };
            
            csdk.core.setFieldData(data, (err) => {
                if (err) {
                    console.error('更新主表贷款余额合计失败:', err);
                } else {
                    console.log(`✅ 主表贷款余额合计已更新为: ${loanBalance} (来源日期: ${targetDate})`);
                }
            });
        } catch (error) {
            console.error('更新主表贷款余额合计异常:', error);
        }
    };

    // 自动点击保存按钮
    App.prototype.autoClickSaveButton = function () {
        try {
            console.log('开始查找保存按钮...');
            
            // 延迟一点时间，确保数据已完全更新
            setTimeout(() => {
                try {
                    // 查找保存按钮（通过图标类名和文本内容）
                    // 按钮结构：<div class="cap4-headers__btn"><i class="cap-icon-baocun"></i><span>保存</span></div>
                    const saveButton = Array.from(document.querySelectorAll('.cap4-headers__btn')).find(btn => {
                        const icon = btn.querySelector('.cap-icon-baocun');
                        const text = btn.querySelector('span');
                        return icon && text && text.textContent.trim() === '保存';
                    }) ||
                    Array.from(document.querySelectorAll('.cap4-headers__btn')).find(btn => {
                        return btn.textContent.includes('保存');
                    });
                    
                    if (saveButton) {
                        console.log('找到保存按钮，准备点击');
                        try {
                            saveButton.click();
                            console.log('✅ 已自动点击保存按钮');
                        } catch (clickError) {
                            console.error('点击保存按钮失败:', clickError);
                            // 如果 click() 方法失败，尝试触发 click 事件
                            try {
                                const clickEvent = new MouseEvent('click', {
                                    bubbles: true,
                                    cancelable: true,
                                    view: window
                                });
                                saveButton.dispatchEvent(clickEvent);
                                console.log('✅ 已通过事件触发保存按钮');
                            } catch (eventError) {
                                console.error('触发保存按钮事件失败:', eventError);
                            }
                        }
                    } else {
                        console.log('未找到保存按钮，跳过自动保存');
                    }
                } catch (error) {
                    console.error('自动点击保存按钮失败:', error);
                }
            }, 1000); // 延迟1秒，确保数据更新完成
        } catch (error) {
            console.error('自动点击保存按钮异常:', error);
        }
    };

    // 获取贷款开始日期和结束日期
    App.prototype.getLoanDateRange = function () {
        try {
            // 根据模拟付息表单数据字典：field0026 贷款开始日期，field0027 贷款结束日期
            const startDateFieldId = 'field0026'; // 贷款开始日期
            const endDateFieldId = 'field0027';   // 贷款结束日期
            
            // 获取开始日期
            const startDateData = getMainTableData(startDateFieldId);
            let startDate = null;
            if (startDateData) {
                const value = startDateData.value || startDateData.showValue;
                if (value) {
                    startDate = this.formatDate(value);
                    if (startDate) {
                        console.log(`获取贷款开始日期: ${startDate}`);
                    }
                }
            }
            
            // 获取结束日期
            const endDateData = getMainTableData(endDateFieldId);
            let endDate = null;
            if (endDateData) {
                const value = endDateData.value || endDateData.showValue;
                if (value) {
                    endDate = this.formatDate(value);
                    if (endDate) {
                        console.log(`获取贷款结束日期: ${endDate}`);
                    }
                }
            }
            
            if (!startDate || !endDate) {
                console.warn(`无法获取贷款日期范围 - 开始日期: ${startDate || '未找到'}, 结束日期: ${endDate || '未找到'}`);
            }
            
            return {
                startDate: startDate,
                endDate: endDate
            };
        } catch (error) {
            console.error('获取贷款日期范围失败:', error);
            return {
                startDate: null,
                endDate: null
            };
        }
    };

    // 校验和排序明细表数据
    App.prototype.validateAndSortDetailTable = async function () {
        try {
            const tableName = 'formson_0044';
            const timeFieldId = 'field0016'; // 时间字段
            
            console.log('开始校验和排序明细表数据...');
            
            // 获取贷款日期范围
            const dateRange = this.getLoanDateRange();
            if (!dateRange.startDate || !dateRange.endDate) {
                console.warn('无法获取贷款开始日期或结束日期，跳过校验');
                return { success: false, message: '无法获取贷款日期范围' };
            }
            
            console.log(`贷款日期范围: ${dateRange.startDate} 至 ${dateRange.endDate}`);
            
            // 获取明细表所有行数据
            const records = this.getDetailTableData();
            if (!records || records.length === 0) {
                console.warn('明细表数据为空，跳过校验和排序');
                return { success: true, message: '明细表数据为空' };
            }
            
            console.log(`获取到 ${records.length} 条明细记录`);
            
            // 校验和收集有效记录
            const validRecords = [];
            const errors = [];
            const dateSet = new Set(); // 用于检测重复日期
            
            records.forEach((record, index) => {
                const rowId = record.id;
                const dateValue = record.field0016 || '';
                const formattedDate = this.formatDate(dateValue);
                
                if (!formattedDate) {
                    errors.push(`第 ${index + 1} 行：时间字段为空或格式不正确`);
                    return;
                }
                
                // 校验1: 时间 ≥ 贷款开始日期
                if (formattedDate < dateRange.startDate) {
                    errors.push(`第 ${index + 1} 行：时间 ${formattedDate} 早于贷款开始日期 ${dateRange.startDate}`);
                    return;
                }
                
                // 校验2: 时间 ≤ 贷款结束日期
                if (formattedDate > dateRange.endDate) {
                    errors.push(`第 ${index + 1} 行：时间 ${formattedDate} 晚于贷款结束日期 ${dateRange.endDate}`);
                    return;
                }
                
                // 校验3: 时间不能重复
                if (dateSet.has(formattedDate)) {
                    errors.push(`第 ${index + 1} 行：时间 ${formattedDate} 与其他行重复`);
                    return;
                }
                
                dateSet.add(formattedDate);
                validRecords.push({
                    ...record,
                    formattedDate: formattedDate
                });
            });
            
            // 如果有错误，提示用户
            if (errors.length > 0) {
                const errorMsg = errors.join('\n');
                console.error('明细表数据校验失败:\n' + errorMsg);
                // 使用友好的错误提示对话框替代 alert
                this.showErrorDialog('明细表数据校验失败', errors);
                return { success: false, message: errorMsg, errors: errors };
            }
            
            return { 
                success: true, 
                message: `校验通过，共 ${validRecords.length} 条有效记录`,
                records: validRecords 
            };
        } catch (error) {
            console.error('校验和排序明细表失败:', error);
            return { success: false, message: error.message };
        }
    };

    // 计算并更新模拟付息（手动触发）
    App.prototype.calculateAndUpdateSimulatedInterest = async function () {
        // 防止重复计算
        if (this._isCalculating) {
            console.log('正在计算中，跳过重复计算');
            return;
        }
        
        this._isCalculating = true;
        
        // 显示加载圈圈
        this.showLoading('正在校验和排序明细表...');
        
        try {
            console.log('========== 开始计算模拟付息 ==========');
            
            // 先校验和排序明细表数据
            const validateResult = await this.validateAndSortDetailTable();
            if (!validateResult.success) {
                console.error('明细表数据校验失败:', validateResult.message);
                this._isCalculating = false;
                this.hideLoading();
                return;
            }
            
            // 更新加载提示
            this.showLoading('正在计算模拟付息...');
            
            // 构建请求参数
            const requestBody = this.buildSimulatedInterestRequest();
            if (!requestBody) {
                console.warn('无法构建请求参数，跳过计算');
                this._isCalculating = false;
                this.hideLoading();
                return;
            }
            
            // 如果没有时间表，不进行计算
            if (!requestBody.timeTableList || requestBody.timeTableList.length === 0) {
                console.warn('时间表为空，跳过计算');
                this._isCalculating = false;
                this.hideLoading();
                return;
            }
            
            // 调用接口
            const response = await fetch('/seeyon/dailyPayment.do?method=forward', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json;charset=UTF-8'
                },
                body: JSON.stringify(requestBody)
            });
            
            // 检查响应状态
            if (!response.ok) {
                throw new Error(`HTTP请求失败，状态码: ${response.status}`);
            }
            
            // 解析响应数据
            const result = await response.json();
            
            if (result.success && result.data && result.data.length > 0) {
                console.log('✅ 模拟付息计算成功，开始更新明细表');
                console.log('计算结果:', result.data);
                
                // 将计算结果写回明细表（这里会等待所有数据插入完成）
                await this.updateDetailTableWithResults(result.data);
                
                console.log('✅ 明细表更新完成');
                
                // 更新主表贷款余额合计（field0012）
                // 延迟一点时间，确保明细表数据已完全更新
                setTimeout(() => {
                    this.updateMainTableLoanBalance();
                }, 500);
                
                // 自动保存表单（调用保存接口）
                this.autoClickSaveButton();
                
                // ========== 新方案已注释 ==========
                // // 新方案：直接修改 submitData 并提交，而不是逐个更新明细行
                // await this.updateSubmitDataAndSave(result.data);
                // =================================
            } else {
                console.warn('计算失败或返回数据为空:', result.msg || result.message);
            }
            
        } catch (error) {
            console.error('计算模拟付息异常:', error.message);
        } finally {
            console.log('========== 计算模拟付息完成 ==========');
            // 隐藏加载圈圈（在所有数据插入完成后）
            this.hideLoading();
            this._isCalculating = false;
        }
    };

    // 新方案：直接修改 submitData 并提交
    App.prototype.updateSubmitDataAndSave = async function (results) {
        try {
            console.log('开始更新 submitData 并保存...');
            
            // 1. 获取初始的 submitData
            const submitData = csdk.core.getSubmitData();
            if (!submitData) {
                console.error('无法获取 submitData，跳过保存');
                return;
            }
            
            console.log('获取到初始 submitData:', submitData);
            
            // 2. 明细表名称
            const detailTableName = 'formson_0044';
            const detailRecords = submitData[detailTableName];
            
            if (!detailRecords || !Array.isArray(detailRecords)) {
                console.error('明细表数据不存在或格式错误');
                return;
            }
            
            // 3. 格式化数值的辅助函数
            const formatNumber = (value) => {
                if (value === null || value === undefined || isNaN(value)) {
                    return '0.00';
                }
                return parseFloat(value).toFixed(2);
            };
            
            const formatDisplay = (value) => {
                if (value === null || value === undefined || isNaN(value)) {
                    return '0.00';
                }
                const num = parseFloat(value);
                return num.toLocaleString('zh-CN', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                });
            };
            
            // 4. 根据后端返回结果，更新 submitData 中对应的明细行
            let updateCount = 0;
            results.forEach((item) => {
                // 根据 rowId 找到对应的明细行
                const record = detailRecords.find(r => r.id === item.rowId);
                if (record) {
                    // 更新 field0018: 贷款余额
                    // 兼容两种格式：对象格式 {value, display} 或直接值
                    const loanBalanceValue = formatNumber(item.loanBalance);
                    const loanBalanceDisplay = formatDisplay(item.loanBalance);
                    if (typeof record.field0018 === 'object' && record.field0018 !== null) {
                        record.field0018.value = loanBalanceValue;
                        record.field0018.display = loanBalanceDisplay;
                    } else {
                        record.field0018 = loanBalanceValue;
                    }
                    
                    // 更新 field0020: 模拟付息
                    const simulatedInterestValue = formatNumber(item.simulatedInterest);
                    const simulatedInterestDisplay = formatDisplay(item.simulatedInterest);
                    if (typeof record.field0020 === 'object' && record.field0020 !== null) {
                        record.field0020.value = simulatedInterestValue;
                        record.field0020.display = simulatedInterestDisplay;
                    } else {
                        record.field0020 = simulatedInterestValue;
                    }
                    
                    updateCount++;
                    console.log(`✅ 更新明细行 ${item.rowId}: 贷款余额=${item.loanBalance}, 模拟付息=${item.simulatedInterest}`);
                } else {
                    console.warn(`⚠️ 未找到 rowId=${item.rowId} 对应的明细行`);
                }
            });
            
            console.log(`共更新 ${updateCount}/${results.length} 条明细记录`);
            
            // 5. 直接提交修改后的 submitData
            console.log('准备提交修改后的 submitData...');
            const response = await fetch('/seeyon/rest/cap4/form/saveOrUpdate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json;charset=UTF-8'
                },
                body: JSON.stringify(submitData)
            });
            
            // 检查响应状态
            if (!response.ok) {
                throw new Error(`保存请求失败，状态码: ${response.status}`);
            }
            
            // 解析响应数据
            const saveResult = await response.json();
            
            // 根据实际返回格式判断成功
            const isSuccess = saveResult && (
                saveResult.code === 0 || 
                (saveResult.data && saveResult.data.success === 1) || 
                (saveResult.data && saveResult.data.code === "200")
            );
            
            if (isSuccess) {
                console.log('✅ 表单保存成功:', saveResult);
            } else {
                const errorMessage = (saveResult && saveResult.data && saveResult.data.message) || 
                                  (saveResult && saveResult.message) || 
                                  '保存失败，返回结果异常';
                console.warn('保存返回结果异常:', saveResult);
                alert(errorMessage);
            }
        } catch (error) {
            console.error('更新 submitData 并保存失败:', error);
            alert('保存失败：' + (error.message || '未知错误'));
        }
    };

    // 将计算结果写回明细表（原逻辑，已注释）
    App.prototype.updateDetailTableWithResults = async function (results) {
        // 添加调用追踪，防止重复调用
        const callId = Date.now() + '-' + Math.random().toString(36).substr(2, 9);
        console.log(`[调用ID: ${callId}] 开始更新 ${results.length} 条明细记录`);
        
        try {
            const tableName = 'formson_0044'; // 明细表名称
            
            // 批量更新所有行的数据（并发执行）
            const updatePromises = results.map((item, index) => {
                return this.updateSingleDetailRow(tableName, item.rowId, item.loanBalance, item.simulatedInterest)
                    .catch(err => {
                        console.error(`[调用ID: ${callId}] 更新第 ${index + 1} 行失败 (rowId: ${item.rowId}):`, err);
                        return null; // 返回 null 表示失败，但不抛出错误
                    });
            });
            
            // 等待所有更新完成（使用 Promise.all 因为每个 Promise 都有 .catch() 处理，不会 reject）
            console.log(`[调用ID: ${callId}] 等待所有更新完成...`);
            const updateResults = await Promise.all(updatePromises);
            const successCount = updateResults.filter(r => r !== null).length;
            const failCount = updateResults.length - successCount;
            console.log(`[调用ID: ${callId}] 所有明细记录更新完成，成功: ${successCount}/${results.length}，失败: ${failCount}`);
        } catch (error) {
            console.error(`[调用ID: ${callId}] 更新明细表失败:`, error);
            throw error; // 重新抛出错误，让上层可以处理
        }
    };

    // 更新单行明细数据
    App.prototype.updateSingleDetailRow = function (tableName, recordId, loanBalance, simulatedInterest) {
        return new Promise((resolve, reject) => {
            let isResolved = false;
            
            // 添加超时机制，防止回调永远不执行
            // 并发更新时，OA系统可能需要更长时间处理，所以增加超时时间
            const timeoutId = setTimeout(() => {
                if (!isResolved) {
                    isResolved = true;
                    resolve(); // 超时也 resolve，避免阻塞
                }
            }, 5000); // 5秒超时（并发时可能需要更长时间）
            
            try {
                // 格式化数值
                const formatNumber = (value) => {
                    if (value === null || value === undefined || isNaN(value)) {
                        return '0.00';
                    }
                    return parseFloat(value).toFixed(2);
                };
                
                const formatDisplay = (value) => {
                    if (value === null || value === undefined || isNaN(value)) {
                        return '0.00';
                    }
                    const num = parseFloat(value);
                    return num.toLocaleString('zh-CN', {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2
                    });
                };
                
                // 构建更新数据
                // field0018: 贷款余额
                // field0020: 模拟付息
                const data = [
                    {
                        fieldId: 'field0018', // 贷款余额
                        tableName: tableName,
                        recordId: recordId,
                        fieldData: {
                            value: formatNumber(loanBalance),
                            display: formatDisplay(loanBalance)
                        }
                    },
                    {
                        fieldId: 'field0020', // 模拟付息
                        tableName: tableName,
                        recordId: recordId,
                        fieldData: {
                            value: formatNumber(simulatedInterest),
                            display: formatDisplay(simulatedInterest)
                        }
                    }
                ];
                
                // 设置字段数据
                csdk.core.setFieldData(data, (err) => {
                    if (isResolved) {
                        // 如果已经超时 resolve，回调才被触发，说明数据可能已经被设置了
                        console.warn(`⚠️ 更新记录 ${recordId} 的回调在超时后才触发（数据可能已设置）`);
                        return; // 不再处理，避免重复操作
                    }
                    
                    clearTimeout(timeoutId);
                    isResolved = true;
                    
                    if (err) {
                        console.error(`更新记录 ${recordId} 失败:`, err);
                        reject(err);
                        return;
                    }
                    
                    console.log(`✅ 更新记录 ${recordId} 成功（回调正常触发）`);
                    resolve();
                });
            } catch (error) {
                if (!isResolved) {
                    clearTimeout(timeoutId);
                    isResolved = true;
                    console.error(`更新记录 ${recordId} 异常:`, error);
                    reject(error);
                }
            }
        });
    };

    // 测试：调用模拟付息接口
    App.prototype.testCalculateSimulatedInterest = async function () {
        try {
            console.log('========== 开始测试调用模拟付息接口 ==========');
            
            // 构建请求参数（同步方法，不需要await）
            const requestBody = this.buildSimulatedInterestRequest();
            if (!requestBody) {
                console.warn('无法构建请求参数，跳过接口调用');
                return;
            }
            
            // 调用接口
            console.log('发送请求到接口...');
            const response = await fetch('/seeyon/dailyPayment.do?method=forward', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json;charset=UTF-8'
                },
                body: JSON.stringify(requestBody)
            });
            
            // 检查响应状态
            if (!response.ok) {
                throw new Error(`HTTP请求失败，状态码: ${response.status}`);
            }
            
            // 解析响应数据
            const result = await response.json();
            
            console.log('========== 模拟付息接口返回结果 ==========');
            console.log('完整响应:', JSON.stringify(result, null, 2));
            
            if (result.success) {
                console.log('✅ 接口调用成功');
                console.log('返回数据条数:', result.data ? result.data.length : 0);
                
                if (result.data && result.data.length > 0) {
                    console.log('计算结果详情:');
                    result.data.forEach((item, index) => {
                        console.log(`  [${index + 1}] rowId: ${item.rowId}, 贷款余额: ${item.loanBalance}, 付息: ${item.simulatedInterest}`);
                    });
                }
            } else {
                console.error('❌ 接口调用失败:', result.msg || result.message);
            }
            
            console.log('========== 测试完成 ==========');
            
            return result;
        } catch (error) {
            console.error('========== 调用模拟付息接口异常 ==========');
            console.error('错误信息:', error.message);
            console.error('错误堆栈:', error.stack);
            console.error('========== 异常结束 ==========');
            throw error;
        }
    };

    return App;
});