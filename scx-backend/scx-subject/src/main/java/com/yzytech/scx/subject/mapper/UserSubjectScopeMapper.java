package com.yzytech.scx.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserSubjectScopeMapper extends BaseMapper<UserSubjectScope> {

	@Select("""
			SELECT COUNT(1)
			FROM sys_user
			WHERE id = #{userId}
			  AND status = '0'
			  AND del_flag = '0'
			""")
	int countActiveUser(@Param("userId") String userId);

}
