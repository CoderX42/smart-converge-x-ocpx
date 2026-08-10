package com.yzytech.scx.adx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.adx.entity.AdxUserScopeRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdxUserScopeRevisionMapper extends BaseMapper<AdxUserScopeRevision> {

	@Select("SELECT version FROM scx_adx_user_scope_revision WHERE user_id=#{userId}")
	Long selectVersion(@Param("userId") String userId);

}
