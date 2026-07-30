package com.yzytech.scx.reporting.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReportLeaseMapper {

	@Insert("""
			INSERT IGNORE INTO scx_report_lease(lease_key,locked_by,locked_until,update_time)
			VALUES(#{key},#{owner},#{until},NOW(3))
			""")
	int insertIfAbsent(@Param("key") String key, @Param("owner") String owner, @Param("until") LocalDateTime until);

	@Update("""
			UPDATE scx_report_lease
			SET locked_by=#{owner},locked_until=#{until},update_time=NOW(3)
			WHERE lease_key=#{key} AND (locked_until<NOW(3) OR locked_by=#{owner})
			""")
	int renewOrAcquire(@Param("key") String key, @Param("owner") String owner, @Param("until") LocalDateTime until);

	@Select("""
			SELECT COUNT(*) FROM scx_report_lease
			WHERE lease_key=#{key} AND locked_by=#{owner} AND locked_until>NOW(3)
			""")
	int isOwned(@Param("key") String key, @Param("owner") String owner);

	@Delete("DELETE FROM scx_report_lease WHERE lease_key=#{key} AND locked_by=#{owner}")
	int release(@Param("key") String key, @Param("owner") String owner);

}
