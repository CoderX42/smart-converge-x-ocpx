package com.yzytech.scx.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.reporting.entity.ExportTask;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {
    @Update("UPDATE scx_export_task SET status='RUNNING', started_at=#{now} WHERE id=#{id} AND status='PENDING'")
    int claim(@Param("id") String id, @Param("now") LocalDateTime now);

    @Update("UPDATE scx_export_task SET status='EXPIRED', artifact_content=NULL WHERE status='SUCCEEDED' AND expires_at < #{now}")
    int expireCompleted(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE scx_export_task
            SET status='PENDING', row_count=0, artifact_name=NULL, artifact_content=NULL, artifact_sha256=NULL,
                expires_at=NULL, error_code=NULL, error_message=NULL, started_at=NULL, finished_at=NULL,
                requested_at=#{now}
            WHERE id=#{id} AND status='FAILED'
            """)
    int resetForRetry(@Param("id") String id, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=#{userId} AND r.role_code='ROLE_ADMIN'")
    long hasAdminRole(@Param("userId") String userId);
}
