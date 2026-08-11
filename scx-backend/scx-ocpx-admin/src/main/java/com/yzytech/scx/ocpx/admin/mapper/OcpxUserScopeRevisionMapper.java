package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.ocpx.admin.entity.OcpxUserScopeRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OcpxUserScopeRevisionMapper extends BaseMapper<OcpxUserScopeRevision> {

	@Select("SELECT version FROM scx_ocpx_user_scope_revision WHERE user_id=#{userId}")
	Long selectVersion(@Param("userId") String userId);

}
