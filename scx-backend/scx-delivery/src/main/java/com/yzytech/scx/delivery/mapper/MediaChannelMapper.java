package com.yzytech.scx.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.delivery.entity.MediaChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaChannelMapper extends BaseMapper<MediaChannel> {

	@Select("SELECT * FROM scx_media_channel WHERE id = #{id} FOR UPDATE")
	MediaChannel selectForUpdate(@Param("id") String id);

	@Select("""
			SELECT
			  (SELECT COUNT(*) FROM scx_media_channel_user WHERE channel_id = #{id})
			  + (SELECT COUNT(*) FROM scx_tracking_task WHERE channel_id = #{id})
			  + (SELECT COUNT(*) FROM scx_report_daily WHERE channel_id = #{id})
			  + (SELECT COUNT(*) FROM scx_media_account WHERE channel_id = #{id})
			""")
	long countReferences(@Param("id") String id);

}
