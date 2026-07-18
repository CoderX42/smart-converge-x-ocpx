package com.yzytech.scx.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_rta_endpoint")
public class RtaEndpoint {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    @NotBlank
    private String mediaId;
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String rtaIdentifier;
    @NotBlank
    private String endpointUrl;
    private String description;
    private String status;
    private String lastHealthStatus;
    private LocalDateTime lastHealthCheckedAt;
    private String lastHealthMessage;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
