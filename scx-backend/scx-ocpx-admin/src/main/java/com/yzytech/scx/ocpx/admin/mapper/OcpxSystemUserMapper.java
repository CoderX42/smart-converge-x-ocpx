package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.system.api.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OcpxSystemUserMapper extends BaseMapper<SysUser> {

	@Select("SELECT id FROM sys_user WHERE id=#{userId} AND del_flag='0' FOR UPDATE")
	String lockActiveUser(@Param("userId") String userId);

	@Select("SELECT id FROM sys_role WHERE role_code='ROLE_ADMIN' AND del_flag='0' ORDER BY id LIMIT 1 FOR UPDATE")
	String lockScopeAdministration();

}
