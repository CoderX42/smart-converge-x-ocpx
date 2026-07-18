package com.yzytech.scx.delivery.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;
@Data @TableName("scx_ad_platform_config")
public class AdPlatformConfig {
 @TableId(type=IdType.ASSIGN_ID) private String id; @NotBlank private String mediaId; @NotBlank private String configKey; @NotBlank private String configValue; private String description; private String status; @Version private Integer version; private LocalDateTime createTime; private LocalDateTime updateTime;
}
