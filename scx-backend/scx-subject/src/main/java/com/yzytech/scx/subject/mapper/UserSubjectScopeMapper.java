package com.yzytech.scx.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.subject.dto.MediaScopeUserOption;
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

	@Select("""
			<script>
			SELECT id, username, nickname
			FROM sys_user
			WHERE status = '0'
			  AND del_flag = '0'
			  <if test="keyword != null and keyword != ''">
			    AND (username LIKE CONCAT('%', #{keyword}, '%')
			      OR nickname LIKE CONCAT('%', #{keyword}, '%'))
			  </if>
			ORDER BY username ASC, id ASC
			</script>
			""")
	Page<MediaScopeUserOption> selectCandidatePage(Page<MediaScopeUserOption> page, @Param("keyword") String keyword);

}
