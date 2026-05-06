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
@TableName("t_user")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class UserDO extends BaseDO {

    /**
     * 登录用户名，业务唯一。
     */
    private String username;

    /**
     * 用户展示名称。
     */
    private String displayName;

    /**
     * 角色编码。
     */
    private String roleCode;

    /**
     * 所属部门ID。
     */
    private Long departmentId;

    /**
     * 职级数值。
     */
    private Integer rankLevel;

    /**
     * 用户状态：ENABLED-启用，DISABLED-停用。
     */
    private String status;

    /**
     * 登录密码哈希。
     */
    private String passwordHash;
}
