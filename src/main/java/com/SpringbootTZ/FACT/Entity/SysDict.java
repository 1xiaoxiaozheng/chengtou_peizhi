package com.SpringbootTZ.FACT.Entity;

import lombok.Data;

import java.util.Date;

/**
 * 数据字典表映射（sys_dict）
 */
@Data
public class SysDict {
    private Long id;
    private String dictType;
    private String dictKey;
    private String dictValue;
    private Integer sort;
    private Integer status;
    private String remark;
    private Date createTime;
    private Date updateTime;
}

