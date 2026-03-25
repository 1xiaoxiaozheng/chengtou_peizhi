package com.seeyon.apps.DailyPayment;

import com.seeyon.cap4.form.bean.ParamDefinition;
import com.seeyon.cap4.form.bean.fieldCtrl.FormFieldCustomCtrl;
import com.seeyon.cap4.form.util.Enums;

public class DailyPayment extends FormFieldCustomCtrl {

    @Override
    public String getPCInjectionInfo() {
        String param = "{path:'apps_res/cap/customCtrlResources/diyResources/',jsUri:'js/DailyPayment.js',initMethod:'init',nameSpace:'field_" + this.getKey() + "'}";
        return param;
    }

    @Override
    public String getMBInjectionInfo() {
        return null;
    }

    @Override
    public String getKey() {
        return "-8527130036398111250";
    }

    @Override
    public String getText() {
        return "日利息计算控件";
    }

    @Override
    public void init() {
        this.setPluginId("DailyPayment");
        this.setIcon("DailyPayment");
        ParamDefinition paramDefinition = new ParamDefinition();
        paramDefinition.setParamType(Enums.ParamType.button);
        paramDefinition.setDisplay("日利息计算控件");
    }

    @Override
    public String[] getDefaultVal(String s) {
        return new String[0];
    }
}
