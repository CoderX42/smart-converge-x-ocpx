package com.yzytech.scx.subject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scx_media")
@Schema(description = "媒体主体")
public class Media extends Model<Media> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;
	private String code;
	private String name;
	private String status;
	private String timezone;
	private String settlementCurrency;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
}
