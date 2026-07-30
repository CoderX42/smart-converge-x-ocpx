package com.yzytech.scx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.inventory.entity.MediaAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaAccountMapper extends BaseMapper<MediaAccount> {

	@Select("""
			SELECT
			  (SELECT COUNT(*) FROM scx_placement WHERE media_account_id = #{id})
			  + (SELECT COUNT(*) FROM scx_media_adapter_config WHERE media_account_id = #{id})
			  + (SELECT COUNT(*) FROM scx_media_publishing_binding WHERE media_account_id = #{id})
			""")
	long countReferences(@Param("id") String id);

}
