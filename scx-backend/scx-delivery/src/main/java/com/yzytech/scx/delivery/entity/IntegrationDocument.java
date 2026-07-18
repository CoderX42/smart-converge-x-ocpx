package com.yzytech.scx.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("scx_integration_document")
public class IntegrationDocument {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    @NotBlank private String mediaId;
    @NotBlank private String name;
    private String type;
    private Integer documentVersion;
    private String storageType;
    private String objectKey;
    private String externalUrl;
    private String contentHash;
    private String status;
    @Version private Integer version;
    @TableField(fill = FieldFill.INSERT) private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
