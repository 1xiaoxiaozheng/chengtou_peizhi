package com.seeyon.apps.MonthRepayment;

import com.seeyon.cap4.form.bean.ParamDefinition;
import com.seeyon.cap4.form.bean.fieldCtrl.FormFieldCustomCtrl;
import com.seeyon.cap4.form.util.Enums;

public class MonthRepayment extends FormFieldCustomCtrl {

    @Override
    public String getPCInjectionInfo() {
        String param = "{path:'apps_res/cap/customCtrlResources/diyResources/',jsUri:'js/MonthRepayment.js',initMethod:'init',nameSpace:'field_" + this.getKey() + "'}";
        return param;
    }

    @Override
    public String getMBInjectionInfo() {
        return null;
    }

    @Override
    public String getKey() {
        return "-4066558273311850158";
    }

    @Override
    public String getText() {
        return "月利息计算控件";
    }

    @Override
    public void init() {
        this.setPluginId("MonthRepayment");
        this.setIcon("MonthRepayment");
        ParamDefinition paramDefinition = new ParamDefinition();
        paramDefinition.setParamType(Enums.ParamType.button);
        paramDefinition.setDisplay("月利息计算控件");
    }

    @Override
    public String[] getDefaultVal(String s) {
        return new String[0];
    }
}
