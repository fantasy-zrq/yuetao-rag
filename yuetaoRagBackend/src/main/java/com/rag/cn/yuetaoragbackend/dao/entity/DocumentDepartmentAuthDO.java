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
@TableName("t_document_department_auth")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class DocumentDepartmentAuthDO extends BaseDO {

    /** 文档ID。 */
    private Long documentId;

    /** 部门ID。 */
    private Long departmentId;
}
