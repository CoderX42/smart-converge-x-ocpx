package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.tracking.entity.ChannelCredential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChannelCredentialMapper extends BaseMapper<ChannelCredential> {

	@Select("""
			SELECT * FROM scx_channel_credential
			WHERE channel_client_id=#{channelClientId} AND key_version=#{keyVersion}
			  AND status='ACTIVE' AND valid_from <= NOW(3)
			  AND (valid_until IS NULL OR valid_until > NOW(3))
			LIMIT 1
			""")
	ChannelCredential selectActive(@Param("channelClientId") String channelClientId,
			@Param("keyVersion") String keyVersion);

}
