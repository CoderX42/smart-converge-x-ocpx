package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.tracking.entity.ChannelClient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChannelClientMapper extends BaseMapper<ChannelClient> {

	@Select("SELECT * FROM scx_channel_client WHERE client_id=#{clientId} AND status='ACTIVE' LIMIT 1")
	ChannelClient selectActiveByClientId(@Param("clientId") String clientId);

	@Select("SELECT * FROM scx_channel_client WHERE id=#{id} FOR UPDATE")
	ChannelClient selectByIdForUpdate(@Param("id") String id);

	@Select("SELECT * FROM scx_channel_client WHERE media_id=#{mediaId} AND source_code=#{sourceCode} LIMIT 1")
	ChannelClient selectByMediaAndSource(@Param("mediaId") String mediaId, @Param("sourceCode") String sourceCode);

}
