package com.yzytech.scx.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("scx_tracking_link")
public class TrackingLink {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    @NotBlank private String mediaId;
    @NotBlank private String code;
    @NotBlank private String name;
    @NotBlank private String productId;
    @NotBlank private String partnerId;
    private String customerId;
    private String documentId;
    @NotBlank private String deviceType;
    private Boolean redirectEnabled;
    @NotBlank private String targetUrl;
    private String downloadUrl;
    private String templateText;
    private Integer configVersion;
    private String status;
    private LocalDateTime archivedAt;
    @Version private Integer version;
    @TableField(fill = FieldFill.INSERT) private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
