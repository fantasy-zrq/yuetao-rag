package com.rag.cn.yuetaoragbackend.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 14:40
 */
@Data
@TableName("t_department")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class DepartmentDO extends BaseDO {

    /** 部门编码，业务唯一。 */
    private String deptCode;

    /** 部门名称。 */
    private String deptName;

    /** 部门状态：ENABLED-启用，DISABLED-停用。 */
    private String status;
}
