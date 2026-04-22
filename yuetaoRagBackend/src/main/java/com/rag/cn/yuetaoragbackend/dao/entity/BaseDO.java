package com.rag.cn.yuetaoragbackend.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.util.Date;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 14:40
 */
@Data
@Accessors(chain = true)
public class BaseDO {

    /** 主键ID，使用雪花算法生成。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 逻辑删除标记：0-未删除，1-已删除。 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleteFlag;
}
