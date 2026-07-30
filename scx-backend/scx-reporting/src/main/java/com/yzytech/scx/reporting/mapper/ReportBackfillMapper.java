package com.yzytech.scx.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.reporting.entity.ReportBackfill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReportBackfillMapper extends BaseMapper<ReportBackfill> {

	@Select("""
			SELECT COUNT(*) FROM scx_raw_event e
			JOIN scx_tracking_task t ON t.id=e.task_id
			WHERE t.media_id=#{mediaId} AND e.data_origin='LIVE'
			  AND e.occurred_at>=#{startUtc} AND e.occurred_at<#{endUtc}
			""")
	long countRawEvents(@Param("mediaId") String mediaId, @Param("startUtc") java.time.LocalDateTime startUtc,
			@Param("endUtc") java.time.LocalDateTime endUtc);

	@Select("""
			SELECT COUNT(*) FROM scx_media_report_hourly_fact f
			WHERE f.media_id=#{mediaId} AND f.stat_hour_utc>=#{startUtc} AND f.stat_hour_utc<#{endUtc}
			  AND f.source_version=(
			    SELECT MAX(f2.source_version) FROM scx_media_report_hourly_fact f2
			    WHERE f2.media_account_id=f.media_account_id
			      AND f2.external_campaign_id=f.external_campaign_id
			      AND f2.stat_hour_utc=f.stat_hour_utc AND f2.sync_type=f.sync_type
			  )
			""")
	long countMediaFacts(@Param("mediaId") String mediaId, @Param("startUtc") java.time.LocalDateTime startUtc,
			@Param("endUtc") java.time.LocalDateTime endUtc);

	@Select("""
			SELECT COUNT(*) FROM scx_report_daily d
			JOIN scx_report_run r ON r.id=d.report_run_id
			WHERE d.media_id=#{mediaId} AND d.report_date BETWEEN #{startDate} AND #{endDate}
			  AND d.data_origin='LIVE' AND r.status='SUCCEEDED' AND r.data_origin='LIVE'
			  AND r.id=(
			    SELECT r2.id FROM scx_report_run r2
			    WHERE r2.media_id=#{mediaId} AND r2.report_date=d.report_date
			      AND r2.status='SUCCEEDED' AND r2.data_origin='LIVE'
			    ORDER BY r2.revision DESC,r2.finished_at DESC LIMIT 1
			  )
			""")
	long countCurrentRows(@Param("mediaId") String mediaId, @Param("startDate") java.time.LocalDate startDate,
			@Param("endDate") java.time.LocalDate endDate);

	@Update("""
			UPDATE scx_report_backfill
			SET status='RUNNING',started_at=UTC_TIMESTAMP(3)
			WHERE id=#{id} AND status='PENDING'
			""")
	int claim(@Param("id") String id);

}
